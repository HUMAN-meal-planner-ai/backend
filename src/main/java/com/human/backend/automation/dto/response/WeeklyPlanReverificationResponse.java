package com.human.backend.automation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [AUTO-004] 재평가된 주간 식단 예산 위험 재확인 결과 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class WeeklyPlanReverificationResponse {

    // 1. 기본 대상 정보
    private final Long facilityId;
    private final String facilityName;
    private final LocalDate weekStartDate;
    private final LocalDate weekEndDate;
    private final String budgetMonth;
    private final Integer mealCount;

    // 2. 월 예산 현황
    private final BigDecimal monthlyBudget;               // 월 배정 총 예산
    private final BigDecimal currentSpentCost;            // 월초~직전까지 기 집행액
    private final BigDecimal monthlyRemainingBudget;      // 월 잔여 예산 (배정예산 - 집행액)

    // 3. 주간 식단 비용 재평가 전/후 비교
    private final BigDecimal originalWeeklyCost;          // 재평가 전 기존 주간 식단 비용
    private final BigDecimal reevaluatedWeeklyCost;       // 재평가/재구성된 주간 식단 비용
    private final BigDecimal costDifference;              // 비용 차액 (기존비용 - 재평가비용, 양수면 절감)
    private final BigDecimal savingsRate;                 // 절감률 (%)

    // 4. 예산 위험 재확인 및 진단 결과
    private final BigDecimal projectedRemainingBudget;    // 재평가 식단 적용 후 잔여 예산
    private final BigDecimal exceededAmount;              // 여전히 초과되는 금액 (초과 없을 시 0)
    private final String previousRiskLevel;               // 이전 위험 등급 (WARNING / CAUTION / SAFE)
    private final String recheckedRiskLevel;              // 재확인된 위험 등급 (WARNING / CAUTION / SAFE)
    private final boolean isRisk;                         // 여전히 초과 위험이 있는지 여부
    private final boolean isRiskResolved;                 // 위험이 해소되었는지 여부 (이전 위험 -> SAFE 전환)
    private final String verificationMessage;             // 사용자용 종합 진단 및 피드백 메시지
    private final LocalDateTime verifiedAt;               // 재확인 일시

    // 5. 일자별 세부 비용 내역
    private final List<DailyReevaluatedCostDetail> dailyBreakdown;

    /**
     * [응집도 향상] 주간 식단 재평가 수치들로부터 스스로 위험도 및 피드백 메시지를 산출하여 DTO를 완성하는 팩토리 메서드
     */
    public static WeeklyPlanReverificationResponse of(
            Long facilityId, String facilityName,
            LocalDate weekStartDate, LocalDate weekEndDate, String budgetMonth, Integer mealCount,
            BigDecimal monthlyBudget, BigDecimal currentSpentCost, BigDecimal monthlyRemainingBudget,
            BigDecimal originalWeeklyCost, BigDecimal reevaluatedWeeklyCost, BigDecimal costDifference, BigDecimal savingsRate,
            List<DailyReevaluatedCostDetail> dailyBreakdown) {

        BigDecimal projectedRemainingBudget = monthlyRemainingBudget.subtract(reevaluatedWeeklyCost);
        boolean isRisk = projectedRemainingBudget.compareTo(BigDecimal.ZERO) < 0;
        BigDecimal exceededAmount = isRisk ? projectedRemainingBudget.abs() : BigDecimal.ZERO;

        String previousRiskLevel = determineRiskLevel(
                monthlyRemainingBudget.subtract(originalWeeklyCost).compareTo(BigDecimal.ZERO) < 0,
                originalWeeklyCost, monthlyRemainingBudget);
        String recheckedRiskLevel = determineRiskLevel(isRisk, reevaluatedWeeklyCost, monthlyRemainingBudget);

        boolean isRiskResolved = (!"SAFE".equals(previousRiskLevel) && "SAFE".equals(recheckedRiskLevel))
                || (costDifference.compareTo(BigDecimal.ZERO) > 0 && !isRisk);

        String verificationMessage = buildVerificationMessage(
                previousRiskLevel, recheckedRiskLevel, costDifference, savingsRate,
                reevaluatedWeeklyCost, monthlyRemainingBudget, exceededAmount, isRiskResolved);

        return WeeklyPlanReverificationResponse.builder()
                .facilityId(facilityId)
                .facilityName(facilityName)
                .weekStartDate(weekStartDate)
                .weekEndDate(weekEndDate)
                .budgetMonth(budgetMonth)
                .mealCount(mealCount)
                .monthlyBudget(monthlyBudget)
                .currentSpentCost(currentSpentCost)
                .monthlyRemainingBudget(monthlyRemainingBudget)
                .originalWeeklyCost(originalWeeklyCost)
                .reevaluatedWeeklyCost(reevaluatedWeeklyCost)
                .costDifference(costDifference)
                .savingsRate(savingsRate)
                .projectedRemainingBudget(projectedRemainingBudget)
                .exceededAmount(exceededAmount)
                .previousRiskLevel(previousRiskLevel)
                .recheckedRiskLevel(recheckedRiskLevel)
                .isRisk(isRisk)
                .isRiskResolved(isRiskResolved)
                .verificationMessage(verificationMessage)
                .verifiedAt(LocalDateTime.now())
                .dailyBreakdown(dailyBreakdown)
                .build();
    }

    private static String determineRiskLevel(boolean isRisk, BigDecimal weeklyCost, BigDecimal remainingBudget) {
        if (isRisk) {
            return "WARNING";
        }
        if (remainingBudget != null && remainingBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usageRatio = weeklyCost.divide(remainingBudget, 4, java.math.RoundingMode.HALF_UP);
            if (usageRatio.compareTo(new BigDecimal("0.50")) >= 0) {
                return "CAUTION";
            }
        }
        return "SAFE";
    }

    private static String buildVerificationMessage(String prevLevel, String newLevel, BigDecimal costDiff, BigDecimal savingsRate,
                                                   BigDecimal reevaluatedCost, BigDecimal remainingBudget, BigDecimal exceededAmount,
                                                   boolean isResolved) {
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,###");
        if (isResolved) {
            return String.format("🎉 [예산 위험 해소] 주간 식단 재평가를 통해 %s원(%s%%)의 원가가 절감되어 예산 위험 등급이 '%s'에서 '%s'(안정)으로 개선되었습니다. (적용 후 잔여 예산: %s원)",
                    df.format(costDiff), savingsRate, prevLevel, newLevel, df.format(remainingBudget.subtract(reevaluatedCost)));
        } else if ("WARNING".equals(newLevel)) {
            return String.format("🚨 [여전히 예산 초과 경고] 재평가된 주간 식단 비용(%s원)이 월 잔여 예산(%s원)을 %s원 초과합니다. 추가적인 고원가 메뉴 교체 또는 식재료 단가 조정이 필요합니다.",
                    df.format(reevaluatedCost), df.format(remainingBudget), df.format(exceededAmount));
        } else if ("CAUTION".equals(newLevel)) {
            return String.format("⚠️ [예산 주의 유지] 재평가된 주간 식단 비용(%s원)이 월 잔여 예산(%s원)의 상당 부분을 소진할 예정입니다. 지속적인 모니터링이 권장됩니다.",
                    df.format(reevaluatedCost), df.format(remainingBudget));
        } else {
            return String.format("✅ [예산 적정 확인] 재평가된 주간 식단 비용(%s원)이 월 잔여 예산(%s원) 범위 내에서 안전하게 관리되고 있습니다.",
                    df.format(reevaluatedCost), df.format(remainingBudget));
        }
    }

    /**
     * 일자별 식단 재평가 비용 세부 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DailyReevaluatedCostDetail {
        private final LocalDate mealDate;
        private final Long menuId;
        private final String menuName;
        private final BigDecimal costPerPerson;
        private final BigDecimal totalCost;
    }
}
