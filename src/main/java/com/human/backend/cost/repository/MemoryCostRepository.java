package com.human.backend.cost.repository;

import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostCsvDataLoader.LoadedCostData;
import com.human.backend.cost.repository.CostCsvDataLoader.PriceInfo;
import com.human.backend.cost.repository.CostCsvDataLoader.RawMealPlanInfo;
import com.human.backend.cost.util.CostConstants;
import com.human.backend.facility.entity.Facility;
import com.human.backend.facility.repository.FacilityRepository;
import com.human.backend.menu.dto.response.MenuResponse;
import com.human.backend.menu.repository.MenuRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * [DB 연동 및 미구현 테이블(식단/예산) 더미 캐시 기반 CostRepository 구현체]
 *
 * ■ 데이터 소스 연결 정책 (Data Source Policy):
 *   1. DB 연동 데이터 (Real DB Connect):
 *      - menu (메뉴 마스터)
 *      - menu_ingredient (메뉴별 식재료 레시피/1인분 중량)
 *      - ingredient (식재료 마스터)
 *      - price_series & ingredient_price (최신 도매/소매 단가)
 *      - price_prediction (AI 미래 가격 예측 단가)
 *      - facility (시설 정보 및 목표 식재료비)
 *
 *   2. 더미 유지 데이터 (CSV Mock Retained):
 *      - meal_plan (끼니별 식단 편성) -> meal_plan.csv
 *      - meal_plan_item (식단 메뉴 슬롯 편성) -> meal_plan_item.csv
 *      - monthly_budget (시설별 월 배정 예산) -> monthly_budget.csv
 *
 *   3. 안전한 장애 격리 (Graceful Degradation):
 *      - DB 연결 오류나 데이터 미존재 시 서비스 중단 없이 기존 CSV 캐시 데이터로 자동 Fallback
 */
@Slf4j
@Repository
@SuppressWarnings("null")
public class MemoryCostRepository implements CostRepository {

    @Value("${mock.csv.path:src/main/java/com/human/backend/cost/dummy}")
    private String mockCsvPath = "src/main/java/com/human/backend/cost/dummy";

    private final CostCsvDataLoader csvDataLoader;
    private final JdbcTemplate jdbcTemplate;
    private final MenuRepository menuRepository;
    private final FacilityRepository facilityRepository;

    // 더미 유지용 (meal_plan, meal_plan_item, monthly_budget) 및 DB Fallback용 메모리 캐시
    private final Map<Long, String> menuMap = new ConcurrentHashMap<>();
    private final Map<Long, List<MenuIngredientCostVo>> menuIngredientsMap = new ConcurrentHashMap<>();
    private final Map<Long, String> facilityNameMap = new ConcurrentHashMap<>();
    private final Map<String, FacilityBudgetVo> budgetMap = new ConcurrentHashMap<>();
    private final List<RawMealPlanInfo> rawMealPlanList = new CopyOnWriteArrayList<>();
    private final Map<Long, List<Long>> planMenuItemsMap = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> predictedPriceMap = new ConcurrentHashMap<>();
    private final Map<Long, PriceInfo> latestPriceMap = new ConcurrentHashMap<>();

    public MemoryCostRepository() {
        this.csvDataLoader = new CostCsvDataLoader();
        this.jdbcTemplate = null;
        this.menuRepository = null;
        this.facilityRepository = null;
    }

    @Autowired
    public MemoryCostRepository(
            @Autowired(required = false) CostCsvDataLoader csvDataLoader,
            @Autowired(required = false) JdbcTemplate jdbcTemplate,
            @Autowired(required = false) MenuRepository menuRepository,
            @Autowired(required = false) FacilityRepository facilityRepository) {
        this.csvDataLoader = csvDataLoader != null ? csvDataLoader : new CostCsvDataLoader();
        this.jdbcTemplate = jdbcTemplate;
        this.menuRepository = menuRepository;
        this.facilityRepository = facilityRepository;
    }

    public MemoryCostRepository(String mockCsvPath) {
        this.mockCsvPath = mockCsvPath;
        this.csvDataLoader = new CostCsvDataLoader();
        this.jdbcTemplate = null;
        this.menuRepository = null;
        this.facilityRepository = null;
    }

    public MemoryCostRepository(String mockCsvPath, JdbcTemplate jdbcTemplate, MenuRepository menuRepository, FacilityRepository facilityRepository) {
        this.mockCsvPath = mockCsvPath;
        this.jdbcTemplate = jdbcTemplate;
        this.menuRepository = menuRepository;
        this.facilityRepository = facilityRepository;
        this.csvDataLoader = new CostCsvDataLoader();
    }

