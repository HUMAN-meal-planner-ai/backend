package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.BudgetRiskResponse;
import com.human.backend.cost.dto.response.BudgetUsageRateResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.facility.repository.MonthlyBudgetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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
 * [예산 초과 위험 및 예산 대비 사용률 분석 전담 서비스]
 *
 * ■ 담당 요구사항:
 *   - BUDG-002: 이번 주·다음 주 예상 비용과 월 잔여 예산을 기준으로 예산 초과 위험이 있으면 경고한다.
 *   - COST-014: 설정된 예산 대비 예상 사용액과 사용률을 표시한다.
 *
 * ■ 분석 처리 흐름 (Analysis Flow):
 *   1. [BUDG-002 2주 예산 초과 위험 분석]
 *      ① [과거 기 집행액 산출] : 월초(1일) ~ 이번 주 직전(일요일)까지의 실제 집행 비용 합산 (currentSpentCost)
 *      ② [월 잔여 예산 도출]    : 월 배정 예산(monthlyBudget) - 과거 기 집행액 = 월 잔여 예산(monthlyRemainingBudget)
 *      ③ [향후 2주 소요액 산출] : 이번 주 예상 비용 + 다음 주 예상 비용 = 2주간 총 예상 소요액 (twoWeeksTotalExpectedCost)
 *      ④ [예산 초과 위험 판정] : 2주 예상 소요액 > 월 잔여 예산인 경우
 *                                 ──> 초과 여부(isRisk = true), 초과액(exceededAmount), 경고 등급(WARNING/CAUTION/SAFE)
 *      ⑤ [경고 메시지 생성]    : 관리자가 직관적으로 대응할 수 있는 상세 권고사항 포맷팅
 *
 *   2. [COST-014 월 예산 소진율 및 사용률 분석]
 *      ① [기 집행액 + 잔여 예상액 합산] : 기준일 이전(과거) 지출액 + 기준일 이후(미래) 예상액 = 월 총 예상 사용액
 *      ② [소진율(%) 계산]              : (월 총 예상 사용액 / 월 배정 예산) * 100
 *      ③ [운영 상태 판정]              : EXCEEDED(초과), WARNING(95% 이상), CAUTION(80% 이상), STABLE(안정)
 */
@Slf4j
@Service
public class BudgetAnalysisService {

    private final CostRepository costRepository;
    private final MonthlyBudgetRepository monthlyBudgetRepository;

    public BudgetAnalysisService(CostRepository costRepository) {
        this(costRepository, null);
    }

    @Autowired
    public BudgetAnalysisService(CostRepository costRepository, MonthlyBudgetRepository monthlyBudgetRepository) {
        this.costRepository = costRepository;
        this.monthlyBudgetRepository = monthlyBudgetRepository;
    }

    /**
     * 이번 주(1주) 및 이번 주+다음 주(2주) 예상 비용과 월 잔여 예산 기준 예산 초과 위험 분석 (BUDG-002)
     * - 1주 시뮬레이션: 이번 주 식단 소요액 기준 잔여 예산 및 초과 위험 진단
     * - 2주 시뮬레이션: 이번 주 + 다음 주 2주 누적 소요액 기준 잔여 예산 및 초과 위험 진단
     */
    public BudgetRiskResponse evaluateBudgetRisk(Long facilityId, LocalDate baseDate) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        LocalDate targetBaseDate = resolveBaseDate(baseDate);

        YearMonth budgetMonth = YearMonth.from(targetBaseDate);
        LocalDate monthStart = budgetMonth.atDay(1);

        // 1. 이번 주(1주) / 다음 주 기간 산출 (월요일 ~ 일요일 기준)
        LocalDate thisWeekMonday = targetBaseDate.with(DayOfWeek.MONDAY);
        LocalDate thisWeekSunday = targetBaseDate.with(DayOfWeek.SUNDAY);
        LocalDate nextWeekMonday = thisWeekMonday.plusWeeks(1);
        LocalDate nextWeekSunday = thisWeekSunday.plusWeeks(1);

