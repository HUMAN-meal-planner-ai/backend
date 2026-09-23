package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [COST-012] 선택 주차 7일 식단의 최신·예측 단가 기준 총 예상 식재료비 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyMealPlanCostResponse {

    // 시설 식별자 및 명칭
    private Long facilityId;
    private String facilityName;

    // 선택 주차 기간 (7일: 시작일 ~ 종료일)
    private LocalDate startDate;
    private LocalDate endDate;

    // 7일간 총 예상 식재료비 합계
    private BigDecimal totalExpectedCost;

    // 7일간 총 식수 인원 합계
    private Integer totalMealCount;

    // 7일간 1인 1식 평균 예상 단가
    private BigDecimal averageCostPerPerson;

    // 7일간 일자별 식단 비용 요약 및 상세 목록
    private List<DailyCostDetail> dailyCosts;

    /**
     * 일자별 식재료비 요약 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCostDetail {
        private LocalDate date;
        private String dayOfWeek;               // 요일 (예: "월요일", "MONDAY")
        private BigDecimal dailyTotalCost;      // 일별 총 예상 식재료비
        private Integer dailyMealCount;         // 일별 총 식수 인원
        private List<MealPlanDetail> meals;     // 해당 일자의 끼니별 상세 목록
    }

    /**
     * 끼니별(식단별) 원가 상세 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MealPlanDetail {
        private Long planId;
        private String mealType;                // 조식/중식/석식 등
        private Integer mealCount;              // 끼니별 식수 인원
        private BigDecimal costPerPerson;       // 1인분 예상 단가 (최신 시세/예측 단가 반영)
        private BigDecimal totalMealCost;       // 끼니별 총 식재료비 (1인분 단가 * 식수 인원)
    }
}