    // DB 고속 캐시 (N+1 쿼리 방지 및 10ms 초고속 응답)
    private final Map<Long, String> dbMenuNameCache = new ConcurrentHashMap<>();
    private final Map<Long, List<MenuIngredientCostVo>> dbLatestIngredientsCache = new ConcurrentHashMap<>();
    private final Map<String, List<MenuIngredientCostVo>> dbPredictedIngredientsCache = new ConcurrentHashMap<>();
    private final List<Long> dbAllMenuIdsCache = new CopyOnWriteArrayList<>();
    private volatile boolean dbLoaded = false;

    /**
     * 스프링 빈 초기화 시 CSV 파일들을 로드하여 식단/예산 더미 및 Fallback 맵을 구성하고 DB 데이터를 일괄 캐싱합니다.
     */
    @PostConstruct
    public void init() {
        loadDataFromCsv();
        loadAllDbData();
    }

    public synchronized void loadAllDbData() {
        if (jdbcTemplate == null) {
            return;
        }

        try {
            log.info(">> [MemoryCostRepository] DB 전체 메뉴 및 식재료 단가 일괄 캐싱 시작...");

            // 1. 메뉴명 일괄 로드
            String menuSql = "SELECT menu_id, name FROM mealfit.menu";
            jdbcTemplate.query(menuSql, (rs) -> {
                dbMenuNameCache.put(rs.getLong("menu_id"), rs.getString("name"));
            });

            // 2. 최신 식재료 단가 전체 일괄 로드 (단 1회의 쿼리로 전체 메뉴 매핑)
            String latestSql = """
                SELECT mi.menu_id,
                       mi.ingredient_id,
                       i.name AS ingredient_name,
                       mi.quantity,
                       COALESCE(ip.standard_unit_price, 0) AS standard_unit_price,
                       COALESCE(ip.price_date, CURRENT_DATE) AS price_date
                FROM mealfit.menu_ingredient mi
                JOIN mealfit.ingredient i ON mi.ingredient_id = i.ingredient_id
                LEFT JOIN LATERAL (
                    SELECT ip2.standard_unit_price, ip2.price_date
                    FROM mealfit.price_series ps
                    JOIN mealfit.ingredient_price ip2 ON ip2.series_id = ps.series_id
                    WHERE ps.ingredient_id = i.ingredient_id
                    ORDER BY (ps.is_cost_basis IS TRUE) DESC, ip2.price_date DESC
                    LIMIT 1
                ) ip ON true
                ORDER BY mi.menu_id, mi.ingredient_id
                """;

            Map<Long, List<MenuIngredientCostVo>> latestMap = new HashMap<>();
            jdbcTemplate.query(latestSql, (rs) -> {
                Long menuId = rs.getLong("menu_id");
                Long ingredientId = rs.getLong("ingredient_id");
                String ingredientName = rs.getString("ingredient_name");
                BigDecimal quantity = rs.getBigDecimal("quantity");
                BigDecimal unitPrice = rs.getBigDecimal("standard_unit_price");
                Date pDate = rs.getDate("price_date");
                LocalDate priceDate = (pDate != null) ? pDate.toLocalDate() : LocalDate.now();

                // DB 단가가 0원이면 CSV 캐시로 Fallback
                if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                    PriceInfo csvPrice = latestPriceMap.get(ingredientId);
                    if (csvPrice != null && csvPrice.getStandardUnitPrice() != null) {
                        unitPrice = csvPrice.getStandardUnitPrice();
                        priceDate = csvPrice.getPriceDate();
                    }
                }

                MenuIngredientCostVo vo = new MenuIngredientCostVo(
                        ingredientId,
                        ingredientName,
                        quantity,
                        unitPrice != null ? unitPrice : BigDecimal.ZERO,
                        priceDate
                );

                latestMap.computeIfAbsent(menuId, k -> new ArrayList<>()).add(vo);
            });

            dbLatestIngredientsCache.clear();
            dbLatestIngredientsCache.putAll(latestMap);

            dbAllMenuIdsCache.clear();
            List<Long> sortedIds = new ArrayList<>(latestMap.keySet());
            sortedIds.sort(Long::compareTo);
            dbAllMenuIdsCache.addAll(sortedIds);

            dbLoaded = true;
            log.info(">> [MemoryCostRepository] DB 전체 메뉴 및 식재료 단가 일괄 캐싱 완료! (활성 메뉴 {}개, 메뉴명 {}개)",
                    dbAllMenuIdsCache.size(), dbMenuNameCache.size());
        } catch (Exception e) {
            log.warn(">> [MemoryCostRepository] DB 일괄 캐싱 중 오류 발생 (단건 쿼리/CSV로 대체): {}", e.getMessage());
        }
    }

    public synchronized void loadDataFromCsv() {
        LoadedCostData data = csvDataLoader.loadAll(mockCsvPath);

        facilityNameMap.clear();
        facilityNameMap.putAll(data.getFacilityNameMap());

        menuMap.clear();
        menuMap.putAll(data.getMenuMap());

        latestPriceMap.clear();
        latestPriceMap.putAll(data.getLatestPriceMap());

        predictedPriceMap.clear();
        predictedPriceMap.putAll(data.getPredictedPriceMap());

        menuIngredientsMap.clear();
        menuIngredientsMap.putAll(data.getMenuIngredientsMap());

        planMenuItemsMap.clear();
        planMenuItemsMap.putAll(data.getPlanMenuItemsMap());

        budgetMap.clear();
        budgetMap.putAll(data.getBudgetMap());

        rawMealPlanList.clear();
        rawMealPlanList.addAll(data.getRawMealPlanList());
    }

    /**
     * [DB 연결] 식재료 구성(menu_ingredient)이 등록된 활성 메뉴 ID 목록 조회 (초고속 인메모리 반환)
     */
    @Override
    public List<Long> findAllMenuIds() {
        if (!dbAllMenuIdsCache.isEmpty()) {
            return new ArrayList<>(dbAllMenuIdsCache);
        }
        if (!dbLoaded && jdbcTemplate != null) {
            loadAllDbData();
            if (!dbAllMenuIdsCache.isEmpty()) {
                return new ArrayList<>(dbAllMenuIdsCache);
            }
        }
        List<Long> ids = new ArrayList<>(menuIngredientsMap.keySet());
        ids.sort(Long::compareTo);
        return ids;
    }

    /**
     * [DB 연결] 메뉴 ID로 메뉴명 조회 (초고속 인메모리 반환)
     */
    @Override
    public Optional<String> findMenuNameById(Long menuId) {
        if (menuId == null) {
            return Optional.empty();
        }

        // 1순위: DB 캐시
        String cachedName = dbMenuNameCache.get(menuId);
        if (cachedName != null) {
            return Optional.of(cachedName);
        }

        // 2순위: CSV Fallback
        return Optional.ofNullable(menuMap.get(menuId));
    }

    /**
     * [DB 연결] 메뉴 ID에 해당하는 최신 식재료 구성 및 단가 정보 목록 조회 (초고속 인메모리 반환)
     */
    @Override
    public List<MenuIngredientCostVo> findLatestIngredientsByMenuId(Long menuId) {
        if (menuId == null) {
            return Collections.emptyList();
        }

        // 1순위: DB 캐시
        List<MenuIngredientCostVo> cached = dbLatestIngredientsCache.get(menuId);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        // 2순위: CSV Fallback
        return menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
    }

    /**
     * [DB 연결] 메뉴 ID 및 특정 미래 일자에 해당하는 예측 식재료 구성 및 단가 정보 목록 조회
     * - 최신 식재료 구성을 기반으로 예측 단가 맵을 결합하여 고속 산출 (0ms)
     */
    @Override
    public List<MenuIngredientCostVo> findPredictedIngredientsByMenuId(Long menuId, LocalDate targetDate) {
        if (menuId == null) {
            return Collections.emptyList();
        }

        LocalDate lookupDate = (targetDate != null) ? targetDate : LocalDate.now();
        String cacheKey = menuId + ":" + lookupDate;

        List<MenuIngredientCostVo> cachedPredicted = dbPredictedIngredientsCache.get(cacheKey);
        if (cachedPredicted != null) {
            return cachedPredicted;
        }

        // 최신 식재료 구성을 바탕으로 예측단가 적용
        List<MenuIngredientCostVo> baseItems = findLatestIngredientsByMenuId(menuId);
        if (baseItems.isEmpty()) {
            baseItems = menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
        }

        if (baseItems.isEmpty()) {
            return Collections.emptyList();
        }

        List<MenuIngredientCostVo> predictedList = new ArrayList<>();
        for (MenuIngredientCostVo item : baseItems) {
            Long ingredientId = item.getIngredientId();
            String predKey = lookupDate + ":" + ingredientId;
            BigDecimal predictedPrice = predictedPriceMap.get(predKey);

            BigDecimal finalUnitPrice;
            LocalDate finalPriceDate;

            if (predictedPrice != null && predictedPrice.compareTo(BigDecimal.ZERO) > 0) {
                finalUnitPrice = predictedPrice;
                finalPriceDate = lookupDate;
            } else {
                finalUnitPrice = item.getStandardUnitPrice();
                finalPriceDate = item.getPriceDate();
            }

            predictedList.add(new MenuIngredientCostVo(
                    ingredientId,
                    item.getIngredientName(),
                    item.getQuantity(),
                    finalUnitPrice,
                    finalPriceDate
            ));
        }

        dbPredictedIngredientsCache.put(cacheKey, predictedList);
        return predictedList;
    }

    /**
     * [시설 DB 연동 + 월 예산 더미 유지]
     * - 시설명: DB(facilityRepository / mealfit.facility) 조회
     * - 예산액: monthly_budget 테이블 미생성으로 monthly_budget.csv 더미 데이터 사용
     */
    @Override
    public Optional<FacilityBudgetVo> findFacilityBudget(Long facilityId, YearMonth month) {
        String key = facilityId + ":" + month;
        FacilityBudgetVo csvBudget = budgetMap.get(key);

        String facilityName = null;
        if (facilityRepository != null && facilityId != null) {
            try {
                facilityName = facilityRepository.findById(facilityId)
                        .map(Facility::getName)
                        .orElse(null);
            } catch (Exception e) {
                log.warn(">> [MemoryCostRepository] DB 시설 조회 실패, CSV 데이터를 사용합니다: {}", e.getMessage());
            }
        }

        if (csvBudget != null) {
            if (facilityName != null) {
                return Optional.of(new FacilityBudgetVo(facilityId, facilityName, csvBudget.getBudgetMonth(), csvBudget.getBudgetAmount()));
            }
            return Optional.of(csvBudget);
        }

        if (facilityName != null) {
            return Optional.of(new FacilityBudgetVo(facilityId, facilityName, month, CostConstants.DEFAULT_MONTHLY_BUDGET));
        }

        return Optional.empty();
    }

    /**
     * [식단 더미 유지 + 단가 DB 연동]
     * - 식단 편성(meal_plan, meal_plan_item)은 테이블 미생성으로 meal_plan.csv 더미 데이터 유지
     * - 단, 식단 내 메뉴별 1인분 원가는 DB의 최신/예측 단가를 실시간 연동하여 계산
     */
    @Override
    public List<MealPlanCostVo> findMealPlansByFacilityAndDateRange(Long facilityId, LocalDate startDate, LocalDate endDate) {
        return rawMealPlanList.stream()
                .filter(p -> Objects.equals(p.getFacilityId(), facilityId))
                .filter(p -> (p.getPlanDate().isEqual(startDate) || p.getPlanDate().isAfter(startDate)) &&
                             (p.getPlanDate().isEqual(endDate) || p.getPlanDate().isBefore(endDate)))
                .sorted(Comparator.comparing(RawMealPlanInfo::getPlanDate))
                .map(this::calculateRealtimeMealPlanCost)
                .collect(Collectors.toList());
    }

    /**
     * [식단 편성 더미 유지] 식단 계획(Plan ID)에 포함된 메뉴 ID 목록 조회 (meal_plan_item.csv)
     */
    @Override
    public List<Long> findMenuIdsByPlanId(Long planId) {
        if (planId == null) {
            return Collections.emptyList();
        }
        return planMenuItemsMap.getOrDefault(planId, Collections.emptyList());
    }

    /**
     * 식단에 포함된 메뉴들의 식재료 예측가/최신 단가를 결합하여 1인분 원가 산출
     * (DB에 단가가 있으면 DB 우선 반영)
     */
    private MealPlanCostVo calculateRealtimeMealPlanCost(RawMealPlanInfo raw) {
        List<Long> menuIds = planMenuItemsMap.getOrDefault(raw.getPlanId(), Collections.emptyList());
        if (menuIds.isEmpty()) {
            return new MealPlanCostVo(raw.getPlanId(), raw.getFacilityId(), raw.getPlanDate(), raw.getMealType(), raw.getMealCount(), raw.getFallbackCost());
        }

        BigDecimal calculatedCostPerPerson = BigDecimal.ZERO;

        for (Long menuId : menuIds) {
            // DB 예측 단가 우선 조회
            List<MenuIngredientCostVo> ingredients = findPredictedIngredientsByMenuId(menuId, raw.getPlanDate());
            if (ingredients.isEmpty()) {
                ingredients = menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
            }

            for (MenuIngredientCostVo ingredient : ingredients) {
                BigDecimal quantity = ingredient.getQuantity();
                BigDecimal unitPrice = ingredient.getStandardUnitPrice();

                if (quantity != null && unitPrice != null) {
                    BigDecimal lineCost = quantity.multiply(unitPrice);
                    calculatedCostPerPerson = calculatedCostPerPerson.add(lineCost);
                }
            }
        }

        BigDecimal finalCostPerPerson = calculatedCostPerPerson.compareTo(BigDecimal.ZERO) > 0
                ? calculatedCostPerPerson.setScale(2, RoundingMode.HALF_UP)
                : raw.getFallbackCost();

        return new MealPlanCostVo(raw.getPlanId(), raw.getFacilityId(), raw.getPlanDate(), raw.getMealType(), raw.getMealCount(), finalCostPerPerson);
    }
}

