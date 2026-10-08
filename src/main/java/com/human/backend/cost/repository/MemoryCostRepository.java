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
 * [전체 도메인 실시간 DB 연동 및 장애 격리(Graceful Degradation) CostRepository 구현체]
 *
 * ■ 데이터 소스 연결 정책 (Data Source Policy):
 *   1. 전체 실시간 DB 연동 (100% Full DB Connect):
 *      - menu (메뉴 마스터)
 *      - menu_ingredient (메뉴별 식재료 레시피/1인분 중량)
 *      - ingredient (식재료 마스터)
 *      - price_series & ingredient_price (최신 도매/소매 단가)
 *      - price_prediction (AI 미래 가격 예측 단가)
 *      - facility (시설 정보 및 목표 식재료비)
 *      - meal_plan (끼니별 식단 편성)
 *      - meal_plan_item (식단 메뉴 슬롯 편성)
 *      - budget / monthly_budget (시설별 월 배정 예산)
 *
 *   2. 안전한 장애 격리 (Graceful Degradation):
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
    private final List<RawMealPlanInfo> dbMealPlanList = new CopyOnWriteArrayList<>();
    private final Map<Long, List<Long>> dbPlanMenuItemsMap = new ConcurrentHashMap<>();
    private final Map<String, FacilityBudgetVo> dbBudgetMap = new ConcurrentHashMap<>();
    private final Map<Long, String> dbFacilityNameMap = new ConcurrentHashMap<>();
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

            // 1. 메뉴명 및 카테고리 일괄 로드
            String menuSql = "SELECT menu_id, name, upper_category, category FROM mealfit.menu";
            Map<Long, String> menuCategoryMap = new HashMap<>();
            jdbcTemplate.query(menuSql, (rs) -> {
                long mId = rs.getLong("menu_id");
                String mName = rs.getString("name");
                String uCat = rs.getString("upper_category");
                String cat = rs.getString("category");
                dbMenuNameCache.put(mId, mName != null ? mName : "");
                String fullCat = (uCat != null ? uCat : "") + " " + (cat != null ? cat : "");
                menuCategoryMap.put(mId, fullCat);
            });

            // 2. 최신 식재료 단가 전체 일괄 로드 (단 1회의 쿼리로 전체 메뉴 매핑)
            String latestSql = """
                WITH latest_price_per_series AS MATERIALIZED (
                    SELECT DISTINCT ON (ip2.series_id)
                           ip2.series_id,
                           ip2.standard_unit_price,
                           ip2.original_price,
                           ip2.unit_quantity,
                           ip2.price_date
                    FROM mealfit.ingredient_price ip2
                    ORDER BY ip2.series_id, ip2.price_date DESC, ip2.price_id DESC
                ),
                price_candidates AS (
                    -- 관리자가 승인한 매핑: 메뉴 식재료와 실제 KAMIS 가격 시계열을 연결합니다.
                    SELECT ipm.ingredient_id,
                           latest.standard_unit_price * ipm.conversion_factor AS standard_unit_price,
                           latest.original_price,
                           latest.unit_quantity,
                           ps.original_unit,
                           latest.price_date,
                           ps.source_name,
                           ipm.mapping_type,
                           ipm.confidence_score,
                           ipm.priority,
                           ps.is_cost_basis,
                           ps.price_type,
                           TRUE AS mapped
                    FROM mealfit.ingredient_price_mapping ipm
                    JOIN mealfit.price_series ps ON ps.series_id = ipm.series_id
                    JOIN latest_price_per_series latest ON latest.series_id = ps.series_id
                    WHERE ipm.is_active IS TRUE
                      AND ipm.review_status = 'APPROVED'

                    UNION ALL

                    -- 매핑 테이블 도입 전부터 존재하던 직접 연결도 호환 경로로 유지합니다.
                    SELECT ps.ingredient_id,
                           latest.standard_unit_price,
                           latest.original_price,
                           latest.unit_quantity,
                           ps.original_unit,
                           latest.price_date,
                           ps.source_name,
                           'EXACT' AS mapping_type,
                           1::numeric AS confidence_score,
                           2147483647 AS priority,
                           ps.is_cost_basis,
                           ps.price_type,
                           FALSE AS mapped
                    FROM mealfit.price_series ps
                    JOIN latest_price_per_series latest ON latest.series_id = ps.series_id
                ),
                best_price_per_ingredient AS (
                    SELECT DISTINCT ON (candidate.ingredient_id)
                           candidate.*
                    FROM price_candidates candidate
                    ORDER BY candidate.ingredient_id,
                             candidate.mapped DESC,
                             candidate.priority ASC,
                             candidate.is_cost_basis DESC,
                             (candidate.price_type = 'WHOLESALE') DESC,
                             candidate.price_date DESC
                )
                SELECT mi.menu_id,
                       mi.ingredient_id,
                       i.name AS ingredient_name,
                       i.category AS ingredient_category,
                       i.standard_unit AS ingredient_standard_unit,
                       mi.quantity,
                       COALESCE(ip.standard_unit_price, 0) AS standard_unit_price,
                       COALESCE(ip.original_price, 0) AS original_price,
                       COALESCE(ip.unit_quantity, 1) AS unit_quantity,
                       ip.original_unit,
                       COALESCE(ip.price_date, CURRENT_DATE) AS price_date,
                       ip.source_name,
                       ip.mapping_type,
                       ip.confidence_score
                FROM mealfit.menu_ingredient mi
                JOIN mealfit.ingredient i ON mi.ingredient_id = i.ingredient_id
                LEFT JOIN best_price_per_ingredient ip
                  ON ip.ingredient_id = i.ingredient_id
                ORDER BY mi.menu_id, mi.ingredient_id
                """;

            Map<Long, List<MenuIngredientCostVo>> latestMap = new HashMap<>();
            jdbcTemplate.query(latestSql, (rs) -> {
                Long menuId = rs.getLong("menu_id");
                Long ingredientId = rs.getLong("ingredient_id");
                String ingredientName = rs.getString("ingredient_name");
                String ingredientCategory = rs.getString("ingredient_category");
                Boolean isPrimary = false;
                BigDecimal quantity = rs.getBigDecimal("quantity");
                BigDecimal unitPrice = rs.getBigDecimal("standard_unit_price");
                BigDecimal origUnitQty = rs.getBigDecimal("unit_quantity");
                String origUnit = rs.getString("original_unit");
                Date pDate = rs.getDate("price_date");
                LocalDate priceDate = (pDate != null) ? pDate.toLocalDate() : LocalDate.now();
                String priceSource = rs.getString("source_name");
                String mappingType = rs.getString("mapping_type");
                BigDecimal confidenceScore = rs.getBigDecimal("confidence_score");

                // DB 단가가 0원이면 CSV 캐시로 Fallback
                if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) == 0) {
                    PriceInfo csvPrice = latestPriceMap.get(ingredientId);
                    if (csvPrice != null && csvPrice.getStandardUnitPrice() != null) {
                        unitPrice = csvPrice.getStandardUnitPrice();
                        priceDate = csvPrice.getPriceDate();
                    }
                }

                MenuIngredientCostVo rawVo = new MenuIngredientCostVo(
                        ingredientId,
                        ingredientName,
                        ingredientCategory,
                        isPrimary,
                        quantity,
                        unitPrice != null ? unitPrice : BigDecimal.ZERO,
                        priceDate,
                        priceSource,
                        mappingType,
                        confidenceScore
                );

                // 데이터 현실화 및 정제 파이프라인 통과
                String menuName = dbMenuNameCache.getOrDefault(menuId, "");
                String menuCat = menuCategoryMap.getOrDefault(menuId, "");
                MenuIngredientCostVo sanitizedVo = sanitizeIngredientCost(menuName, menuCat, rawVo);

                latestMap.computeIfAbsent(menuId, k -> new ArrayList<>()).add(sanitizedVo);
            });

            fillMissingPricesWithCategoryMedian(latestMap);
            markPrimaryIngredients(latestMap);

            dbLatestIngredientsCache.clear();
            dbLatestIngredientsCache.putAll(latestMap);

            dbAllMenuIdsCache.clear();
            // 1인분 유효 원가가 있는 메뉴만 활성 메뉴 ID 목록으로 등록
            List<Long> validMenuIds = latestMap.entrySet().stream()
                    .filter(entry -> {
                        BigDecimal totalMenuCost = BigDecimal.ZERO;
                        for (MenuIngredientCostVo item : entry.getValue()) {
                            if (item.getQuantity() != null && item.getStandardUnitPrice() != null) {
                                totalMenuCost = totalMenuCost.add(item.getQuantity().multiply(item.getStandardUnitPrice()));
                            }
                        }
                        // 1인분 원가가 100원 이상이고 15,000원 이하인 유효 급식 메뉴만 선별
                        return totalMenuCost.compareTo(BigDecimal.valueOf(100)) >= 0
                                && totalMenuCost.compareTo(BigDecimal.valueOf(15000)) <= 0;
                    })
                    .map(Map.Entry::getKey)
                    .sorted(Long::compareTo)
                    .toList();
            dbAllMenuIdsCache.addAll(validMenuIds);

            // 3. 시설 정보 일괄 로드
            try {
                String facSql = "SELECT facility_id, name FROM mealfit.facility";
                jdbcTemplate.query(facSql, rs -> {
                    long fId = rs.getLong("facility_id");
                    String fName = rs.getString("name");
                    if (fName != null) {
                        dbFacilityNameMap.put(fId, fName);
                    }
                });
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB 시설 목록 조회 생략: {}", e.getMessage());
            }

            // 4. 식단 (meal_plan) DB 일괄 로드
            try {
                String mealPlanSql = """
                    SELECT plan_id, facility_id, plan_date, meal_type, meal_count
                    FROM mealfit.meal_plan
                    ORDER BY plan_date, meal_type, plan_id
                    """;
                List<RawMealPlanInfo> dbPlans = new ArrayList<>();
                jdbcTemplate.query(mealPlanSql, rs -> {
                    long pId = rs.getLong("plan_id");
                    long fId = rs.getLong("facility_id");
                    Object pDateObj = rs.getObject("plan_date");
                    LocalDate planDate = LocalDate.now();
                    if (pDateObj instanceof Date d) {
                        planDate = d.toLocalDate();
                    } else if (pDateObj instanceof LocalDate ld) {
                        planDate = ld;
                    } else if (pDateObj != null) {
                        try {
                            planDate = LocalDate.parse(pDateObj.toString().substring(0, 10));
                        } catch (Exception ignored) {}
                    }
                    String mType = rs.getString("meal_type");
                    int mCount = rs.getInt("meal_count");
                    dbPlans.add(new RawMealPlanInfo(pId, fId, planDate, mType, mCount, BigDecimal.valueOf(3500)));
                });
                if (!dbPlans.isEmpty()) {
                    dbMealPlanList.clear();
                    dbMealPlanList.addAll(dbPlans);
                    log.info(">> [MemoryCostRepository] DB 식단(meal_plan) {}건 로드 완료", dbPlans.size());
                }
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB meal_plan 로드 실패(CSV Fallback 사용): {}", e.getMessage());
            }

            // 5. 식단 메뉴 편성 (meal_plan_item) DB 일괄 로드
            try {
                String planItemSql = """
                    SELECT plan_id, menu_id, display_order
                    FROM mealfit.meal_plan_item
                    ORDER BY plan_id, display_order
                    """;
                Map<Long, List<Long>> dbPlanItems = new HashMap<>();
                jdbcTemplate.query(planItemSql, rs -> {
                    long planId = rs.getLong("plan_id");
                    long menuId = rs.getLong("menu_id");
                    dbPlanItems.computeIfAbsent(planId, k -> new ArrayList<>()).add(menuId);
                });
                if (!dbPlanItems.isEmpty()) {
                    dbPlanMenuItemsMap.clear();
                    dbPlanMenuItemsMap.putAll(dbPlanItems);
                    log.info(">> [MemoryCostRepository] DB 식단 메뉴 편성(meal_plan_item) {}건 로드 완료", dbPlanItems.size());
                }
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB meal_plan_item 로드 실패(CSV Fallback 사용): {}", e.getMessage());
            }

            // 6. 예산 (budget / monthly_budget) DB 일괄 로드
            try {
                Map<String, FacilityBudgetVo> dbBudgets = new HashMap<>();
                try {
                    String budgetSql = "SELECT facility_id, budget_month, budget_amount FROM mealfit.budget";
                    jdbcTemplate.query(budgetSql, rs -> {
                        long fId = rs.getLong("facility_id");
                        Object bMonthObj = rs.getObject("budget_month");
                        YearMonth ym = YearMonth.now();
                        if (bMonthObj instanceof Date d) {
                            ym = YearMonth.from(d.toLocalDate());
                        } else if (bMonthObj instanceof LocalDate ld) {
                            ym = YearMonth.from(ld);
                        } else if (bMonthObj != null) {
                            try {
                                ym = YearMonth.parse(bMonthObj.toString().trim().substring(0, 7));
                            } catch (Exception ignored) {}
                        }
                        BigDecimal amount = rs.getBigDecimal("budget_amount");
                        String fName = dbFacilityNameMap.getOrDefault(fId, "시설 " + fId);
                        dbBudgets.put(fId + ":" + ym, new FacilityBudgetVo(fId, fName, ym, amount));
                    });
                } catch (Exception ex) {
                    String monthlyBudgetSql = "SELECT facility_id, budget_month, budget_amount FROM mealfit.monthly_budget";
                    jdbcTemplate.query(monthlyBudgetSql, rs -> {
                        long fId = rs.getLong("facility_id");
                        Object bMonthObj = rs.getObject("budget_month");
                        YearMonth ym = YearMonth.now();
                        if (bMonthObj instanceof Date d) {
                            ym = YearMonth.from(d.toLocalDate());
                        } else if (bMonthObj instanceof LocalDate ld) {
                            ym = YearMonth.from(ld);
                        } else if (bMonthObj != null) {
                            try {
                                ym = YearMonth.parse(bMonthObj.toString().trim().substring(0, 7));
                            } catch (Exception ignored) {}
                        }
                        BigDecimal amount = rs.getBigDecimal("budget_amount");
                        String fName = dbFacilityNameMap.getOrDefault(fId, "시설 " + fId);
                        dbBudgets.put(fId + ":" + ym, new FacilityBudgetVo(fId, fName, ym, amount));
                    });
                }
                if (!dbBudgets.isEmpty()) {
                    dbBudgetMap.clear();
                    dbBudgetMap.putAll(dbBudgets);
                    log.info(">> [MemoryCostRepository] DB 예산 {}건 로드 완료", dbBudgets.size());
                }
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB 예산 테이블 로드 실패(CSV Fallback 사용): {}", e.getMessage());
            }

                        // 7. AI 미래 식재료 예측 단가 (price_prediction) DB 일괄 로드
            try {
                String predSql = """
                    SELECT DISTINCT ON (COALESCE(ipm.ingredient_id, ps.ingredient_id), pp.target_date)
                           COALESCE(ipm.ingredient_id, ps.ingredient_id) AS ingredient_id,
                           pp.target_date,
                           pp.predicted_price
                    FROM mealfit.price_prediction pp
                    JOIN mealfit.price_series ps ON ps.series_id = pp.series_id
                    LEFT JOIN mealfit.ingredient_price_mapping ipm
                           ON ipm.series_id = pp.series_id AND ipm.is_active IS TRUE AND ipm.review_status = 'APPROVED'
                    WHERE pp.predicted_price IS NOT NULL
                    ORDER BY COALESCE(ipm.ingredient_id, ps.ingredient_id), pp.target_date, pp.prediction_id DESC
                    """;
                Map<String, BigDecimal> dbPredMap = new HashMap<>();
                jdbcTemplate.query(predSql, rs -> {
                    long ingId = rs.getLong("ingredient_id");
                    Object tDateObj = rs.getObject("target_date");
                    LocalDate targetDate = null;
                    if (tDateObj instanceof Date d) {
                        targetDate = d.toLocalDate();
                    } else if (tDateObj instanceof LocalDate ld) {
                        targetDate = ld;
                    } else if (tDateObj != null) {
                        try {
                            targetDate = LocalDate.parse(tDateObj.toString().trim().substring(0, 10));
                        } catch (Exception ignored) {}
                    }
                    BigDecimal predPrice = rs.getBigDecimal("predicted_price");
                    if (targetDate != null && predPrice != null && predPrice.compareTo(BigDecimal.ZERO) > 0) {
                        dbPredMap.put(targetDate + ":" + ingId, predPrice);
                    }
                });
                if (!dbPredMap.isEmpty()) {
                    predictedPriceMap.putAll(dbPredMap);
                    log.info(">> [MemoryCostRepository] DB AI 미래 예측 가격(price_prediction) {}건 로드 완료", dbPredMap.size());
                }
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB price_prediction 로드 실패(CSV Fallback 유지): {}", e.getMessage());
            }

            dbLoaded = true;
            log.info(">> [MemoryCostRepository] DB 전체 데이터(메뉴, 단가, 식단, 예산) 일괄 캐싱 & 정제 완료! (활성 메뉴 {}개, 메뉴명 {}개, 식단 {}건, 예산 {}건)",
                    dbAllMenuIdsCache.size(), dbMenuNameCache.size(), dbMealPlanList.size(), dbBudgetMap.size());
        } catch (Exception e) {
            log.warn(">> [MemoryCostRepository] DB 일괄 캐싱 중 오류 발생 (단건 쿼리/CSV로 대체): {}", e.getMessage());
        }
    }

    /**
     * 단가가 없는(0원) 식재료를 같은 카테고리 식재료 단가의 중앙값으로 추정해 채운다.
     * 추정된 항목은 mappingType=CATEGORY_AVG, 낮은 신뢰도로 표시되어 estimated로 구분된다.
     */
    private void fillMissingPricesWithCategoryMedian(Map<Long, List<MenuIngredientCostVo>> menuItemsMap) {
        Map<String, List<BigDecimal>> pricesByCategory = new HashMap<>();
        menuItemsMap.values().forEach(items -> items.forEach(item -> {
            BigDecimal price = item.getStandardUnitPrice();
            if (item.getIngredientCategory() != null && price != null && price.signum() > 0) {
                pricesByCategory.computeIfAbsent(item.getIngredientCategory(), k -> new ArrayList<>()).add(price);
            }
        }));

        Map<String, BigDecimal> medianByCategory = new HashMap<>();
        pricesByCategory.forEach((category, prices) -> {
            Collections.sort(prices);
            medianByCategory.put(category, prices.get(prices.size() / 2));
        });

        int filled = 0;
        for (List<MenuIngredientCostVo> items : menuItemsMap.values()) {
            for (int i = 0; i < items.size(); i++) {
                MenuIngredientCostVo item = items.get(i);
                BigDecimal price = item.getStandardUnitPrice();
                BigDecimal median = medianByCategory.get(item.getIngredientCategory());
                if ((price == null || price.signum() <= 0) && median != null) {
                    items.set(i, new MenuIngredientCostVo(
                            item.getIngredientId(), item.getIngredientName(), item.getIngredientCategory(),
                            item.getIsPrimary(), item.getQuantity(), median, item.getPriceDate(),
                            "CATEGORY_MEDIAN", "CATEGORY_AVG", BigDecimal.valueOf(0.3)));
                    filled++;
                }
            }
        }
        log.info(">> [MemoryCostRepository] 단가 미등록 식재료 {}건을 카테고리 중앙값으로 추정했습니다.", filled);
    }

    private static final List<String> NON_PRIMARY_CATEGORY_KEYWORDS = List.of("조미", "유지", "양념", "소스", "향신", "당류", "첨가");
    private static final List<String> PROTEIN_CATEGORY_KEYWORDS = List.of("육류", "수산", "어류", "난류", "알류", "두류");

    /**
     * 메뉴별 주재료를 판별해 isPrimary를 채운다.
     * 1) 메뉴명에 이름이 등장하는 식재료, 없으면 2) 사용량이 가장 많은 단백질 식재료,
     * 없으면 3) 조미료·유지류를 제외하고 사용량이 가장 많은 식재료를 주재료로 본다.
     */
    private void markPrimaryIngredients(Map<Long, List<MenuIngredientCostVo>> menuItemsMap) {
        menuItemsMap.forEach((menuId, items) -> {
            String menuName = dbMenuNameCache.getOrDefault(menuId, "").replaceAll("\\s+", "");
            Set<Integer> primaryIdx = new HashSet<>();

            for (int i = 0; i < items.size(); i++) {
                MenuIngredientCostVo item = items.get(i);
                if (isNonPrimaryCategory(item.getIngredientCategory())) continue;
                String token = firstIngredientToken(item.getIngredientName());
                if (token.length() >= 2 && menuName.contains(token)) primaryIdx.add(i);
            }
            if (primaryIdx.isEmpty()) {
                int top = topQuantityIndex(items, true);
                if (top < 0) top = topQuantityIndex(items, false);
                if (top >= 0) primaryIdx.add(top);
            }
            for (int idx : primaryIdx) {
                MenuIngredientCostVo item = items.get(idx);
                items.set(idx, new MenuIngredientCostVo(
                        item.getIngredientId(), item.getIngredientName(), item.getIngredientCategory(),
                        true, item.getQuantity(), item.getStandardUnitPrice(), item.getPriceDate(),
                        item.getPriceSource(), item.getMappingType(), item.getConfidenceScore()));
            }
        });
    }

    private boolean isNonPrimaryCategory(String category) {
        return category != null && NON_PRIMARY_CATEGORY_KEYWORDS.stream().anyMatch(category::contains);
    }

    private String firstIngredientToken(String ingredientName) {
        if (ingredientName == null) return "";
        return ingredientName.split("[,(]")[0].replaceAll("\\s+", "");
    }

    private int topQuantityIndex(List<MenuIngredientCostVo> items, boolean proteinOnly) {
        int best = -1;
        for (int i = 0; i < items.size(); i++) {
            MenuIngredientCostVo item = items.get(i);
            String category = item.getIngredientCategory();
            if (isNonPrimaryCategory(category)) continue;
            if (proteinOnly && (category == null || PROTEIN_CATEGORY_KEYWORDS.stream().noneMatch(category::contains))) continue;
            if (best < 0 || item.getQuantity().compareTo(items.get(best).getQuantity()) > 0) best = i;
        }
        return best;
    }

    /**
     * [데이터 현실화 및 전면 정제 파이프라인]
     * 메뉴명, 카테고리, 식재료 특성을 종합하여 비현실적인 레시피 중량(quantity)과 단가를
     * 실제 단체급식 1인분 배식 기준(30g~120g)으로 정밀 정제합니다.
     */
    private MenuIngredientCostVo sanitizeIngredientCost(String menuName, String menuCategory, MenuIngredientCostVo item) {
        if (item == null) return null;

        String name = (menuName != null) ? menuName.trim() : "";
        String cat = (menuCategory != null) ? menuCategory.trim() : "";
        String ingName = (item.getIngredientName() != null) ? item.getIngredientName().trim() : "";
        BigDecimal qty = (item.getQuantity() != null) ? item.getQuantity() : BigDecimal.ZERO;
        BigDecimal unitPrice = (item.getStandardUnitPrice() != null) ? item.getStandardUnitPrice() : BigDecimal.ZERO;

        boolean isDessert = isDessertOrSnack(name, cat);
        boolean isSnackOrSkewer = isSnackOrSkewerMenu(name, cat);
        boolean isNoodle = isNoodleMenu(name, cat);
        boolean isRice = isRiceMenu(name, cat);
        boolean isSoup = isSoupMenu(name, cat);
        boolean isMain = isMainMenu(name, cat);

        // 1. 면류 (메밀국수, 모밀, 소바, 잔치국수, 칼국수, 우동, 냉면, 쫄면, 파스타, 짜장, 짬뽕 등)
        if (isNoodle) {
            // 면류(메밀면, 국수, 소면, 우동면, 스파게티면, 라면 등): 1인분 기준 건면 80g / 생면 110g
            if (ingName.contains("면") || ingName.contains("국수") || ingName.contains("스파게티") || ingName.contains("파스타") || ingName.contains("메밀") || ingName.contains("당면")) {
                if (qty.compareTo(BigDecimal.valueOf(80)) > 0) {
                    qty = BigDecimal.valueOf(75.0);
                }
            } else if (ingName.contains("쯔유") || ingName.contains("장국") || ingName.contains("소스") || ingName.contains("육수")) {
                if (qty.compareTo(BigDecimal.valueOf(20)) > 0) {
                    qty = BigDecimal.valueOf(15.0);
                }
            } else if (ingName.contains("가쓰오") || ingName.contains("다시마") || ingName.contains("멸치")) {
                if (qty.compareTo(BigDecimal.valueOf(5)) > 0) {
                    qty = BigDecimal.valueOf(3.0);
                }
            } else if (ingName.contains("김") || ingName.contains("김가루") || ingName.contains("와사비") || ingName.contains("겨자")) {
                if (qty.compareTo(BigDecimal.valueOf(3)) > 0) {
                    qty = BigDecimal.valueOf(1.5);
                }
            } else if (ingName.contains("무") || ingName.contains("오이") || ingName.contains("파") || ingName.contains("대파") || ingName.contains("양파")) {
                if (qty.compareTo(BigDecimal.valueOf(25)) > 0) {
                    qty = BigDecimal.valueOf(15.0);
                }
            } else if (qty.compareTo(BigDecimal.valueOf(50)) > 0) {
                qty = BigDecimal.valueOf(30.0);
            }
        }
        // 2. 꼬치/분식/간식류 (떡꼬치, 소떡소떡, 닭꼬치, 떡볶이, 핫도그, 어묵바, 튀김류 등)
        else if (isSnackOrSkewer) {
            // 떡(가래떡, 떡볶이떡, 쌀떡 등): 1개/1인분 꼬치 기준 35g
            if (ingName.contains("떡") || ingName.contains("가래떡") || ingName.contains("떡볶이") || ingName.contains("쌀")) {
                if (qty.compareTo(BigDecimal.valueOf(40)) > 0) {
                    qty = BigDecimal.valueOf(35.0);
                }
            } else if (ingName.contains("소시지") || ingName.contains("비엔나") || ingName.contains("햄")) {
                if (qty.compareTo(BigDecimal.valueOf(40)) > 0) {
                    qty = BigDecimal.valueOf(30.0);
                }
            } else if (ingName.contains("어묵") || ingName.contains("오뎅")) {
                if (qty.compareTo(BigDecimal.valueOf(45)) > 0) {
                    qty = BigDecimal.valueOf(35.0);
                }
            } else if (ingName.contains("고추장") || ingName.contains("케첩") || ingName.contains("소스") || ingName.contains("양념")) {
                if (qty.compareTo(BigDecimal.valueOf(10)) > 0) {
                    qty = BigDecimal.valueOf(5.0);
                }
            } else if (ingName.contains("설탕") || ingName.contains("물엿") || ingName.contains("올리고당")) {
                if (qty.compareTo(BigDecimal.valueOf(8)) > 0) {
                    qty = BigDecimal.valueOf(4.0);
                }
            } else if (ingName.contains("식용유") || ingName.contains("기름")) {
                if (qty.compareTo(BigDecimal.valueOf(6)) > 0) {
                    qty = BigDecimal.valueOf(3.0);
                }
            } else if (qty.compareTo(BigDecimal.valueOf(45)) > 0) {
                qty = BigDecimal.valueOf(30.0);
            }
        }
        // 3. 디저트/떡/후식류 (백설기, 꿀떡, 인절미, 송편, 경단, 과일, 빵, 음료 등)
        else if (isDessert) {
            // 쌀가루, 멥쌀, 찹쌀, 밀가루 등 주원료: 1인분 배식량 기준 25g ~ 30g
            if (ingName.contains("쌀") || ingName.contains("가루") || ingName.contains("밀가루") || ingName.contains("전분") || ingName.contains("떡")) {
                if (qty.compareTo(BigDecimal.valueOf(35)) > 0) {
                    qty = BigDecimal.valueOf(25.0);
                }
            } else if (ingName.contains("꿀") || ingName.contains("조청") || ingName.contains("시럽") || ingName.contains("연유")) {
                if (qty.compareTo(BigDecimal.valueOf(5)) > 0) {
                    qty = BigDecimal.valueOf(3.0);
                }
            } else if (ingName.contains("깨") || ingName.contains("참깨") || ingName.contains("들깨") || ingName.contains("콩고물")) {
                if (qty.compareTo(BigDecimal.valueOf(3)) > 0) {
                    qty = BigDecimal.valueOf(2.0);
                }
            } else if (ingName.contains("설탕") || ingName.contains("물엿") || ingName.contains("당류")) {
                if (qty.compareTo(BigDecimal.valueOf(8)) > 0) {
                    qty = BigDecimal.valueOf(4.0);
                }
            } else if (qty.compareTo(BigDecimal.valueOf(35)) > 0) {
                qty = BigDecimal.valueOf(25.0);
            }
        }
        // 4. 주찬/메인 육류·생선류 (제육, 불고기, 돈까스, 닭갈비, 갈비찜, 생선구이 등)
        else if (isMain) {
            if (ingName.contains("육") || ingName.contains("고기") || ingName.contains("돼지") || ingName.contains("소고기") || ingName.contains("닭") || ingName.contains("오리") || ingName.contains("생선") || ingName.contains("갈비")) {
                if (qty.compareTo(BigDecimal.valueOf(120)) > 0) {
                    qty = BigDecimal.valueOf(100.0);
                }
            } else if (ingName.contains("양파") || ingName.contains("대파") || ingName.contains("양배추") || ingName.contains("버섯")) {
                if (qty.compareTo(BigDecimal.valueOf(40)) > 0) {
                    qty = BigDecimal.valueOf(25.0);
                }
            }
        }
        // 5. 밥류 (쌀밥, 잡곡밥 등 생쌀 투입량 75~85g)
        else if (isRice && !name.contains("볶음") && !name.contains("덮밥") && !name.contains("비빔")) {
            if (ingName.contains("쌀") || ingName.contains("현미") || ingName.contains("보리") || ingName.contains("잡곡") || ingName.contains("콩")) {
                if (qty.compareTo(BigDecimal.valueOf(90)) > 0) {
                    qty = BigDecimal.valueOf(80.0);
                }
            }
        }
        // 6. 국/찌개류 (육수용 고기/두부/채소)
        else if (isSoup) {
            if (ingName.contains("육") || ingName.contains("고기") || ingName.contains("닭") || ingName.contains("해물")) {
                if (qty.compareTo(BigDecimal.valueOf(50)) > 0) {
                    qty = BigDecimal.valueOf(30.0);
                }
            } else if (ingName.contains("두부")) {
                if (qty.compareTo(BigDecimal.valueOf(45)) > 0) {
                    qty = BigDecimal.valueOf(30.0);
                }
            }
        }

        // 7. 공통: 조미료/향신료 (소금, 후추, 참기름 등) 과다 투입량 정제
        if (ingName.contains("소금") || ingName.contains("후추") || ingName.contains("고춧가루")) {
            if (qty.compareTo(BigDecimal.valueOf(4)) > 0) {
                qty = BigDecimal.valueOf(2.0);
            }
        } else if (ingName.contains("참기름") || ingName.contains("들기름") || ingName.contains("식용유") || ingName.contains("버터")) {
            if (qty.compareTo(BigDecimal.valueOf(8)) > 0) {
                qty = BigDecimal.valueOf(3.0);
            }
        } else if (ingName.contains("간장") || ingName.contains("된장") || ingName.contains("고추장") || ingName.contains("케첩") || ingName.contains("마요네즈")) {
            if (qty.compareTo(BigDecimal.valueOf(15)) > 0) {
                qty = BigDecimal.valueOf(8.0);
            }
        }

        // 8. 1인분 기준 120g 이상으로 과도하게 적재된 단일 재료 상한 제한
        if (qty.compareTo(BigDecimal.valueOf(120)) > 0) {
            qty = BigDecimal.valueOf(90.0);
        }

        return new MenuIngredientCostVo(
                item.getIngredientId(),
                item.getIngredientName(),
                item.getIngredientCategory(),
                item.getIsPrimary(),
                qty,
                unitPrice,
                item.getPriceDate(),
                item.getPriceSource(),
                item.getMappingType(),
                item.getConfidenceScore()
        );
    }

    private boolean isNoodleMenu(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("국수") || full.contains("메밀") || full.contains("모밀") || full.contains("소바")
                || full.contains("우동") || full.contains("냉면") || full.contains("쫄면") || full.contains("칼국수")
                || full.contains("스파게티") || full.contains("파스타") || full.contains("짜장") || full.contains("짬뽕")
                || full.contains("라면") || full.contains("라멘") || full.contains("쌀국수") || full.contains("마라탕")
                || full.contains("잡채") || full.contains("비빔면") || full.contains("누들");
    }

    private boolean isMainMenu(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("불고기") || full.contains("제육") || full.contains("돈까스") || full.contains("돈가스")
                || full.contains("갈비") || full.contains("닭") || full.contains("치킨") || full.contains("삼겹")
                || full.contains("생선") || full.contains("스테이크") || full.contains("함박") || full.contains("탕수육")
                || full.contains("까스") || full.contains("가스") || full.contains("조림") || full.contains("구이")
                || full.contains("찜") || full.contains("볶음") || full.contains("폭립") || full.contains("너비아니");
    }

    private boolean isSnackOrSkewerMenu(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("꼬치") || full.contains("소떡") || full.contains("떡볶이") || full.contains("라볶이")
                || full.contains("어묵") || full.contains("오뎅") || full.contains("핫도그") || full.contains("순대")
                || full.contains("튀김") || full.contains("만두") || full.contains("전") || full.contains("부침")
                || full.contains("너겟") || full.contains("핑거");
    }

    private boolean isDessertOrSnack(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("설기") || full.contains("떡") || full.contains("경단") || full.contains("송편")
                || full.contains("약과") || full.contains("약식") || full.contains("만주") || full.contains("유과")
                || full.contains("한과") || full.contains("과일") || full.contains("사과") || full.contains("배")
                || full.contains("바나나") || full.contains("오렌지") || full.contains("귤") || full.contains("포도")
                || full.contains("수박") || full.contains("참외") || full.contains("딸기") || full.contains("키위")
                || full.contains("토마토") || full.contains("디저트") || full.contains("후식") || full.contains("빵")
                || full.contains("케이크") || full.contains("케익") || full.contains("쿠키") || full.contains("머핀")
                || full.contains("와플") || full.contains("도넛") || full.contains("파이") || full.contains("토스트")
                || full.contains("카스테라") || full.contains("푸딩") || full.contains("젤리") || full.contains("요거트")
                || full.contains("요구르트") || full.contains("아이스크림") || full.contains("주스") || full.contains("음료")
                || full.contains("식혜") || full.contains("수정과") || full.contains("에이드") || full.contains("차")
                || full.contains("우유") || full.contains("두유");
    }

    private boolean isRiceMenu(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("밥") || full.contains("죽") || full.contains("라이스");
    }

    private boolean isSoupMenu(String name, String category) {
        String full = (name + " " + category).toLowerCase();
        return full.contains("국") || full.contains("찌개") || full.contains("탕") || full.contains("스프") || full.contains("전골");
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
        for (Map.Entry<Long, List<MenuIngredientCostVo>> entry : data.getMenuIngredientsMap().entrySet()) {
            Long menuId = entry.getKey();
            String mName = data.getMenuMap().getOrDefault(menuId, "");
            List<MenuIngredientCostVo> sanitized = entry.getValue().stream()
                    .map(vo -> sanitizeIngredientCost(mName, "", vo))
                    .collect(Collectors.toList());
            menuIngredientsMap.put(menuId, sanitized);
        }

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
                    item.getIngredientCategory(),
                    item.getIsPrimary(),
                    item.getQuantity(),
                    finalUnitPrice,
                    finalPriceDate,
                    item.getPriceSource(),
                    item.getMappingType(),
                    item.getConfidenceScore()
            ));
        }

        dbPredictedIngredientsCache.put(cacheKey, predictedList);
        return predictedList;
    }

    /**
     * [시설 & 월 예산 DB 연동]
     * - 1순위: DB 예산 캐시 (mealfit.budget / mealfit.monthly_budget)
     * - 2순위: CSV 예산 데이터 (monthly_budget.csv Fallback)
     * - 3순위: 시설 DB 기본 정보 기반 기본 예산 반환
     */
    @Override
    public Optional<FacilityBudgetVo> findFacilityBudget(Long facilityId, YearMonth month) {
        String key = facilityId + ":" + month;

        if (jdbcTemplate != null && facilityId != null && month != null) {
            try {
                List<FacilityBudgetVo> currentDatabaseBudget = jdbcTemplate.query(
                        "SELECT mb.facility_id, mb.budget_month, mb.budget_amount, f.name "
                                + "FROM mealfit.monthly_budget mb JOIN mealfit.facility f ON f.facility_id = mb.facility_id "
                                + "WHERE mb.facility_id = ? AND LEFT(CAST(mb.budget_month AS VARCHAR), 7) = ? "
                                + "ORDER BY mb.budget_month DESC LIMIT 1",
                        (resultSet, rowNumber) -> new FacilityBudgetVo(
                                resultSet.getLong("facility_id"),
                                resultSet.getString("name"),
                                YearMonth.parse(resultSet.getString("budget_month").substring(0, 7)),
                                resultSet.getBigDecimal("budget_amount")),
                        facilityId,
                        month.toString());
                if (!currentDatabaseBudget.isEmpty()) {
                    return Optional.of(currentDatabaseBudget.get(0));
                }
            } catch (Exception e) {
                log.debug(" >> [MemoryCostRepository] 월 예산 실시간 조회 실패, 캐시를 사용합니다: {}", e.getMessage());
            }
        }

        // 1순위: DB 예산 캐시
        FacilityBudgetVo dbBudget = dbBudgetMap.get(key);
        if (dbBudget != null) {
            return Optional.of(dbBudget);
        }

        FacilityBudgetVo csvBudget = budgetMap.get(key);

        String facilityName = dbFacilityNameMap.get(facilityId);
        if (facilityName == null && facilityRepository != null && facilityId != null) {
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
     * [식단 DB 연동 + 단가 DB 연동]
     * - 1순위: DB 식단 편성 (mealfit.meal_plan 실시간 조회)
     * - 2순위: 인메모리/CSV 식단 데이터 (meal_plan.csv Fallback)
     * - 식단 내 메뉴별 1인분 원가는 DB의 최신/예측 단가를 실시간 연동하여 계산
     */
    @Override
    public List<MealPlanCostVo> findMealPlansByFacilityAndDateRange(Long facilityId, LocalDate startDate, LocalDate endDate) {
        if (jdbcTemplate != null && facilityId != null && startDate != null && endDate != null) {
            try {
                String sql = """
                    SELECT mp.plan_id, mp.facility_id, mp.plan_date, mp.meal_type, mp.meal_count
                    FROM mealfit.meal_plan mp
                    WHERE mp.facility_id = ? AND mp.plan_date BETWEEN ? AND ?
                    ORDER BY mp.plan_date, mp.meal_type, mp.plan_id
                    """;
                List<RawMealPlanInfo> dbPlans = jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> {
                            long pId = rs.getLong("plan_id");
                            long fId = rs.getLong("facility_id");
                            Date pDate = rs.getDate("plan_date");
                            LocalDate planDate = (pDate != null) ? pDate.toLocalDate() : LocalDate.now();
                            String mType = rs.getString("meal_type");
                            int mCount = rs.getInt("meal_count");
                            return new RawMealPlanInfo(pId, fId, planDate, mType, mCount, BigDecimal.valueOf(3500));
                        },
                        facilityId,
                        Date.valueOf(startDate),
                        Date.valueOf(endDate)
                );

                if (!dbPlans.isEmpty()) {
                    return dbPlans.stream()
                            .map(this::calculateRealtimeMealPlanCost)
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                log.warn(">> [MemoryCostRepository] DB 식단 실시간 조회 실패, 캐시/Fallback 사용: {}", e.getMessage());
            }
        }

        List<RawMealPlanInfo> sourceList = !dbMealPlanList.isEmpty() ? dbMealPlanList : rawMealPlanList;
        return sourceList.stream()
                .filter(p -> Objects.equals(p.getFacilityId(), facilityId))
                .filter(p -> (p.getPlanDate().isEqual(startDate) || p.getPlanDate().isAfter(startDate)) &&
                             (p.getPlanDate().isEqual(endDate) || p.getPlanDate().isBefore(endDate)))
                .sorted(Comparator.comparing(RawMealPlanInfo::getPlanDate))
                .map(this::calculateRealtimeMealPlanCost)
                .collect(Collectors.toList());
    }

    /**
     * [식단 메뉴 편성 DB 연동] 식단 계획(Plan ID)에 포함된 메뉴 ID 목록 조회
     * - 1순위: DB 식단 메뉴 (mealfit.meal_plan_item 실시간 조회)
     * - 2순위: CSV 식단 메뉴 (meal_plan_item.csv Fallback)
     */
    @Override
    public List<Long> findMenuIdsByPlanId(Long planId) {
        if (planId == null) {
            return Collections.emptyList();
        }
        if (jdbcTemplate != null) {
            try {
                String sql = "SELECT menu_id FROM mealfit.meal_plan_item WHERE plan_id = ? ORDER BY display_order, item_id";
                List<Long> dbItems = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("menu_id"), planId);
                if (!dbItems.isEmpty()) {
                    return dbItems;
                }
            } catch (Exception e) {
                log.debug(">> [MemoryCostRepository] DB meal_plan_item 실시간 조회 실패: {}", e.getMessage());
            }
        }
        List<Long> dbItems = dbPlanMenuItemsMap.get(planId);
        if (dbItems != null && !dbItems.isEmpty()) {
            return dbItems;
        }
        return planMenuItemsMap.getOrDefault(planId, Collections.emptyList());
    }

    /**
     * 식단에 포함된 메뉴들의 식재료 예측가/최신 단가를 결합하여 1인분 원가 산출
     * (DB에 단가가 있으면 DB 우선 반영)
     */
    private MealPlanCostVo calculateRealtimeMealPlanCost(RawMealPlanInfo raw) {
        List<Long> menuIds = findMenuIdsByPlanId(raw.getPlanId());
        if (menuIds.isEmpty()) {
            return new MealPlanCostVo(raw.getPlanId(), raw.getFacilityId(), raw.getPlanDate(), raw.getMealType(), raw.getMealCount(), raw.getFallbackCost());
        }

        BigDecimal calculatedCostPerPerson = BigDecimal.ZERO;

        for (Long menuId : menuIds) {
            // DB 예측 단가 우선 조회
            List<MenuIngredientCostVo> ingredients = findPredictedIngredientsByMenuId(menuId, raw.getPlanDate());
            if (ingredients.isEmpty()) {
                ingredients = findLatestIngredientsByMenuId(menuId);
            }
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

