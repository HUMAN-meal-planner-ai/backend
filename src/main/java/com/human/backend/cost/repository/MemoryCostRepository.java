package com.human.backend.cost.repository;

import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * CSV 파일 기반의 CostRepository 구현체
 * mock_csv_bundle 디렉터리의 CSV 파일들을 읽어 인메모리에 적재하고 원가 및 예산 조회 기능을 제공합니다.
 */
@Slf4j
@Repository
public class MemoryCostRepository implements CostRepository {

    @Value("${mock.csv.path:src/main/java/com/human/backend/cost/dummy}")
    private String mockCsvPath = "src/main/java/com/human/backend/cost/dummy";

    private final Map<Long, String> menuMap = new ConcurrentHashMap<>();
    private final Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap = new ConcurrentHashMap<>();
    private final Map<Long, String> facilityNameMap = new ConcurrentHashMap<>();
    private final Map<String, FacilityBudgetVo> budgetMap = new ConcurrentHashMap<>(); // key: "facilityId:YYYY-MM"
    private final List<RawMealPlanInfo> rawMealPlanList = new CopyOnWriteArrayList<>();
    private final Map<Long, List<Long>> planMenuItemsMap = new ConcurrentHashMap<>(); // planId -> List<menuId>
    private final Map<String, BigDecimal> predictedPriceMap = new ConcurrentHashMap<>(); // key: "YYYY-MM-DD:ingredientId" -> predictedPrice
    private final Map<Long, PriceInfo> latestPriceMap = new ConcurrentHashMap<>(); // ingredientId -> latest PriceInfo

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
            // 1. facility.csv 읽기 -> facilityId -> facilityName
            Map<Long, String> loadedFacilityNameMap = loadFacilityCsv(new File(baseDir, "facility.csv"));

            // 2. menu.csv 읽기 -> menuId -> menuName
            Map<Long, String> loadedMenuMap = loadMenuCsv(new File(baseDir, "menu.csv"));

            // 3. ingredient.csv 읽기 -> ingredientId -> ingredientName
            Map<Long, String> ingredientNameMap = loadIngredientCsv(new File(baseDir, "ingredient.csv"));

            // 4. ingredient_price.csv 읽기 -> ingredientId -> 최신 PriceInfo (가격, 기준일)
            Map<Long, PriceInfo> loadedLatestPriceMap = loadLatestIngredientPrices(new File(baseDir, "ingredient_price.csv"));

            // 5. price_prediction.csv 읽기 -> "targetDate:ingredientId" -> predictedPrice
            Map<String, BigDecimal> loadedPredictedPriceMap = loadPricePredictions(new File(baseDir, "price_prediction.csv"));

            // 6. menu_ingredient.csv 읽기 및 결합 -> menuId -> List<MenuIngredientCostVo>
            Map<Long, List<MenuIngredientCostVo>> loadedMenuIngredientsMap = loadMenuIngredients(
                    new File(baseDir, "menu_ingredient.csv"),
                    ingredientNameMap,
                    loadedLatestPriceMap
            );

            // 7. meal_plan_item.csv 읽기 -> planId -> List<menuId>
            Map<Long, List<Long>> loadedPlanMenuItemsMap = loadPlanMenuItems(new File(baseDir, "meal_plan_item.csv"));

            // 8. monthly_budget.csv 읽기 -> "facilityId:YYYY-MM" -> FacilityBudgetVo
            Map<String, FacilityBudgetVo> loadedBudgetMap = loadBudgetCsv(new File(baseDir, "monthly_budget.csv"), loadedFacilityNameMap);

            // 9. meal_plan.csv 읽기 -> List<RawMealPlanInfo>
            List<RawMealPlanInfo> loadedMealPlans = loadMealPlanCsv(new File(baseDir, "meal_plan.csv"));

            // 기존 맵 및 리스트 갱신
            facilityNameMap.clear();
            facilityNameMap.putAll(loadedFacilityNameMap);

            menuMap.clear();
            menuMap.putAll(loadedMenuMap);

