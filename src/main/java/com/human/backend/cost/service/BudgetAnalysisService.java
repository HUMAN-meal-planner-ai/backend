package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.BudgetRiskResponse;
import com.human.backend.cost.dto.response.BudgetUsageRateResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

import static com.human.backend.cost.util.CostCalculationUtils.*;
import static com.human.backend.cost.util.CostConstants.*;

/**
 * 예산 초과 위험 및 예산 대비 사용률 분석 전담 서비스
 * 담당 요구사항:
 * - BUDG-002: 이번 주·다음 주 예상 비용과 월 잔여 예산을 기준으로 예산 초과 위험이 있으면 경고한다.
 * - COST-014: 설정된 예산 대비 예상 사용액과 사용률을 표시한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAnalysisService {

    private final CostRepository costRepository;

    /**
     * 이번 주·다음 주 예상 비용과 월 잔여 예산 기준 예산 초과 위험 분석 (BUDG-002)
     */
    public BudgetRiskResponse evaluateBudgetRisk(Long facilityId, LocalDate baseDate) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        LocalDate targetBaseDate = (baseDate != null) ? baseDate : DEFAULT_BASE_DATE;

        YearMonth budgetMonth = YearMonth.from(targetBaseDate);
        LocalDate monthStart = budgetMonth.atDay(1);

        // 1. 이번 주 / 다음 주 기간 산출 (월요일 ~ 일요일 기준)
        LocalDate thisWeekMonday = targetBaseDate.with(DayOfWeek.MONDAY);
        LocalDate thisWeekSunday = targetBaseDate.with(DayOfWeek.SUNDAY);
        LocalDate nextWeekMonday = thisWeekMonday.plusWeeks(1);
        LocalDate nextWeekSunday = thisWeekSunday.plusWeeks(1);

        // 2. 시설 월 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, budgetMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, budgetMonth,
                        DEFAULT_MONTHLY_BUDGET));

        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 3. 월초부터 이번 주 직전(일요일)까지의 과거 누적 집행 비용
        BigDecimal currentSpentCost = BigDecimal.ZERO;
        if (thisWeekMonday.isAfter(monthStart)) {
            LocalDate pastEnd = thisWeekMonday.minusDays(1);
            List<MealPlanCostVo> pastPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                    monthStart, pastEnd);
            currentSpentCost = calculateTotalMealPlansCost(pastPlans);
        }

        // 4. 이번 주 식단 목록 및 예상 비용
        List<MealPlanCostVo> thisWeekPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                thisWeekMonday, thisWeekSunday);
        BigDecimal thisWeekExpectedCost = calculateTotalMealPlansCost(thisWeekPlans);

        // 5. 다음 주 식단 목록 및 예상 비용
        List<MealPlanCostVo> nextWeekPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                nextWeekMonday, nextWeekSunday);
        BigDecimal nextWeekExpectedCost = calculateTotalMealPlansCost(nextWeekPlans);

        // 6. 예산 및 잔여액 계산
        BigDecimal monthlyRemainingBudget = monthlyBudget.subtract(currentSpentCost);
        BigDecimal twoWeeksTotalExpectedCost = thisWeekExpectedCost.add(nextWeekExpectedCost);
        BigDecimal projectedRemainingBudget = monthlyRemainingBudget.subtract(twoWeeksTotalExpectedCost);

        // 7. 초과 위험도 및 경고 판정
        boolean isRisk = projectedRemainingBudget.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal exceededAmount = isRisk ? projectedRemainingBudget.abs() : BigDecimal.ZERO;
        String riskLevel = determineRiskLevel(isRisk, twoWeeksTotalExpectedCost, monthlyRemainingBudget);
        String warningMessage = buildWarningMessage(riskLevel, twoWeeksTotalExpectedCost, monthlyRemainingBudget,
                exceededAmount);

        // 8. 세부 DTO 매핑
        List<BudgetRiskResponse.DailyPlanCostDetail> thisWeekDetails = toDailyPlanCostDetails(thisWeekPlans);
        List<BudgetRiskResponse.DailyPlanCostDetail> nextWeekDetails = toDailyPlanCostDetails(nextWeekPlans);

        log.info(">> [BUDG-002 예산 위험 분석] 시설 ID: {}, 월 예산: {}원, 잔여 예산: {}원, 2주 예상비용: {}원, 위험수준: {}",
                targetFacilityId, monthlyBudget, monthlyRemainingBudget, twoWeeksTotalExpectedCost, riskLevel);

        return BudgetRiskResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(budgetVo.getFacilityName())
                .baseDate(targetBaseDate)
                .budgetMonth(budgetMonth.toString())
                .monthlyBudget(monthlyBudget)
                .currentSpentCost(currentSpentCost)
                .monthlyRemainingBudget(monthlyRemainingBudget)
                .thisWeekExpectedCost(thisWeekExpectedCost)
                .nextWeekExpectedCost(nextWeekExpectedCost)
                .twoWeeksTotalExpectedCost(twoWeeksTotalExpectedCost)
                .projectedRemainingBudget(projectedRemainingBudget)
                .exceededAmount(exceededAmount)
                .riskLevel(riskLevel)
                .isRisk(isRisk)
                .warningMessage(warningMessage)
                .thisWeekDetails(thisWeekDetails)
                .nextWeekDetails(nextWeekDetails)
                .build();
    }

    /**
     * 설정된 예산 대비 예상 사용액과 사용률 분석 (COST-014)
     */
    public BudgetUsageRateResponse evaluateBudgetUsage(Long facilityId, String yearMonthStr, LocalDate baseDate) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        YearMonth targetYearMonth = parseYearMonth(yearMonthStr);
        LocalDate monthStart = targetYearMonth.atDay(1);
        LocalDate monthEnd = targetYearMonth.atEndOfMonth();

        LocalDate targetBaseDate;
        if (baseDate != null) {
            targetBaseDate = baseDate;
        } else if (targetYearMonth.equals(YearMonth.from(DEFAULT_BASE_DATE))) {
            targetBaseDate = DEFAULT_BASE_DATE;
        } else {
            targetBaseDate = monthStart;
        }

        // 1. 시설 및 월 배정 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, targetYearMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, targetYearMonth,
                        DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 2. 기준일 이전 기 집행(과거) 식단 비용 산출
        BigDecimal actualSpentCost = BigDecimal.ZERO;
        if (targetBaseDate.isAfter(monthStart)) {
            LocalDate pastEnd = targetBaseDate.minusDays(1);
            List<MealPlanCostVo> pastPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                    monthStart, pastEnd);
            actualSpentCost = calculateTotalMealPlansCost(pastPlans);
        }

        // 3. 기준일부터 월말까지 잔여 예상 식단 비용 산출
        BigDecimal projectedRemainingCost = BigDecimal.ZERO;
        if (!targetBaseDate.isAfter(monthEnd)) {
            List<MealPlanCostVo> futurePlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                    targetBaseDate, monthEnd);
            projectedRemainingCost = calculateTotalMealPlansCost(futurePlans);
        }

        // 4. 월 총 예상 사용액 = 기 집행액 + 잔여 예상액
        BigDecimal totalExpectedCost = actualSpentCost.add(projectedRemainingCost);

        // 5. 사용률 계산 (유틸리티 활용)
        BigDecimal currentUsageRate = calculatePercentage(actualSpentCost, monthlyBudget);
        BigDecimal expectedUsageRate = calculatePercentage(totalExpectedCost, monthlyBudget);

        // 6. 잔여 예산 및 초과 여부 산출
        BigDecimal remainingBudget = monthlyBudget.subtract(totalExpectedCost);
        boolean isExceeded = remainingBudget.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal exceededAmount = isExceeded ? remainingBudget.abs() : BigDecimal.ZERO;

        // 7. 상태 및 상태 메시지 판정
        String status = determineBudgetUsageStatus(isExceeded, expectedUsageRate);
        String statusMessage = buildBudgetUsageMessage(status, monthlyBudget, totalExpectedCost, expectedUsageRate,
                remainingBudget, exceededAmount);

        log.info("==================================================");
        log.info(">> [COST-014 예산 대비 사용률 분석] 시설: {} (ID: {}) | 대상월: {} (기준일: {})", budgetVo.getFacilityName(),
                targetFacilityId, targetYearMonth, targetBaseDate);
        log.info(">> 월 배정 예산: {}원 | 기 집행액: {}원 ({}%) | 잔여 예상액: {}원", monthlyBudget, actualSpentCost, currentUsageRate,
                projectedRemainingCost);
        log.info(">> 총 예상 사용액: {}원 | 최종 예상 사용률: {}% | 잔여 예산: {}원 | 상태: {}", totalExpectedCost, expectedUsageRate,
                remainingBudget, status);
        log.info("--------------------------------------------------");

        return BudgetUsageRateResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(budgetVo.getFacilityName())
                .yearMonth(targetYearMonth.toString())
                .baseDate(targetBaseDate)
                .monthlyBudget(monthlyBudget)
                .actualSpentCost(actualSpentCost)
                .projectedRemainingCost(projectedRemainingCost)
                .totalExpectedCost(totalExpectedCost)
                .currentUsageRate(currentUsageRate)
                .expectedUsageRate(expectedUsageRate)
                .remainingBudget(remainingBudget)
                .isExceeded(isExceeded)
                .exceededAmount(exceededAmount)
                .status(status)
                .statusMessage(statusMessage)
                .build();
    }

    private BigDecimal calculateTotalMealPlansCost(List<MealPlanCostVo> plans) {
        if (plans == null || plans.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return plans.stream()
                .map(MealPlanCostVo::calculateTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String determineRiskLevel(boolean isRisk, BigDecimal twoWeeksCost, BigDecimal remainingBudget) {
        if (isRisk) {
            return "WARNING";
        }
        if (remainingBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usageRatio = twoWeeksCost.divide(remainingBudget, PERCENT_CALC_SCALE, RoundingMode.HALF_UP);
            if (usageRatio.compareTo(new BigDecimal("0.70")) >= 0) {
                return "CAUTION";
            }
        }
        return "SAFE";
    }

    private String buildWarningMessage(String riskLevel, BigDecimal twoWeeksCost, BigDecimal remainingBudget,
            BigDecimal exceededAmount) {
        DecimalFormat df = new DecimalFormat("#,###");
        if ("WARNING".equals(riskLevel)) {
            return String.format(
                    "🚨 [예산 초과 경고] 향후 2주간(이번 주+다음 주) 예상 식단 비용(%s원)이 월 잔여 예산(%s원)을 %s원 초과할 것으로 예상됩니다. 고원가 식단 조정 또는 대체 식재료 검토가 필요합니다.",
                    df.format(twoWeeksCost), df.format(remainingBudget), df.format(exceededAmount));
        } else if ("CAUTION".equals(riskLevel)) {
            return String.format(
                    "⚠️ [예산 관리 주의] 향후 2주간 예상 비용(%s원)이 월 잔여 예산(%s원)의 70%% 이상을 소진할 예정입니다. 지속적인 원가 모니터링을 권장합니다.",
                    df.format(twoWeeksCost), df.format(remainingBudget));
        } else {
            return String.format(
                    "✅ [예산 안정] 향후 2주간 예상 비용(%s원)이 월 잔여 예산(%s원) 범위 내에서 안정적으로 운영되고 있습니다.",
                    df.format(twoWeeksCost), df.format(remainingBudget));
        }
    }

    private String determineBudgetUsageStatus(boolean isExceeded, BigDecimal expectedUsageRate) {
        if (isExceeded) {
            return "EXCEEDED";
        }
        if (expectedUsageRate.compareTo(new BigDecimal("95.00")) >= 0) {
            return "WARNING";
        }
        if (expectedUsageRate.compareTo(new BigDecimal("80.00")) >= 0) {
            return "CAUTION";
        }
        return "STABLE";
    }

    private String buildBudgetUsageMessage(String status, BigDecimal monthlyBudget, BigDecimal totalExpectedCost,
            BigDecimal expectedUsageRate, BigDecimal remainingBudget, BigDecimal exceededAmount) {
        DecimalFormat df = new DecimalFormat("#,###");
        if ("EXCEEDED".equals(status)) {
            return String.format("🚨 [예산 초과] 총 예상 사용액(%s원)이 월 배정 예산(%s원)을 %s원 초과할 것으로 예상됩니다 (예상 사용률: %s%%).",
                    df.format(totalExpectedCost), df.format(monthlyBudget), df.format(exceededAmount),
                    expectedUsageRate);
        } else if ("WARNING".equals(status)) {
            return String.format("⚠️ [초과 위험] 총 예상 사용액(%s원)이 예산의 95%%에 육박하여 잔여 예산(%s원) 관리가 시급합니다 (예상 사용률: %s%%).",
                    df.format(totalExpectedCost), df.format(remainingBudget), expectedUsageRate);
        } else if ("CAUTION".equals(status)) {
            return String.format("⚠️ [주의 요망] 총 예상 사용액(%s원)이 예산의 80%% 이상을 소진할 것으로 예상됩니다 (예상 사용률: %s%%).",
                    df.format(totalExpectedCost), expectedUsageRate);
        } else {
            return String.format("✅ [안정적 운영] 총 예상 사용액(%s원)이 월 배정 예산(%s원) 범위 내에서 안정적으로 집행될 예정입니다 (예상 사용률: %s%%).",
                    df.format(totalExpectedCost), df.format(monthlyBudget), expectedUsageRate);
        }
    }

    private List<BudgetRiskResponse.DailyPlanCostDetail> toDailyPlanCostDetails(List<MealPlanCostVo> plans) {
        if (plans == null) {
            return List.of();
        }
        return plans.stream()
                .map(plan -> BudgetRiskResponse.DailyPlanCostDetail.builder()
                        .planId(plan.getPlanId())
                        .planDate(plan.getPlanDate())
                        .mealType(plan.getMealType())
                        .mealCount(plan.getMealCount())
                        .costPerPerson(plan.getExpectedCostPerPerson())
                        .totalDailyCost(plan.calculateTotalCost())
                        .build())
                .collect(Collectors.toList());
    }
}
