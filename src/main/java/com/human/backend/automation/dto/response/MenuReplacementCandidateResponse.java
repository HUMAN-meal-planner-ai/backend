package com.human.backend.automation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * [AUTO-006] 주간 재평가 메뉴 변경 검토 후보 탐지 결과 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuReplacementCandidateResponse {

    private final Long facilityId;
    private final LocalDate weekStartDate;
    private final LocalDate targetDate;
    private final Integer totalMenusEvaluated;          // 평가 대상 총 메뉴 수
    private final Integer candidateCount;               // 탐지된 변경 검토 후보 메뉴 수
    private final String evaluationSummary;             // 전체 종합 평가 요약
    private final LocalDateTime evaluatedAt;            // 탐지 일시

    private final List<MenuReplacementCandidate> candidates; // 변경 검토 후보 메뉴 목록 (영향도 높은 순)

    /**
     * 개별 변경 검토 후보 메뉴 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class MenuReplacementCandidate {
        private final Long menuId;
        private final String menuName;
        private final LocalDate mealDate;                   // 주간 식단 내 편성일자 (해당 주차 편성 시)
        private final String mealType;                      // 끼니 유형 (LUNCH, DINNER 등)
        private final BigDecimal currentCostPerPerson;      // 현재 1인당 원가
        private final BigDecimal futureCostPerPerson;       // 미래 예측 1인당 원가
        private final BigDecimal costDifference;            // 원가 변동 차액 (미래 - 현재)
        private final BigDecimal increaseRate;              // 원가 상승률 (%)
        private final String riskLevel;                     // 메뉴 가격 위험 등급 (WARNING, CAUTION, SAFE)
        private final Integer riskScore;                    // 위험 점수
        private final boolean isCostSurge;                  // 원가 급등 여부 (상승률 임계치 초과)
        private final boolean isTargetCostExceeded;         // 목표 단가 초과 여부
        private final String topCostDriver;                 // 원가 상승 주도 1위 식재료명
        private final List<String> triggerReasons;          // 변경 검토 후보 선정 사유 목록
        private final String recommendedAction;             // 권장 조치 ("교체 권장", "유지 검토", "식재료 조정" 등)
        private final BigDecimal impactScore;               // 영향도 점수 (정렬 기준)

        /**
         * [응집도 향상] 변경 검토 후보 해당 여부 판정 (원가 급등 OR 가격 위험 OR 목표단가 초과)
         */
        public static boolean isCandidate(BigDecimal increaseRate, String riskLevel,
                                          BigDecimal currentCost, BigDecimal futureCost,
                                          BigDecimal targetCost, BigDecimal surgeThresholdRate) {
            boolean isCostSurge = increaseRate != null && surgeThresholdRate != null 
                    && increaseRate.compareTo(surgeThresholdRate) >= 0;
            boolean isHighRisk = "WARNING".equalsIgnoreCase(riskLevel) || "CAUTION".equalsIgnoreCase(riskLevel);
            boolean isTargetCostExceeded = (futureCost != null && targetCost != null && futureCost.compareTo(targetCost) > 0)
                    || (currentCost != null && targetCost != null && currentCost.compareTo(targetCost) > 0);

            return isCostSurge || isHighRisk || isTargetCostExceeded;
        }

        /**
         * [응집도 향상] 다차원 분석 결과를 바탕으로 스스로 상태 및 사유/조치/점수를 캡슐화하여 생성
         */
        public static MenuReplacementCandidate from(com.human.backend.cost.dto.response.MenuCostComparisonResponse comparison,
                                                    com.human.backend.cost.dto.response.MenuRiskResponse riskResponse,
                                                    com.human.backend.cost.dto.response.CostDriverResponse driverResponse,
                                                    com.human.backend.cost.entity.MealPlanCostVo planInfo,
                                                    BigDecimal targetCost, BigDecimal surgeThresholdRate) {
            BigDecimal currentCost = comparison.getCurrentCostPerPerson();
            BigDecimal futureCost = comparison.getFutureCostPerPerson();
            BigDecimal costDiff = comparison.getCostDifference();
            BigDecimal incRate = comparison.getIncreaseRate();

            String riskLevel = (riskResponse != null) ? riskResponse.getRiskLevel() : "SAFE";
            int riskScore = (riskResponse != null && riskResponse.getRiskScore() != null) ? riskResponse.getRiskScore() : 0;

            String topCostDriver = (driverResponse != null && driverResponse.getTopDriver() != null)
                    ? driverResponse.getTopDriver().getIngredientName() : "없음";

            boolean isCostSurge = incRate != null && surgeThresholdRate != null && incRate.compareTo(surgeThresholdRate) >= 0;
            boolean isTargetCostExceeded = (futureCost != null && targetCost != null && futureCost.compareTo(targetCost) > 0)
                    || (currentCost != null && targetCost != null && currentCost.compareTo(targetCost) > 0);

            // 1. 트리거 사유 목록 구성
            java.util.List<String> triggerReasons = new java.util.ArrayList<>();
            if (isCostSurge) {
                triggerReasons.add(String.format("원가 급등 (예측 상승률 +%s%%)", incRate));
            }
            if ("WARNING".equalsIgnoreCase(riskLevel)) {
                triggerReasons.add("식재료 가격 위험 등급 [경고 (WARNING)]");
            } else if ("CAUTION".equalsIgnoreCase(riskLevel)) {
                triggerReasons.add("식재료 가격 위험 등급 [주의 (CAUTION)]");
            }
            if (isTargetCostExceeded && futureCost != null && targetCost != null) {
                BigDecimal excess = futureCost.subtract(targetCost);
                if (excess.compareTo(BigDecimal.ZERO) > 0) {
                    triggerReasons.add(String.format("목표 단가(%s원) 대비 %s원 초과", targetCost, excess));
                }
            }

            // 2. 권장 조치 결정
            String recommendedAction;
            if ("WARNING".equalsIgnoreCase(riskLevel) || (incRate != null && incRate.compareTo(new BigDecimal("20.0")) >= 0)) {
                recommendedAction = "교체 권장 (원가 급등 및 가격 위험 심각)";
            } else if (isCostSurge && !"없음".equals(topCostDriver)) {
                recommendedAction = String.format("대체 식재료 검토 (주요 상승 원인: %s)", topCostDriver);
            } else if (isTargetCostExceeded) {
                recommendedAction = "유지 검토 및 식단 내 단가 조정";
            } else {
                recommendedAction = "원가 지속 모니터링";
            }

            // 3. 종합 영향도 점수 산출
            BigDecimal excessRatio = BigDecimal.ZERO;
            if (isTargetCostExceeded && targetCost != null && targetCost.compareTo(BigDecimal.ZERO) > 0 && futureCost != null) {
                excessRatio = futureCost.subtract(targetCost).divide(targetCost, 4, java.math.RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }
            BigDecimal impactScore = BigDecimal.valueOf(riskScore).multiply(new BigDecimal("0.4"))
                    .add((incRate != null ? incRate : BigDecimal.ZERO).multiply(new BigDecimal("0.4")))
                    .add(excessRatio.multiply(new BigDecimal("0.2")))
                    .setScale(2, java.math.RoundingMode.HALF_UP);

            LocalDate mealDate = (planInfo != null) ? planInfo.getPlanDate() : null;
            String mealType = (planInfo != null) ? planInfo.getMealType() : null;

            return MenuReplacementCandidate.builder()
                    .menuId(comparison.getMenuId())
                    .menuName(comparison.getMenuName())
                    .mealDate(mealDate)
                    .mealType(mealType)
                    .currentCostPerPerson(currentCost)
                    .futureCostPerPerson(futureCost)
                    .costDifference(costDiff)
                    .increaseRate(incRate)
                    .riskLevel(riskLevel)
                    .riskScore(riskScore)
                    .isCostSurge(isCostSurge)
                    .isTargetCostExceeded(isTargetCostExceeded)
                    .topCostDriver(topCostDriver)
                    .triggerReasons(triggerReasons)
                    .recommendedAction(recommendedAction)
                    .impactScore(impactScore)
                    .build();
        }
    }
}
