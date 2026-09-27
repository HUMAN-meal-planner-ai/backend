package com.human.backend.budget.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [BUDG-003] 주간 식단 내 특정 메뉴가 제공된 끼니별 세부 내역 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServedMealDetail {

    private Long planId;
    private LocalDate planDate;
    private String dayOfWeek;         // "월요일", "화요일" 등
    private String mealType;          // "LUNCH", "DINNER" 등
    private Integer mealCount;        // 해당 끼니 식수 인원
    private BigDecimal costPerPerson; // 해당 일자 메뉴 1인분 예상 단가
    private BigDecimal mealTotalCost; // 해당 끼니에서 발생한 총 비용 (costPerPerson * mealCount)
}