            latestPriceMap.clear();
            latestPriceMap.putAll(loadedLatestPriceMap);

            predictedPriceMap.clear();
            predictedPriceMap.putAll(loadedPredictedPriceMap);

            menuIngredientsMap.clear();
            menuIngredientsMap.putAll(loadedMenuIngredientsMap);

            planMenuItemsMap.clear();
            planMenuItemsMap.putAll(loadedPlanMenuItemsMap);

            budgetMap.clear();
            budgetMap.putAll(loadedBudgetMap);

            rawMealPlanList.clear();
            rawMealPlanList.addAll(loadedMealPlans);

            log.info(">> [MemoryCostRepository] CSV mock data 로딩 완료: 메뉴 {}개, 메뉴식재료 {}개, 시설 {}개, 예산 {}개, 식단 {}개, 예측가격 {}개",
                    menuMap.size(), menuIngredientsMap.size(), facilityNameMap.size(), budgetMap.size(), rawMealPlanList.size(), predictedPriceMap.size());

        } catch (Exception e) {
            log.error(">> [MemoryCostRepository] CSV mock data 로딩 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * facility.csv 파싱
     */
    private Map<Long, String> loadFacilityCsv(File file) {
        Map<Long, String> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 2) {
                try {
                    Long facilityId = Long.parseLong(cols[0].trim());
                    String name = cols[1].trim();
                    map.put(facilityId, name);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * menu.csv 파싱
     */
    private Map<Long, String> loadMenuCsv(File file) {
        Map<Long, String> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 3) {
                try {
                    Long menuId = Long.parseLong(cols[0].trim());
                    String menuName = cols[2].trim();
                    map.put(menuId, menuName);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * ingredient.csv 파싱
     */
    private Map<Long, String> loadIngredientCsv(File file) {
        Map<Long, String> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 3) {
                try {
                    Long ingredientId = Long.parseLong(cols[0].trim());
                    String ingredientName = cols[2].trim();
                    map.put(ingredientId, ingredientName);
                } catch (NumberFormatException ignored) {}
            }
        }
        return map;
    }

    /**
     * ingredient_price.csv 파싱 (최신 가격 추출)
     */
    private Map<Long, PriceInfo> loadLatestIngredientPrices(File file) {
        Map<Long, PriceInfo> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 9) {
                try {
                    Long ingredientId = Long.parseLong(cols[1].trim());
                    LocalDate priceDate = LocalDate.parse(cols[2].trim());
                    BigDecimal standardUnitPrice = new BigDecimal(cols[8].trim());

                    PriceInfo currentBest = map.get(ingredientId);
                    if (currentBest == null || priceDate.isAfter(currentBest.priceDate)) {
                        map.put(ingredientId, new PriceInfo(priceDate, standardUnitPrice));
                    }
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * price_prediction.csv 파싱 (예측 가격 추출)
     * 컬럼: prediction_id, ingredient_id, base_date, target_date, base_price, predicted_price, ...
     */
    private Map<String, BigDecimal> loadPricePredictions(File file) {
        Map<String, BigDecimal> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 6) {
                try {
                    Long ingredientId = Long.parseLong(cols[1].trim());
                    LocalDate targetDate = LocalDate.parse(cols[3].trim());
                    BigDecimal predictedPrice = new BigDecimal(cols[5].trim());

                    String key = targetDate + ":" + ingredientId;
                    map.put(key, predictedPrice);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * meal_plan_item.csv 파싱
     * 컬럼: plan_item_id, plan_id, menu_id, display_order, ...
     */
    private Map<Long, List<Long>> loadPlanMenuItems(File file) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 3) {
                try {
                    Long planId = Long.parseLong(cols[1].trim());
                    Long menuId = Long.parseLong(cols[2].trim());
                    map.computeIfAbsent(planId, k -> new ArrayList<>()).add(menuId);
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * menu_ingredient.csv 파싱
     */
    private Map<Long, List<MenuIngredientCostVo>> loadMenuIngredients(
            File file,
            Map<Long, String> ingredientNameMap,
            Map<Long, PriceInfo> latestPriceMap) {

        Map<Long, List<MenuIngredientCostVo>> map = new HashMap<>();
        if (!file.exists()) return map;

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
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * monthly_budget.csv 파싱
     */
    private Map<String, FacilityBudgetVo> loadBudgetCsv(File file, Map<Long, String> facilityNames) {
        Map<String, FacilityBudgetVo> map = new HashMap<>();
        if (!file.exists()) return map;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 4) {
                try {
                    Long facilityId = Long.parseLong(cols[1].trim());
                    String dateStr = cols[2].trim();
                    YearMonth month = dateStr.length() > 7 
                            ? YearMonth.from(LocalDate.parse(dateStr)) 
                            : YearMonth.parse(dateStr);
                    BigDecimal amount = new BigDecimal(cols[3].trim());
                    String facilityName = facilityNames.getOrDefault(facilityId, "시설 " + facilityId);

                    String key = facilityId + ":" + month;
                    map.put(key, new FacilityBudgetVo(facilityId, facilityName, month, amount));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    /**
     * meal_plan.csv 파싱 (원시 식단 정보)
     */
    private List<RawMealPlanInfo> loadMealPlanCsv(File file) {
        List<RawMealPlanInfo> list = new ArrayList<>();
        if (!file.exists()) return list;

        List<String[]> rows = readCsvRows(file);
        for (String[] cols : rows) {
            if (cols.length >= 7) {
                try {
                    Long planId = Long.parseLong(cols[0].trim());
                    Long facilityId = Long.parseLong(cols[1].trim());
                    LocalDate planDate = LocalDate.parse(cols[3].trim());
                    String mealType = cols[4].trim();
                    Integer mealCount = Integer.parseInt(cols[5].trim());
                    BigDecimal fallbackCost = new BigDecimal(cols[6].trim());

                    list.add(new RawMealPlanInfo(planId, facilityId, planDate, mealType, mealCount, fallbackCost));
                } catch (Exception ignored) {}
            }
        }
        return list;
    }

    private List<String[]> readCsvRows(File file) {
        List<String[]> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                if (isFirstLine) {
                    if (line.startsWith("\uFEFF")) line = line.substring(1);
                    isFirstLine = false;
                    continue;
                }
                list.add(parseCsvLine(line));
            }
        } catch (Exception e) {
            log.error("CSV 파일 읽기 실패: {} ({})", file.getAbsolutePath(), e.getMessage());
        }
        return list;
    }

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

    @Override
    public List<MenuIngredientCostVo> findPredictedIngredientsByMenuId(Long menuId, LocalDate targetDate) {
        List<MenuIngredientCostVo> baseIngredients = menuIngredientsMap.get(menuId);
        if (baseIngredients == null || baseIngredients.isEmpty()) {
            return Collections.emptyList();
        }

        LocalDate lookupDate = (targetDate != null) ? targetDate : LocalDate.now();
        List<MenuIngredientCostVo> result = new ArrayList<>();

        for (MenuIngredientCostVo item : baseIngredients) {
            Long ingredientId = item.getIngredientId();
            String predKey = lookupDate + ":" + ingredientId;
            BigDecimal predictedPrice = predictedPriceMap.get(predKey);

            BigDecimal finalUnitPrice;
            LocalDate finalPriceDate;

            if (predictedPrice != null) {
                finalUnitPrice = predictedPrice;
                finalPriceDate = lookupDate;
            } else {
                // 예측 단가가 없는 경우 최신 시세 적용 (안전한 폴백)
                PriceInfo priceInfo = latestPriceMap.get(ingredientId);
                finalUnitPrice = (priceInfo != null) ? priceInfo.standardUnitPrice : item.getStandardUnitPrice();
                finalPriceDate = (priceInfo != null) ? priceInfo.priceDate : item.getPriceDate();
            }

            result.add(new MenuIngredientCostVo(
                    ingredientId,
                    item.getIngredientName(),
                    item.getQuantity(),
                    finalUnitPrice,
                    finalPriceDate
            ));
        }

        return result;
    }

    @Override
    public Optional<FacilityBudgetVo> findFacilityBudget(Long facilityId, YearMonth month) {
        String key = facilityId + ":" + month;
        return Optional.ofNullable(budgetMap.get(key));
    }

    /**
     * 최신 시세 및 예측 가격을 실시간 곱연산하여 1인당 단가를 동적으로 산출한 MealPlanCostVo 목록 반환
     */
    @Override
    public List<MealPlanCostVo> findMealPlansByFacilityAndDateRange(Long facilityId, LocalDate startDate, LocalDate endDate) {
        return rawMealPlanList.stream()
                .filter(p -> Objects.equals(p.facilityId, facilityId))
                .filter(p -> (p.planDate.isEqual(startDate) || p.planDate.isAfter(startDate)) &&
                             (p.planDate.isEqual(endDate) || p.planDate.isBefore(endDate)))
                .sorted(Comparator.comparing(p -> p.planDate))
                .map(this::calculateRealtimeMealPlanCost)
                .collect(Collectors.toList());
    }

    /**
     * 식단에 포함된 메뉴들의 식재료 예측가/최신 단가를 실시간 곱연산하여 1인분 원가 산출
     */
    private MealPlanCostVo calculateRealtimeMealPlanCost(RawMealPlanInfo raw) {
        List<Long> menuIds = planMenuItemsMap.getOrDefault(raw.planId, Collections.emptyList());
        if (menuIds.isEmpty()) {
            return new MealPlanCostVo(raw.planId, raw.facilityId, raw.planDate, raw.mealType, raw.mealCount, raw.fallbackCost);
        }

        BigDecimal calculatedCostPerPerson = BigDecimal.ZERO;

        for (Long menuId : menuIds) {
            List<MenuIngredientCostVo> ingredients = menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
            for (MenuIngredientCostVo ingredient : ingredients) {
                Long ingredientId = ingredient.getIngredientId();
                BigDecimal quantity = ingredient.getQuantity();

                // 1. 해당 일자의 예측 가격 조회 (price_prediction.csv)
                String predKey = raw.planDate + ":" + ingredientId;
                BigDecimal unitPrice = predictedPriceMap.get(predKey);

                // 2. 예측 가격이 없으면 최신 수집 가격 사용 (ingredient_price.csv)
                if (unitPrice == null) {
                    PriceInfo priceInfo = latestPriceMap.get(ingredientId);
                    unitPrice = (priceInfo != null) ? priceInfo.standardUnitPrice : ingredient.getStandardUnitPrice();
                }

                if (quantity != null && unitPrice != null) {
                    BigDecimal lineCost = quantity.multiply(unitPrice);
                    calculatedCostPerPerson = calculatedCostPerPerson.add(lineCost);
                }
            }
        }

        BigDecimal finalCostPerPerson = calculatedCostPerPerson.compareTo(BigDecimal.ZERO) > 0
                ? calculatedCostPerPerson.setScale(2, RoundingMode.HALF_UP)
                : raw.fallbackCost;

        return new MealPlanCostVo(raw.planId, raw.facilityId, raw.planDate, raw.mealType, raw.mealCount, finalCostPerPerson);
    }

    private static class RawMealPlanInfo {
        private final Long planId;
        private final Long facilityId;
        private final LocalDate planDate;
        private final String mealType;
        private final Integer mealCount;
        private final BigDecimal fallbackCost;

        public RawMealPlanInfo(Long planId, Long facilityId, LocalDate planDate, String mealType, Integer mealCount, BigDecimal fallbackCost) {
            this.planId = planId;
            this.facilityId = facilityId;
            this.planDate = planDate;
            this.mealType = mealType;
            this.mealCount = mealCount;
            this.fallbackCost = fallbackCost;
        }
    }

    private static class PriceInfo {
        private final LocalDate priceDate;
        private final BigDecimal standardUnitPrice;

        public PriceInfo(LocalDate priceDate, BigDecimal standardUnitPrice) {
            this.priceDate = priceDate;
            this.standardUnitPrice = standardUnitPrice;
        }
    }
}


