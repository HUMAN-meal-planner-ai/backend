package com.human.backend.cost.repository;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSV 파일 기반의 CostRepository 구현체
 * mock_csv_bundle 디렉터리의 CSV 파일들을 읽어 인메모리에 적재하고 원가 조회 기능을 제공합니다.
 */
@Slf4j
@Repository
public class MemoryCostRepository implements CostRepository {

    @Value("${mock.csv.path:src/main/java/com/human/backend/cost/dummy}")
    private String mockCsvPath = "src/main/java/com/human/backend/cost/dummy";

    private final Map<Long, String> menuMap = new ConcurrentHashMap<>();
    private final Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap = new ConcurrentHashMap<>();

    // 테스트나 프로그래밍적 초기화를 위한 생성자
    public MemoryCostRepository() {
    }

    // 경로 직접 지정 생성자 (단위 테스트 등에서 활용)
    public MemoryCostRepository(String mockCsvPath) {
        this.mockCsvPath = mockCsvPath;
    }

    /**
     * 스프링 빈 초기화 시 CSV 파일들을 순차적으로 로드하여 결합합니다.
     */
    @PostConstruct
    public void init() {
        loadDataFromCsv();
    }

    /**
     * CSV 데이터를 읽어와 메모리 맵을 초기화하는 핵심 메서드
     */
    public synchronized void loadDataFromCsv() {
        log.info(">> [MemoryCostRepository] CSV mock data 로딩 시작. 경로: {}", mockCsvPath);
        File baseDir = new File(mockCsvPath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn(">> [MemoryCostRepository] 지정된 CSV 디렉터리를 찾을 수 없습니다: {}", mockCsvPath);
            return;
        }

        try {
            // 1. menu.csv 읽기 -> menuId -> menuName
            Map<Long, String> loadedMenuMap = loadMenuCsv(new File(baseDir, "menu.csv"));

            // 2. ingredient.csv 읽기 -> ingredientId -> ingredientName
            Map<Long, String> ingredientNameMap = loadIngredientCsv(new File(baseDir, "ingredient.csv"));

            // 3. ingredient_price.csv 읽기 -> ingredientId -> 최신 PriceInfo (가격, 기준일)
            Map<Long, PriceInfo> latestPriceMap = loadLatestIngredientPrices(new File(baseDir, "ingredient_price.csv"));

            // 4. menu_ingredient.csv 읽기 및 결합 -> menuId -> List<MenuIngredientCostVo>
            Map<Long, List<MenuIngredientCostVo>> loadedMenuIngredientsMap = loadMenuIngredients(
                    new File(baseDir, "menu_ingredient.csv"),
                    ingredientNameMap,
                    latestPriceMap
            );

            // 기존 맵 데이터 갱신
            menuMap.clear();
            menuMap.putAll(loadedMenuMap);

            menuIngredientsMap.clear();
            menuIngredientsMap.putAll(loadedMenuIngredientsMap);

            log.info(">> [MemoryCostRepository] CSV mock data 로딩 완료: 총 {}개 메뉴, {}개 메뉴식재료 구성 로드됨",
                    menuMap.size(), menuIngredientsMap.size());

        } catch (Exception e) {
            log.error(">> [MemoryCostRepository] CSV mock data 로딩 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * menu.csv 파싱
     * 컬럼: menu_id, menu_code, name, upper_category, category, slot_type, cooking_method, serving_weight, created_at, updated_at
     */
    private Map<Long, String> loadMenuCsv(File file) {
        Map<Long, String> map = new HashMap<>();
        if (!file.exists()) {
            log.warn("menu.csv 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            return map;
        }

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 3) {
                try {
                    Long menuId = Long.parseLong(cols[0].trim());
                    String menuName = cols[2].trim();
                    map.put(menuId, menuName);
                } catch (NumberFormatException e) {
                    log.debug("menu.csv 파싱 스킵 (헤더 또는 유효하지 않은 행): {}", (Object) cols);
                }
            }
        }
        return map;
    }

    /**
     * ingredient.csv 파싱
     * 컬럼: ingredient_id, ingredient_code, name, category, standard_unit, created_at, updated_at
     */
    private Map<Long, String> loadIngredientCsv(File file) {
        Map<Long, String> map = new HashMap<>();
        if (!file.exists()) {
            log.warn("ingredient.csv 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            return map;
        }

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 3) {
                try {
                    Long ingredientId = Long.parseLong(cols[0].trim());
                    String ingredientName = cols[2].trim();
                    map.put(ingredientId, ingredientName);
                } catch (NumberFormatException e) {
                    log.debug("ingredient.csv 파싱 스킵: {}", (Object) cols);
                }
            }
        }
        return map;
    }

    /**
     * ingredient_price.csv 파싱하여 식재료별 '최신 가격 정보(가장 최근 price_date)'만 추출
     * 컬럼: price_id, ingredient_id, price_date, variety, grade, price_type, original_unit, original_price, standard_unit_price, market, region, source_name, source_item_code, created_at
     */
    private Map<Long, PriceInfo> loadLatestIngredientPrices(File file) {
        Map<Long, PriceInfo> map = new HashMap<>();
        if (!file.exists()) {
            log.warn("ingredient_price.csv 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            return map;
        }

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 9) {
                try {
                    Long ingredientId = Long.parseLong(cols[1].trim());
                    LocalDate priceDate = LocalDate.parse(cols[2].trim());
                    BigDecimal standardUnitPrice = new BigDecimal(cols[8].trim());

                    // 기존에 저장된 데이터가 없거나, 현재 행의 날짜가 더 최신인 경우 갱신
                    PriceInfo currentBest = map.get(ingredientId);
                    if (currentBest == null || priceDate.isAfter(currentBest.priceDate)) {
                        map.put(ingredientId, new PriceInfo(priceDate, standardUnitPrice));
                    }
                } catch (Exception e) {
                    log.debug("ingredient_price.csv 파싱 스킵: {}", (Object) cols);
                }
            }
        }
        return map;
    }

