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
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * 인메모리 및 DB 연동 기반의 CostRepository 구현체
 * 마스터 데이터(메뉴, 시설)는 실제 DB 저장소를 우선 조회하며,
 * 식재료 상세 및 예측 가격은 CostCsvDataLoader가 파싱한 캐시 데이터를 활용하여 서빙합니다.
 */
@Slf4j
@Repository
public class MemoryCostRepository implements CostRepository {

    @Value("${mock.csv.path:src/main/java/com/human/backend/cost/dummy}")
    private String mockCsvPath = "src/main/java/com/human/backend/cost/dummy";

    private final CostCsvDataLoader csvDataLoader;

    @Autowired(required = false)
    private MenuRepository menuRepository;

    @Autowired(required = false)
    private FacilityRepository facilityRepository;

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
    }

    @Autowired
    public MemoryCostRepository(CostCsvDataLoader csvDataLoader) {
        this.csvDataLoader = csvDataLoader != null ? csvDataLoader : new CostCsvDataLoader();
    }

    public MemoryCostRepository(String mockCsvPath) {
        this.mockCsvPath = mockCsvPath;
        this.csvDataLoader = new CostCsvDataLoader();
    }

    public MemoryCostRepository(String mockCsvPath, MenuRepository menuRepository, FacilityRepository facilityRepository) {
        this.mockCsvPath = mockCsvPath;
        this.menuRepository = menuRepository;
        this.facilityRepository = facilityRepository;
        this.csvDataLoader = new CostCsvDataLoader();
    }

    /**
     * 스프링 빈 초기화 시 CSV 파일들을 로드합니다.
     */
    @PostConstruct
    public void init() {
        loadDataFromCsv();
    }

    /**
     * CSV 데이터를 읽어와 메모리 맵을 초기화합니다.
     */
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

    @Override
    public List<Long> findAllMenuIds() {
        List<Long> ids = new ArrayList<>(menuIngredientsMap.keySet());
        ids.sort(Long::compareTo);
        return ids;
    }

    @Override
    public Optional<String> findMenuNameById(Long menuId) {
        if (menuRepository != null && menuId != null) {
            try {
                List<MenuResponse> menus = menuRepository.getMenus();
                if (menus != null) {
                    Optional<String> dbMenuName = menus.stream()
                            .filter(m -> Objects.equals(m.getMenuId(), menuId))
                            .map(MenuResponse::getMenuName)
                            .findFirst();
                    if (dbMenuName.isPresent()) {
                        return dbMenuName;
                    }
                }
            } catch (Exception e) {
                log.warn(">> [MemoryCostRepository] DB 메뉴명 조회 실패, CSV 데이터를 사용합니다: {}", e.getMessage());
            }
        }
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
                PriceInfo priceInfo = latestPriceMap.get(ingredientId);
                finalUnitPrice = (priceInfo != null) ? priceInfo.getStandardUnitPrice() : item.getStandardUnitPrice();
                finalPriceDate = (priceInfo != null) ? priceInfo.getPriceDate() : item.getPriceDate();
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
     * 식단에 포함된 메뉴들의 식재료 예측가/최신 단가를 실시간 곱연산하여 1인분 원가 산출
     */
    private MealPlanCostVo calculateRealtimeMealPlanCost(RawMealPlanInfo raw) {
        List<Long> menuIds = planMenuItemsMap.getOrDefault(raw.getPlanId(), Collections.emptyList());
        if (menuIds.isEmpty()) {
            return new MealPlanCostVo(raw.getPlanId(), raw.getFacilityId(), raw.getPlanDate(), raw.getMealType(), raw.getMealCount(), raw.getFallbackCost());
        }

        BigDecimal calculatedCostPerPerson = BigDecimal.ZERO;

        for (Long menuId : menuIds) {
            List<MenuIngredientCostVo> ingredients = menuIngredientsMap.getOrDefault(menuId, Collections.emptyList());
            for (MenuIngredientCostVo ingredient : ingredients) {
                Long ingredientId = ingredient.getIngredientId();
                BigDecimal quantity = ingredient.getQuantity();

                String predKey = raw.getPlanDate() + ":" + ingredientId;
                BigDecimal unitPrice = predictedPriceMap.get(predKey);

                if (unitPrice == null) {
                    PriceInfo priceInfo = latestPriceMap.get(ingredientId);
                    unitPrice = (priceInfo != null) ? priceInfo.getStandardUnitPrice() : ingredient.getStandardUnitPrice();
                }

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
