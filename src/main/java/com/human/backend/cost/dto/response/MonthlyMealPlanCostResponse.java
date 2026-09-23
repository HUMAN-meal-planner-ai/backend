package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [COST-013] 주별 예상 비용을 합산하여 월간 총 예상 식재료비를 계산하는 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyMealPlanCostResponse {

    // 시설 식별자 및 명칭
    private Long facilityId;
    private String facilityName;

    // 분석 대상 연월 (예: "2026-09")
    private String yearMonth;

    // 시설의 월 배정 예산
    private BigDecimal monthlyBudget;

    // [핵심] 주별 예상 비용을 합산한 월간 총 예상 식재료비
    private BigDecimal totalMonthlyExpectedCost;

    // 월간 총 식수 인원 합계
    private Integer totalMonthlyMealCount;

    // 월간 1인 1식 평균 예상 단가
    private BigDecimal averageCostPerPerson;

    // 월 예산 대비 예상 잔여액 (월 예산 - 월간 총 예상 식재료비)
    private BigDecimal projectedRemainingBudget;

    // 월 예산 소진 예상 비율 (%)
    private BigDecimal budgetUsageRate;

    // 주차별(1주차~N주차) 예상 식재료비 요약 목록
    private List<WeeklyCostSummary> weeklyCosts;

    /**
     * 주차별 식재료비 요약 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklyCostSummary {
        private Integer weekOfMonth;            // 주차 (예: 1주차, 2주차...)
        private String weekLabel;               // 주차 명칭 (예: "9월 1주차")
        private LocalDate startDate;            // 해당 주차 시작일
        private LocalDate endDate;              // 해당 주차 종료일
        private BigDecimal weeklyTotalCost;     // 해당 주차 총 예상 식재료비
        private Integer weeklyMealCount;        // 해당 주차 총 식수 인원
        private BigDecimal averageCostPerPerson;// 해당 주차 1인당 평균 단가
    }
}