        // 2. 시설 월 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, budgetMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, budgetMonth,
                        DEFAULT_MONTHLY_BUDGET));

        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 3. 기 집행액은 결제·영수증 데이터가 없으므로 사용자가 저장한 수동 금액만 사용합니다.
        BigDecimal currentSpentCost = monthlyBudgetRepository == null
            ? BigDecimal.ZERO
            : monthlyBudgetRepository.findExecutedAmount(targetFacilityId, budgetMonth)
                .orElse(BigDecimal.ZERO);

        // 4. 이번 주(1주차) 식단 목록 및 예상 비용
        List<MealPlanCostVo> thisWeekPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                thisWeekMonday, thisWeekSunday);
        BigDecimal thisWeekExpectedCost = calculateTotalMealPlansCost(thisWeekPlans);

        // 5. 다음 주(2주차) 식단 목록 및 예상 비용
        List<MealPlanCostVo> nextWeekPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                nextWeekMonday, nextWeekSunday);
        BigDecimal nextWeekExpectedCost = calculateTotalMealPlansCost(nextWeekPlans);

        BigDecimal monthlyRemainingBudget = monthlyBudget.subtract(currentSpentCost);

        // =========================================================================
        // 6. [1주간 시뮬레이션] 이번 주 기준 위험 및 잔여 예산 진단
        // =========================================================================
        BigDecimal oneWeekExpectedCost = thisWeekExpectedCost;
        BigDecimal oneWeekProjectedRemainingBudget = monthlyRemainingBudget.subtract(oneWeekExpectedCost);
        boolean oneWeekIsRisk = oneWeekProjectedRemainingBudget.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal oneWeekExceededAmount = oneWeekIsRisk ? oneWeekProjectedRemainingBudget.abs() : BigDecimal.ZERO;
        String oneWeekRiskLevel = determineRiskLevel(oneWeekIsRisk, oneWeekExpectedCost, monthlyRemainingBudget);
        String oneWeekWarningMessage = buildPeriodWarningMessage("1주간(이번 주)", oneWeekRiskLevel, oneWeekExpectedCost,
                monthlyRemainingBudget, oneWeekExceededAmount);

        // =========================================================================
        // 7. [2주간 시뮬레이션] 이번 주 + 다음 주 누적 위험 및 잔여 예산 진단
        // =========================================================================
        BigDecimal twoWeeksTotalExpectedCost = thisWeekExpectedCost.add(nextWeekExpectedCost);
        BigDecimal twoWeeksProjectedRemainingBudget = monthlyRemainingBudget.subtract(twoWeeksTotalExpectedCost);
        boolean twoWeeksIsRisk = twoWeeksProjectedRemainingBudget.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal twoWeeksExceededAmount = twoWeeksIsRisk ? twoWeeksProjectedRemainingBudget.abs() : BigDecimal.ZERO;
        String twoWeeksRiskLevel = determineRiskLevel(twoWeeksIsRisk, twoWeeksTotalExpectedCost, monthlyRemainingBudget);
        String twoWeeksWarningMessage = buildPeriodWarningMessage("2주간(이번 주+다음 주)", twoWeeksRiskLevel, twoWeeksTotalExpectedCost,
                monthlyRemainingBudget, twoWeeksExceededAmount);

        // 8. 세부 DTO 매핑
        List<BudgetRiskResponse.DailyPlanCostDetail> thisWeekDetails = toDailyPlanCostDetails(thisWeekPlans);
        List<BudgetRiskResponse.DailyPlanCostDetail> nextWeekDetails = toDailyPlanCostDetails(nextWeekPlans);

        log.info(">> [BUDG-002 예산 시뮬레이션] 시설 ID: {}, 월 예산: {}원, 기집행: {}원, 잔여: {}원 | 1주예상: {}원(위험:{}), 2주예상: {}원(위험:{})",
                targetFacilityId, monthlyBudget, currentSpentCost, monthlyRemainingBudget,
                oneWeekExpectedCost, oneWeekRiskLevel, twoWeeksTotalExpectedCost, twoWeeksRiskLevel);

        return BudgetRiskResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(budgetVo.getFacilityName())
                .baseDate(targetBaseDate)
                .budgetMonth(budgetMonth.toString())
                .monthlyBudget(monthlyBudget)
                .currentSpentCost(currentSpentCost)
                .monthlyRemainingBudget(monthlyRemainingBudget)
                // 1주 시뮬레이션 지표
                .thisWeekExpectedCost(thisWeekExpectedCost)
                .oneWeekExpectedCost(oneWeekExpectedCost)
                .oneWeekProjectedRemainingBudget(oneWeekProjectedRemainingBudget)
                .oneWeekExceededAmount(oneWeekExceededAmount)
                .oneWeekRiskLevel(oneWeekRiskLevel)
                .oneWeekIsRisk(oneWeekIsRisk)
                .oneWeekWarningMessage(oneWeekWarningMessage)
                // 2주 시뮬레이션 지표
                .nextWeekExpectedCost(nextWeekExpectedCost)
                .twoWeeksTotalExpectedCost(twoWeeksTotalExpectedCost)
                .twoWeeksProjectedRemainingBudget(twoWeeksProjectedRemainingBudget)
                .twoWeeksExceededAmount(twoWeeksExceededAmount)
                .twoWeeksRiskLevel(twoWeeksRiskLevel)
                .twoWeeksIsRisk(twoWeeksIsRisk)
                .twoWeeksWarningMessage(twoWeeksWarningMessage)
                // 하위 호환 매핑 (2주 기준)
                .projectedRemainingBudget(twoWeeksProjectedRemainingBudget)
                .exceededAmount(twoWeeksExceededAmount)
                .riskLevel(twoWeeksRiskLevel)
                .isRisk(twoWeeksIsRisk)
                .warningMessage(twoWeeksWarningMessage)
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
        } else if (targetYearMonth.equals(YearMonth.now())) {
            targetBaseDate = LocalDate.now();
        } else {
            targetBaseDate = monthStart;
        }

        // 1. 시설 및 월 배정 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, targetYearMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, targetYearMonth,
                        DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 2. 기 집행액은 시스템이 추정하지 않고 사용자가 입력한 수동 금액을 사용합니다.
        BigDecimal actualSpentCost = monthlyBudgetRepository == null
            ? BigDecimal.ZERO
            : monthlyBudgetRepository.findExecutedAmount(targetFacilityId, targetYearMonth)
                .orElse(BigDecimal.ZERO);

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

    private String determineRiskLevel(boolean isRisk, BigDecimal periodCost, BigDecimal remainingBudget) {
        if (isRisk) {
            return "WARNING";
        }
        if (remainingBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usageRatio = periodCost.divide(remainingBudget, PERCENT_CALC_SCALE, RoundingMode.HALF_UP);
            if (usageRatio.compareTo(new BigDecimal("0.70")) >= 0) {
                return "CAUTION";
            }
        }
        return "SAFE";
    }

    private String buildPeriodWarningMessage(String periodLabel, String riskLevel, BigDecimal periodCost,
            BigDecimal remainingBudget, BigDecimal exceededAmount) {
        DecimalFormat df = new DecimalFormat("#,###");
        if ("WARNING".equals(riskLevel)) {
            return String.format(
                    "🚨 [예산 초과 경고] %s 예상 식단 비용(%s원)이 월 잔여 예산(%s원)을 %s원 초과할 것으로 예상됩니다. 고원가 식단 조정 또는 대체 식재료 검토가 필요합니다.",
                    periodLabel, df.format(periodCost), df.format(remainingBudget), df.format(exceededAmount));
        } else if ("CAUTION".equals(riskLevel)) {
            return String.format(
                    "⚠️ [예산 관리 주의] %s 예상 비용(%s원)이 월 잔여 예산(%s원)의 70%% 이상을 소진할 예정입니다. 지속적인 원가 모니터링을 권장합니다.",
                    periodLabel, df.format(periodCost), df.format(remainingBudget));
        } else {
            return String.format(
                    "✅ [예산 안정] %s 예상 비용(%s원)이 월 잔여 예산(%s원) 범위 내에서 안정적으로 운영되고 있습니다.",
                    periodLabel, df.format(periodCost), df.format(remainingBudget));
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
                .map(plan -> {
                    List<Long> menuIds = costRepository.findMenuIdsByPlanId(plan.getPlanId());
                    String menuNames = menuIds.stream()
                            .map(mId -> costRepository.findMenuNameById(mId).orElse("메뉴 " + mId))
                            .collect(Collectors.joining(", "));
                    return BudgetRiskResponse.DailyPlanCostDetail.builder()
                            .planId(plan.getPlanId())
                            .planDate(plan.getPlanDate())
                            .mealType(plan.getMealType())
                            .mealCount(plan.getMealCount())
                            .costPerPerson(plan.getExpectedCostPerPerson())
                            .totalDailyCost(plan.calculateTotalCost())
                            .menuNames(menuNames.isBlank() ? "편성 메뉴 없음" : menuNames)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