    /**
     * menu_ingredient.csv 파싱 및 식재료명, 최신 단가 결합
     * 컬럼: menu_ingredient_id, menu_id, ingredient_id, quantity, is_primary, created_at
     */
    private Map<Long, List<MenuIngredientCostVo>> loadMenuIngredients(
            File file,
            Map<Long, String> ingredientNameMap,
            Map<Long, PriceInfo> latestPriceMap) {

        Map<Long, List<MenuIngredientCostVo>> map = new HashMap<>();
        if (!file.exists()) {
            log.warn("menu_ingredient.csv 파일이 존재하지 않습니다: {}", file.getAbsolutePath());
            return map;
        }

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 4) {
                try {
                    Long menuId = Long.parseLong(cols[1].trim());
                    Long ingredientId = Long.parseLong(cols[2].trim());
                    BigDecimal quantity = new BigDecimal(cols[3].trim());

                    String ingredientName = ingredientNameMap.getOrDefault(ingredientId, "식재료-" + ingredientId);
                    PriceInfo priceInfo = latestPriceMap.get(ingredientId);

                    BigDecimal unitPrice = (priceInfo != null) ? priceInfo.standardUnitPrice : BigDecimal.ZERO;
                    LocalDate priceDate = (priceInfo != null) ? priceInfo.priceDate : LocalDate.now();

                    MenuIngredientCostVo vo = new MenuIngredientCostVo(
                            ingredientId,
                            ingredientName,
                            quantity,
                            unitPrice,
                            priceDate
                    );

                    map.computeIfAbsent(menuId, k -> new ArrayList<>()).add(vo);
                } catch (Exception e) {
                    log.debug("menu_ingredient.csv 파싱 스킵: {}", (Object) cols);
                }
            }
        }
        return map;
    }

    /**
     * CSV 파일을 읽어 행별 문자열 배열 리스트로 반환 (첫 번째 헤더 행 자동 제외, BOM 제거)
     */
    private List<String[]> readCsvRows(File file) {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                // BOM 제거
                if (isFirstLine) {
                    if (line.startsWith("\uFEFF")) {
                        line = line.substring(1);
                    }
                    isFirstLine = false;
                    // 헤더 행 건너뛰기
                    continue;
                }

                String[] tokens = parseCsvLine(line);
                list.add(tokens);
            }
        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패: {} ({})", file.getAbsolutePath(), e.getMessage());
        }
        return list;
    }

    /**
     * 간단한 콤마 구분 파서 (따옴표 내 콤마 처리 포함)
     */
    private String[] parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());

        return tokens.toArray(new String[0]);
    }

    @Override
    public List<Long> findAllMenuIds() {
        List<Long> ids = new ArrayList<>(menuMap.keySet());
        ids.sort(Long::compareTo);
        return ids;
    }

    @Override
    public Optional<String> findMenuNameById(Long menuId) {
        return Optional.ofNullable(menuMap.get(menuId));
    }

    @Override
    public List<MenuIngredientCostVo> findLatestIngredientsByMenuId(Long menuId) {
        return menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
    }

    // 내부 식재료 가격 정보 DTO
    private static class PriceInfo {
        private final LocalDate priceDate;
        private final BigDecimal standardUnitPrice;

        public PriceInfo(LocalDate priceDate, BigDecimal standardUnitPrice) {
            this.priceDate = priceDate;
            this.standardUnitPrice = standardUnitPrice;
        }
    }
}
