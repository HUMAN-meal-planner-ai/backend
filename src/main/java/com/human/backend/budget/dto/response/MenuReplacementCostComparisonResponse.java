package com.human.backend.budget.dto.response;

import com.human.backend.menu.domain.MenuSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [BUDG-005] 대체 메뉴 적용 전후 예상 비용 차이 및 절감액 종합 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuReplacementCostComparisonResponse {

    // 1. 시설 및 기준 일자/끼니 정보
    private final Long facilityId;
    private final String facilityName;
    private final LocalDate targetDate;
    private final String dayOfWeek;
    private final Long planId;
    private final String mealType;
    private final Integer mealCount;

    // 2. 기존 메뉴 정보
    private final Long originalMenuId;
    private final String originalMenuName;
    private final MenuSlot originalSlot;
    private final String originalSlotName;
    private final BigDecimal originalCostPerPerson;
    private final BigDecimal originalMenuTotalCost;

    // 3. 변경 전 끼니 총 비용
    private final BigDecimal beforeMealTotalCost;

    // 4. 선택된 단일 대체 메뉴 분석 결과
    private final MenuReplacementCandidateDto selectedReplacement;

    // 5. 복수 대체 메뉴 후보군 비교 목록 (절감액 순 정렬)
    private final List<MenuReplacementCandidateDto> candidateReplacements;

    // 6. 주간 식단 및 월간 예산 영향도
    private final WeeklyBudgetImpactDto weeklyImpact;

    // 7. 종합 분석 요약 문구
    private final String analysisSummary;

    /**
     * 주간 식단 및 월 예산에 미치는 영향 분석 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class WeeklyBudgetImpactDto {
        private final LocalDate weekStartDate;
        private final LocalDate weekEndDate;
        private final BigDecimal monthlyBudget;

        private final BigDecimal beforeWeeklyTotalCost;
        private final BigDecimal afterWeeklyTotalCost;
        private final BigDecimal weeklyTotalSavings;
        private final BigDecimal weeklySavingsRate; // 주간 비용 절감율 (%)

        private final BigDecimal beforeRemainingBudget;
        private final BigDecimal afterRemainingBudget;
        private final BigDecimal beforeUsageRate; // %
        private final BigDecimal afterUsageRate;  // %
        private final String budgetUsageRateChange; // 예: "45.2% -> 43.1%"

        private final int totalWeeklyMeals;
        private final int replacedOccurrences; // 주간 내 교체된 끼니 수
        private final int replacedMealCount;   // 주간 내 교체 적용된 총 식수 인원
    }
}
