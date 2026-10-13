package com.human.backend.mealplan.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * [식단 메뉴 교체 요청 DTO]
 * 주간 식단 내 특정 메뉴를 추천된 대체 메뉴로 교체 반영하기 위한 요청 정보입니다.
 */
public record MealPlanReplaceRequest(
        @NotNull(message = "교체 대상 기존 메뉴 ID는 필수입니다.")
        Long originalMenuId,

        @NotNull(message = "대체할 신규 메뉴 ID는 필수입니다.")
        Long replacementMenuId,

        Long facilityId,

        Long planId,

        LocalDate targetDate,

        LocalDate weekStartDate,

        Boolean applyToAllOccurrences
) {
    /**
     * 특정 끼니(planId) 단건 교체인지 판정
     */
    public boolean isSinglePlanReplacement() {
        return planId != null && !Boolean.TRUE.equals(applyToAllOccurrences);
    }

    /**
     * 주간 식단 내 동일 메뉴 일괄 교체인지 판정
     */
    public boolean isBulkReplacement() {
        return Boolean.TRUE.equals(applyToAllOccurrences);
    }

    /**
     * 주차 시작일(월요일) 계산
     */
    public LocalDate resolveWeekStartDate() {
        if (weekStartDate != null) {
            return weekStartDate;
        }
        if (targetDate != null) {
            return targetDate.with(DayOfWeek.MONDAY);
        }
        return null;
    }

    /**
     * 주차 종료일(일요일) 계산
     */
    public LocalDate resolveWeekEndDate() {
        LocalDate start = resolveWeekStartDate();
        return start != null ? start.plusDays(6) : null;
    }
}
