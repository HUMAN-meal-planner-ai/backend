package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostComparisonResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.human.backend.cost.util.CostCalculationUtils.*;

/**
 * 메뉴 원가 계산 및 비교/분석 전담 서비스
 * 담당 요구사항:
 * - COST-001: 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다.
 * - COST-003: 메뉴별 1인분 기준 원가를 산출한다.
 * - COST-004: 1인분 원가와 식수 인원을 곱하여 총 예상 원가를 계산한다.
 * - COST-009: 시설 또는 식단의 목표 원가를 초과하는 메뉴를 탐지한다.
 * - COST-002: 미래 식재료 예측가격과 사용 중량으로 메뉴 미래 원가를 계산한다.
 * - COST-005: 현재 메뉴 원가와 미래 예상 원가를 비교하여 차액과 상승률을 표시한다.
 * - COST-006: 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver)를 식별한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuCostService {

    private final CostRepository costRepository;

    /**
     * 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다. (단건, COST-001, COST-004, COST-009)
     */
    public MenuCostResponse calculateCurrentMenuCost(Long menuId, Integer mealCount, BigDecimal targetCost) {
        int validMealCount = resolveMealCount(mealCount);
        BigDecimal validTargetCost = resolveTargetCost(targetCost);

        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND",
                        "존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        List<MenuIngredientCostVo> items = costRepository.findLatestIngredientsByMenuId(menuId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_INGREDIENT_NOT_FOUND",
                    "해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        log.info("==================================================");
        log.info(">> [메뉴 현재 원가 계산] 메뉴: {} (ID: {}) | 식재료 품목: {}개", menuName, menuId, items.size());
        log.info("--------------------------------------------------");

        BigDecimal costPerPerson = BigDecimal.ZERO;
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>();

        for (MenuIngredientCostVo item : items) {
            MenuCostResponse.IngredientDetail detail = MenuCostResponse.IngredientDetail.from(item);
            BigDecimal lineCost = detail.getLineCost();

            costPerPerson = costPerPerson.add(lineCost);
            detailList.add(detail);

            log.info("   - [{}] 사용량: {}g | 단가: {}원/g | 재료원가: {}원 (누적: {}원)",
                    item.getIngredientName(), item.getQuantity(), item.getStandardUnitPrice(), lineCost, costPerPerson);
        }

        BigDecimal totalMealCost = costPerPerson.multiply(BigDecimal.valueOf(validMealCount));

        boolean isExceeded = costPerPerson.compareTo(validTargetCost) > 0;
        BigDecimal exceededAmount = isExceeded
                ? costPerPerson.subtract(validTargetCost)
                : BigDecimal.ZERO;

        log.info(">> [총 원가 산출] {} | 1인분: {}원 * 식수: {}명 = 총 {}원 (목표초과: {} / 초과액: {}원)",
                menuName, costPerPerson, validMealCount, totalMealCost, isExceeded, exceededAmount);

        return MenuCostResponse.of(menuId, menuName, costPerPerson, validMealCount, totalMealCost,
                validTargetCost, isExceeded, exceededAmount, detailList);
    }

    /**
     * 등록된 모든 메뉴별 1인분 기준 현재 원가 일괄 산출 (COST-003)
     */
    public List<MenuCostResponse> calculateAllMenuCosts(Integer mealCount, BigDecimal targetCost) {
        List<Long> menuIds = costRepository.findAllMenuIds();
        List<MenuCostResponse> result = new ArrayList<>();

        for (Long menuId : menuIds) {
            result.add(calculateCurrentMenuCost(menuId, mealCount, targetCost));
        }

        return result;
    }

    /**
     * 미래 식재료 예측가격과 사용 중량으로 메뉴 미래 원가를 계산한다. (단건, COST-002, COST-004, COST-009)
     */
    public MenuCostResponse calculateFutureMenuCost(Long menuId, LocalDate targetDate, Integer mealCount,
                                                    BigDecimal targetCost) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        int validMealCount = resolveMealCount(mealCount);
        BigDecimal validTargetCost = resolveTargetCost(targetCost);

        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND",
                        "존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        List<MenuIngredientCostVo> items = costRepository.findPredictedIngredientsByMenuId(menuId, validTargetDate);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_INGREDIENT_NOT_FOUND",
                    "해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        log.info("==================================================");
        log.info(">> [메뉴 미래 원가 계산] 메뉴: {} (ID: {}) | 기준일: {}", menuName, menuId, validTargetDate);
        log.info(">> 식재료 품목 수: {}개", items.size());
        log.info("--------------------------------------------------");

        BigDecimal costPerPerson = BigDecimal.ZERO;
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>();

        for (MenuIngredientCostVo item : items) {
            MenuCostResponse.IngredientDetail detail = MenuCostResponse.IngredientDetail.from(item);
            BigDecimal lineCost = detail.getLineCost();

            costPerPerson = costPerPerson.add(lineCost);
            detailList.add(detail);

            log.info("   - [{}] 사용량: {}g | 예측단가: {}원/g (기준일: {}) | 재료원가: {}원 (누적: {}원)",
                    item.getIngredientName(), item.getQuantity(), item.getStandardUnitPrice(), item.getPriceDate(),
                    lineCost, costPerPerson);
        }

        BigDecimal totalMealCost = costPerPerson.multiply(BigDecimal.valueOf(validMealCount));

        boolean isExceeded = costPerPerson.compareTo(validTargetCost) > 0;
        BigDecimal exceededAmount = isExceeded
                ? costPerPerson.subtract(validTargetCost)
                : BigDecimal.ZERO;

        log.info(">> [미래 총 원가 산출] {} | 기준일: {} | 1인분: {}원 * 식수: {}명 = 총 {}원",
                menuName, validTargetDate, costPerPerson, validMealCount, totalMealCost);

        return MenuCostResponse.of(menuId, menuName, validTargetDate, costPerPerson, validMealCount, totalMealCost,
                validTargetCost, isExceeded, exceededAmount, detailList);
    }

    /**
     * 등록된 모든 메뉴별 특정 미래 일자 기준 원가 일괄 산출 (COST-002)
     */
    public List<MenuCostResponse> calculateAllFutureMenuCosts(LocalDate targetDate, Integer mealCount,
                                                              BigDecimal targetCost) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        List<Long> menuIds = costRepository.findAllMenuIds();
        List<MenuCostResponse> result = new ArrayList<>();

        for (Long menuId : menuIds) {
            result.add(calculateFutureMenuCost(menuId, validTargetDate, mealCount, targetCost));
        }

        return result;
    }

    /**
     * 현재 메뉴 원가와 미래 예상 원가를 비교하여 차액과 상승률을 계산한다. (COST-005)
     */
    public MenuCostComparisonResponse compareMenuCost(Long menuId, LocalDate targetDate, Integer mealCount) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        int validMealCount = resolveMealCount(mealCount);

        // 1. 현재 원가 및 미래 예상 원가 계산
        MenuCostResponse current = calculateCurrentMenuCost(menuId, validMealCount, null);
        MenuCostResponse future = calculateFutureMenuCost(menuId, validTargetDate, validMealCount, null);

        // 2. 1인분 기준 차액 및 상승률 계산 (유틸리티 활용)
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
        log.info(">> 식수({}명) 총원가: 현재 {}원 -> 미래 {}원 | 총 차액: {}원",
                validMealCount, currentTotal, futureTotal, totalCostDifference);
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
        int validMealCount = resolveMealCount(mealCount);
        List<Long> menuIds = costRepository.findAllMenuIds();
        List<MenuCostComparisonResponse> result = new ArrayList<>();

        for (Long menuId : menuIds) {
            result.add(compareMenuCost(menuId, validTargetDate, validMealCount));
        }

        return result;
    }

    /**
     * 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 식별 및 랭킹 분석 (단건, COST-006)
     */
    public CostDriverResponse identifyCostDrivers(Long menuId, LocalDate targetDate) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);

        MenuCostResponse current = calculateCurrentMenuCost(menuId, 1, null);
        MenuCostResponse future = calculateFutureMenuCost(menuId, validTargetDate, 1, null);

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
            log.info(">> 🔥 [최대 상승 기여 식재료 (Top 1)]: [{}] (상승액: {}원, 단가변동: {}%, 기여율: {}%)",
                    topDriver.getIngredientName(), topDriver.getLineCostDifference(),
                    topDriver.getUnitPriceIncreaseRate(), topDriver.getContributionRate());
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
        List<Long> menuIds = costRepository.findAllMenuIds();
        List<CostDriverResponse> result = new ArrayList<>();

        for (Long menuId : menuIds) {
            result.add(identifyCostDrivers(menuId, validTargetDate));
        }

        return result;
    }
}
