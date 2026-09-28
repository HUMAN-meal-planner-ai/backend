package com.human.backend.budget.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [BUDG-003] 선택 주차 식단 비용 기여도 높은 메뉴 식별 및 재구성 변경 검토 후보 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyHighCostMenuCandidateResponse {

    // 1. 기본 식별 및 주차 정보
    private Long facilityId;
    private String facilityName;
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private String budgetMonth;

    // 2. 주간 식단 비용 및 예산 종합 지표
    private BigDecimal monthlyBudget;              // 배정된 월 예산
    private BigDecimal weeklyTotalCost;            // 선택 주차 총 식단 예상 비용 (원)
    private Integer totalUniqueMenuCount;          // 주간 식단 구성 고유 메뉴 수
    private Integer totalMealCount;                // 주간 총 누적 식수 (명)
    private BigDecimal averageCostPerMeal;         // 끼니 1인당 평균 단가 (원)

    // 3. 고비용 메뉴 변경 검토 후보 목록 (비용 기여도 순)
    private List<HighCostMenuDto> candidates;

    // 4. 재구성 의사결정을 돕는 종합 진단 및 권고사항
    private String recommendationSummary;
}
