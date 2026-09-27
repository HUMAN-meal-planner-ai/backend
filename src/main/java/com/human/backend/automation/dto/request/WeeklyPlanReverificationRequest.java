package com.human.backend.automation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [AUTO-004] 재평가된 주간 식단 예산 위험 재확인 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyPlanReverificationRequest {

    /**
     * 대상 시설 ID (기본값: 1)
     */
    private Long facilityId;

    /**
     * 재평가 대상 주차 시작일 (월요일)
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate weekStartDate;

    /**
     * 분석 기준 일자 (미지정 시 주차 시작일 또는 오늘 날짜 적용)
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate baseDate;

    /**
     * 1회 식수 인원 (미지정 시 기본 1인 또는 기존 식단 설정 인원 적용)
     */
    private Integer mealCount;

    /**
     * 직접 입력/재구성된 식단 항목 목록 (선택 사항: 없을 경우 시스템에 저장된 재구성 식단 또는 기본 식단 자동 조회)
     */
    private List<ReconfiguredItemDto> reconfiguredItems;

    /**
     * 위험 해소 시 기존 경고 알림 자동 처리 여부
     */
    @Builder.Default
    private Boolean autoUpdateAlert = true;

    /**
     * 재구성된 식단 항목 DTO
     */
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconfiguredItemDto {
        private LocalDate mealDate;
        private Long menuId;
        private String menuName;
        private BigDecimal costPerPerson;
    }
}
