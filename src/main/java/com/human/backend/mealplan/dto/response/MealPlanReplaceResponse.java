package com.human.backend.mealplan.dto.response;

/**
 * [식단 메뉴 교체 응답 DTO]
 * 식단 메뉴 교체 처리 결과 및 수정된 식단 항목 수를 반환합니다.
 */
public record MealPlanReplaceResponse(
        boolean success,
        int replacedCount,
        Long originalMenuId,
        Long replacementMenuId,
        String message
) {
}
