package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostComparisonResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.repository.CostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.human.backend.cost.util.CostCalculationUtils.*;

/**
 * [메뉴 원가 비교 분석 및 식재료 가격 위험 진단 전담 서비스]
 *
 * ■ 담당 요구사항:
 *   - COST-005: 현재 메뉴 원가와 미래 예상 원가를 비교하여 차액과 상승률을 표시한다.
 *   - COST-006: 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver)를 식별한다.
 *   - MENU-009: 메뉴 구성 주요 식재료의 가격 위험을 종합해 메뉴 위험도를 표시한다.
 *
 * ■ 주요 분석 흐름 (Analysis Flow):
 *   1. [COST-005 원가 변동 비교]
 *      - 현재 원가(current) & 미래 원가(future) 동시 계산
 *      - 1인분 차액(차액 = 미래 - 현재) 및 상승률(%) 산출
 *      - 개별 식재료별 단가 및 재료비 변동 내역(IngredientCostComparison) 매핑
 *
 *   2. [COST-006 Cost Driver 식별 & 랭킹]
 *      - 식재료별 재료비 상승액(lineCostDiff) 기준 내림차순 정렬 및 순위 부여
 *      - 전체 원가 상승액 대비 기여율(contributionRate = (lineCostDiff / totalDiff) * 100) 산출
 *      - 상승 기여도 1위 식재료(Top Driver) 도출
 *
 *   3. [MENU-009 식재료 가격 위험 종합 진단]
 *      - 개별 식재료 위험도 판정:
 *          * WARNING : 단가 상승률 >= 20% 또는 (상승률 >= 10% & 기여율 >= 30%)
 *          * CAUTION : 단가 상승률 >= 8%
 *          * SAFE    : 그 외 안정세
 *      - 메뉴 종합 위험도(riskLevel, riskScore, riskSummary) 도출:
 *          * WARNING (위험) : 경고 식재료 존재 또는 총 원가 상승률 >= 15%
 *          * CAUTION (주의) : 주의 식재료 존재 또는 총 원가 상승률 >= 7%
 *          * SAFE (안정)    : 원가 변동 안정 범위
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MenuRiskService {

    private final MenuCostService menuCostService;
    private final CostRepository costRepository;

    // =========================================================================
    // 1. [COST-005] 현재 원가 vs 미래 예상 원가 비교 및 상승률 분석
    // =========================================================================

    /**
     * 현재 메뉴 원가와 미래 예상 원가를 비교하여 차액과 상승률을 계산한다. (단건, COST-005)
     */
    public MenuCostComparisonResponse compareMenuCost(Long menuId, LocalDate targetDate, Integer mealCount) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        int validMealCount = resolveMealCount(mealCount);

        // 1. 현재 원가 및 미래 예상 원가 계산 위임
        MenuCostResponse current = menuCostService.calculateCurrentMenuCost(menuId, validMealCount, null);
        MenuCostResponse future = menuCostService.calculateFutureMenuCost(menuId, validTargetDate, validMealCount, null);

        // 2. 1인분 기준 차액 및 상승률 계산
        BigDecimal currentPerPerson = current.getCostPerPerson();
        BigDecimal futurePerPerson = future.getCostPerPerson();
        BigDecimal costDifference = futurePerPerson.subtract(currentPerPerson);
        BigDecimal increaseRate = calculateIncreaseRate(currentPerPerson, futurePerPerson);
        boolean isIncreased = costDifference.compareTo(BigDecimal.ZERO) > 0;

        // 3. 총 식수 기준 차액
        BigDecimal currentTotal = current.getTotalMealCost();
        BigDecimal futureTotal = future.getTotalMealCost();
        BigDecimal totalCostDifference = futureTotal.subtract(currentTotal);

        // 4. 개별 식재료별 단가 및 원가 변동 비교 매핑
        List<MenuCostComparisonResponse.IngredientCostComparison> ingredientComparisons = new ArrayList<>();
        Map<Long, MenuCostResponse.IngredientDetail> futureDetailMap = future.getDetails().stream()
                .collect(Collectors.toMap(MenuCostResponse.IngredientDetail::getIngredientId, d -> d, (a, b) -> a));

        for (MenuCostResponse.IngredientDetail curDetail : current.getDetails()) {
            Long ingId = curDetail.getIngredientId();
            MenuCostResponse.IngredientDetail futDetail = futureDetailMap.get(ingId);

            BigDecimal curUnitPrice = curDetail.getStandardUnitPrice();
            BigDecimal futUnitPrice = (futDetail != null) ? futDetail.getStandardUnitPrice() : curUnitPrice;
            BigDecimal futLineCost = (futDetail != null) ? futDetail.getLineCost() : curDetail.getLineCost();
            LocalDate futPriceDate = (futDetail != null) ? futDetail.getPriceDate() : validTargetDate;

            BigDecimal lineDiff = futLineCost.subtract(curDetail.getLineCost());
            BigDecimal unitRate = calculateIncreaseRate(curUnitPrice, futUnitPrice);

            ingredientComparisons.add(MenuCostComparisonResponse.IngredientCostComparison.builder()
                    .ingredientId(ingId)
                    .ingredientName(curDetail.getIngredientName())
                    .quantity(curDetail.getQuantity())
                    .currentUnitPrice(curUnitPrice)
                    .futureUnitPrice(futUnitPrice)
                    .currentLineCost(curDetail.getLineCost())
                    .futureLineCost(futLineCost)
                    .lineCostDifference(lineDiff)
                    .unitPriceIncreaseRate(unitRate)
                    .currentPriceDate(curDetail.getPriceDate())
                    .futurePriceDate(futPriceDate)
                    .build());
        }

        log.info("==================================================");
        log.info(">> [원가 비교 분석] 메뉴: {} (ID: {}) | 기준일: {}", current.getMenuName(), menuId, validTargetDate);
        log.info(">> 현재 1인분: {}원 -> 미래 1인분: {}원 | 차액: {}원 (상승률: {}%)",
                currentPerPerson, futurePerPerson, costDifference, increaseRate);
        log.info("--------------------------------------------------");

        return MenuCostComparisonResponse.builder()
                .menuId(menuId)
                .menuName(current.getMenuName())
                .targetDate(validTargetDate)
                .currentCostPerPerson(currentPerPerson)
                .futureCostPerPerson(futurePerPerson)
                .costDifference(costDifference)
                .increaseRate(increaseRate)
                .isIncreased(isIncreased)
                .mealCount(validMealCount)
                .currentTotalMealCost(currentTotal)
                .futureTotalMealCost(futureTotal)
                .totalCostDifference(totalCostDifference)
                .ingredientComparisons(ingredientComparisons)
                .build();
    }

    /**
     * 모든 메뉴 대상 현재 원가와 미래 예상 원가 비교 일괄 목록 산출 (COST-005)
     */
    public List<MenuCostComparisonResponse> compareAllMenuCosts(LocalDate targetDate, Integer mealCount) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        return costRepository.findAllMenuIds().stream()
                .map(menuId -> {
                    try {
                        return compareMenuCost(menuId, validTargetDate, mealCount);
                    } catch (Exception e) {
                        log.warn(">> [원가 비교 일괄 분석] 메뉴 ID={} 분석 생략: {}", menuId, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // =========================================================================
    // 2. [COST-006] 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 식별
    // =========================================================================

    /**
     * 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 식별 및 랭킹 분석 (단건, COST-006)
     */
    public CostDriverResponse identifyCostDrivers(Long menuId, LocalDate targetDate) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);

        MenuCostResponse current = menuCostService.calculateCurrentMenuCost(menuId, 1, null);
        MenuCostResponse future = menuCostService.calculateFutureMenuCost(menuId, validTargetDate, 1, null);

        BigDecimal currentPerPerson = current.getCostPerPerson();
        BigDecimal futurePerPerson = future.getCostPerPerson();
        BigDecimal totalCostDifference = futurePerPerson.subtract(currentPerPerson);
        BigDecimal totalIncreaseRate = calculateIncreaseRate(currentPerPerson, futurePerPerson);

        Map<Long, MenuCostResponse.IngredientDetail> futureDetailMap = future.getDetails().stream()
                .collect(Collectors.toMap(MenuCostResponse.IngredientDetail::getIngredientId, d -> d, (a, b) -> a));

        List<CostDriverResponse.IngredientDriver> rawDrivers = new ArrayList<>();

        for (MenuCostResponse.IngredientDetail curDetail : current.getDetails()) {
            Long ingId = curDetail.getIngredientId();
            MenuCostResponse.IngredientDetail futDetail = futureDetailMap.get(ingId);

            BigDecimal curUnitPrice = curDetail.getStandardUnitPrice();
            BigDecimal futUnitPrice = (futDetail != null) ? futDetail.getStandardUnitPrice() : curUnitPrice;
            BigDecimal unitPriceDiff = futUnitPrice.subtract(curUnitPrice);
            BigDecimal unitRate = calculateIncreaseRate(curUnitPrice, futUnitPrice);

            BigDecimal curLineCost = curDetail.getLineCost();
            BigDecimal futLineCost = (futDetail != null) ? futDetail.getLineCost() : curLineCost;
            BigDecimal lineCostDiff = futLineCost.subtract(curLineCost);
            boolean isCostIncrease = lineCostDiff.compareTo(BigDecimal.ZERO) > 0;

            // 전체 메뉴 상승액 대비 기여율 (%) 계산
            BigDecimal contributionRate = (totalCostDifference.compareTo(BigDecimal.ZERO) > 0 && isCostIncrease)
                    ? calculatePercentage(lineCostDiff, totalCostDifference)
                    : BigDecimal.ZERO;

            rawDrivers.add(CostDriverResponse.IngredientDriver.builder()
                    .ingredientId(ingId)
                    .ingredientName(curDetail.getIngredientName())
                    .quantity(curDetail.getQuantity())
                    .currentUnitPrice(curUnitPrice)
                    .futureUnitPrice(futUnitPrice)
                    .unitPriceDifference(unitPriceDiff)
                    .unitPriceIncreaseRate(unitRate)
                    .currentLineCost(curLineCost)
                    .futureLineCost(futLineCost)
                    .lineCostDifference(lineCostDiff)
                    .contributionRate(contributionRate)
                    .isCostIncrease(isCostIncrease)
                    .build());
        }

        // 원가 상승액 내림차순 정렬 및 순위 부여
        rawDrivers.sort((a, b) -> b.getLineCostDifference().compareTo(a.getLineCostDifference()));

        List<CostDriverResponse.IngredientDriver> rankedDrivers = new ArrayList<>();
        int rank = 1;
        for (CostDriverResponse.IngredientDriver driver : rawDrivers) {
            rankedDrivers.add(CostDriverResponse.IngredientDriver.builder()
                    .rank(rank++)
                    .ingredientId(driver.getIngredientId())
                    .ingredientName(driver.getIngredientName())
                    .quantity(driver.getQuantity())
                    .currentUnitPrice(driver.getCurrentUnitPrice())
                    .futureUnitPrice(driver.getFutureUnitPrice())
                    .unitPriceDifference(driver.getUnitPriceDifference())
                    .unitPriceIncreaseRate(driver.getUnitPriceIncreaseRate())
                    .currentLineCost(driver.getCurrentLineCost())
                    .futureLineCost(driver.getFutureLineCost())
                    .lineCostDifference(driver.getLineCostDifference())
                    .contributionRate(driver.getContributionRate())
                    .isCostIncrease(driver.isCostIncrease())
                    .build());
        }

        CostDriverResponse.IngredientDriver topDriver = rankedDrivers.isEmpty() ? null : rankedDrivers.get(0);

        log.info("==================================================");
        log.info(">> [Cost Driver 분석] 메뉴: {} (ID: {}) | 기준일: {}", current.getMenuName(), menuId, validTargetDate);
        if (topDriver != null) {
            log.info(">> 🔥 [최대 상승 기여 식재료 (Top 1)]: [{}] (상승액: {}원, 기여율: {}%)",
                    topDriver.getIngredientName(), topDriver.getLineCostDifference(), topDriver.getContributionRate());
        }
        log.info("--------------------------------------------------");

        return CostDriverResponse.builder()
                .menuId(menuId)
                .menuName(current.getMenuName())
                .targetDate(validTargetDate)
                .currentCostPerPerson(currentPerPerson)
                .futureCostPerPerson(futurePerPerson)
                .totalCostDifference(totalCostDifference)
                .totalIncreaseRate(totalIncreaseRate)
                .topDriver(topDriver)
                .rankedDrivers(rankedDrivers)
                .build();
    }

    /**
     * 모든 메뉴 대상 원가 상승 기여 식재료 식별 일괄 목록 산출 (COST-006)
     */
    public List<CostDriverResponse> identifyAllCostDrivers(LocalDate targetDate) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        return costRepository.findAllMenuIds().stream()
                .map(menuId -> {
                    try {
                        return identifyCostDrivers(menuId, validTargetDate);
                    } catch (Exception e) {
                        log.warn(">> [Cost Driver 일괄 분석] 메뉴 ID={} 분석 생략: {}", menuId, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // =========================================================================
    // 3. [MENU-009] 메뉴 구성 주요 식재료의 가격 위험을 종합해 메뉴 위험도 산출
    // =========================================================================

    /**
     * 메뉴 구성 주요 식재료의 가격 변동 위험을 종합하여 메뉴의 종합 위험도를 판정한다. (단건, MENU-009)
     */
    public MenuRiskResponse evaluateMenuRisk(Long menuId, LocalDate targetDate) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);

        // 1. 현재 원가 및 미래 예상 원가 산출
        MenuCostResponse current = menuCostService.calculateCurrentMenuCost(menuId, 1, null);
        MenuCostResponse future = menuCostService.calculateFutureMenuCost(menuId, validTargetDate, 1, null);

        BigDecimal currentPerPerson = current.getCostPerPerson();
        BigDecimal futurePerPerson = future.getCostPerPerson();
        BigDecimal costDifference = futurePerPerson.subtract(currentPerPerson);
        BigDecimal increaseRate = calculateIncreaseRate(currentPerPerson, futurePerPerson);

        // 2. 미래 식재료 매핑
        Map<Long, MenuCostResponse.IngredientDetail> futureDetailMap = future.getDetails().stream()
                .collect(Collectors.toMap(MenuCostResponse.IngredientDetail::getIngredientId, d -> d, (a, b) -> a));

        List<MenuRiskResponse.IngredientRiskDetail> riskIngredients = new ArrayList<>();
        int warningCount = 0;
        int cautionCount = 0;
        String highestRiskIngredientName = null;
        BigDecimal maxIncreaseRate = BigDecimal.ZERO;

        for (MenuCostResponse.IngredientDetail curDetail : current.getDetails()) {
            Long ingId = curDetail.getIngredientId();
            MenuCostResponse.IngredientDetail futDetail = futureDetailMap.get(ingId);

            BigDecimal curUnitPrice = curDetail.getStandardUnitPrice();
            BigDecimal futUnitPrice = (futDetail != null) ? futDetail.getStandardUnitPrice() : curUnitPrice;
            BigDecimal unitRate = calculateIncreaseRate(curUnitPrice, futUnitPrice);

            BigDecimal curLineCost = curDetail.getLineCost();
            BigDecimal futLineCost = (futDetail != null) ? futDetail.getLineCost() : curLineCost;
            BigDecimal lineCostDiff = futLineCost.subtract(curLineCost);

            // 기여율 계산
            BigDecimal contributionRate = (costDifference.compareTo(BigDecimal.ZERO) > 0 && lineCostDiff.compareTo(BigDecimal.ZERO) > 0)
                    ? calculatePercentage(lineCostDiff, costDifference)
                    : BigDecimal.ZERO;

            // 식재료별 위험도 판정:
            // WARNING: 단가 상승률 20% 이상 또는 (상승률 10% 이상 & 기여율 30% 이상)
            // CAUTION: 단가 상승률 8% 이상
            // SAFE: 그 외
            String ingRiskLevel = "SAFE";
            String reason = "가격 변동 안정";

            if (unitRate.compareTo(BigDecimal.valueOf(20)) >= 0 ||
                    (unitRate.compareTo(BigDecimal.valueOf(10)) >= 0 && contributionRate.compareTo(BigDecimal.valueOf(30)) >= 0)) {
                ingRiskLevel = "WARNING";
                reason = "단가 " + unitRate + "% 급등 (원가 기여도 " + contributionRate + "%)";
                warningCount++;
            } else if (unitRate.compareTo(BigDecimal.valueOf(8)) >= 0) {
                ingRiskLevel = "CAUTION";
                reason = "단가 " + unitRate + "% 상승 (주의 요망)";
                cautionCount++;
            }

            if (unitRate.compareTo(maxIncreaseRate) > 0) {
                maxIncreaseRate = unitRate;
                highestRiskIngredientName = curDetail.getIngredientName();
            }

            riskIngredients.add(MenuRiskResponse.IngredientRiskDetail.builder()
                    .ingredientId(ingId)
                    .ingredientName(curDetail.getIngredientName())
                    .quantity(curDetail.getQuantity())
                    .currentUnitPrice(curUnitPrice)
                    .futureUnitPrice(futUnitPrice)
                    .unitPriceIncreaseRate(unitRate)
                    .lineCostDifference(lineCostDiff)
                    .contributionRate(contributionRate)
                    .ingredientRiskLevel(ingRiskLevel)
                    .riskReason(reason)
                    .build());
        }

        // 3. 식재료 변동률/기여도 순 정렬
        riskIngredients.sort((a, b) -> b.getLineCostDifference().compareTo(a.getLineCostDifference()));

        // 4. 메뉴 종합 위험도(riskLevel, riskScore, riskSummary) 종합 판정
        String riskLevel;
        boolean isRisk;
        int riskScore;
        String riskSummary;

        if (warningCount > 0 || increaseRate.compareTo(BigDecimal.valueOf(15)) >= 0) {
            riskLevel = "WARNING";
            isRisk = true;
            riskScore = Math.min(100, 75 + warningCount * 5 + increaseRate.intValue());
            riskSummary = String.format("주요 식재료인 '%s' 등 %d개 품목의 급등으로 메뉴 원가 경고(WARNING) 단계입니다. (총 원가 %s%% 상승)",
                    highestRiskIngredientName != null ? highestRiskIngredientName : "식재료",
                    warningCount, increaseRate);
        } else if (cautionCount > 0 || increaseRate.compareTo(BigDecimal.valueOf(7)) >= 0) {
            riskLevel = "CAUTION";
            isRisk = true;
            riskScore = Math.min(74, 40 + cautionCount * 5 + increaseRate.intValue());
            riskSummary = String.format("주요 식재료인 '%s'의 가격 인상으로 원가 주의(CAUTION) 단계입니다. (총 원가 %s%% 상승)",
                    highestRiskIngredientName != null ? highestRiskIngredientName : "식재료",
                    increaseRate);
        } else {
            riskLevel = "SAFE";
            isRisk = false;
            riskScore = Math.max(0, Math.min(39, 10 + increaseRate.intValue()));
            riskSummary = "메뉴 구성 식재료의 가격 변동이 안정적이며 위험도가 낮습니다.";
        }

        log.info("==================================================");
        log.info(">> [MENU-009 메뉴 위험도 종합 진단] 메뉴: {} (ID: {}) | 기준일: {}", current.getMenuName(), menuId, validTargetDate);
        log.info(">> 종합 위험등급: {} (점수: {}점, 위험여부: {})", riskLevel, riskScore, isRisk);
        log.info("--------------------------------------------------");

        return MenuRiskResponse.builder()
                .menuId(menuId)
                .menuName(current.getMenuName())
                .targetDate(validTargetDate)
                .currentCostPerPerson(currentPerPerson)
                .futureCostPerPerson(futurePerPerson)
                .costDifference(costDifference)
                .increaseRate(increaseRate)
                .riskLevel(riskLevel)
                .isRisk(isRisk)
                .riskScore(riskScore)
                .riskSummary(riskSummary)
                .riskIngredients(riskIngredients)
                .build();
    }

    /**
     * 모든 메뉴 대상 주요 식재료 가격 위험 종합 및 메뉴 위험도 일괄 산출 (MENU-009)
     */
    public List<MenuRiskResponse> evaluateAllMenuRisks(LocalDate targetDate) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        return costRepository.findAllMenuIds().stream()
                .map(menuId -> {
                    try {
                        return evaluateMenuRisk(menuId, validTargetDate);
                    } catch (Exception e) {
                        log.warn(">> [메뉴 위험도 일괄 분석] 메뉴 ID={} 분석 생략: {}", menuId, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
