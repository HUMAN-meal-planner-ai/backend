package com.human.backend.automation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [AUTO-006] 주간 재평가 메뉴 변경 검토 후보 탐지 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuReplacementCandidateRequest {

    /**
     * 대상 시설 ID (기본값: 1)
     */
    private Long facilityId;

    /**
     * 주간 재평가 대상 주차 시작일 (월요일)
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate weekStartDate;

    /**
     * 분석/예측 기준 일자
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate targetDate;

    /**
     * 1회 식수 인원 (기본값: 1)
     */
    private Integer mealCount;

    /**
     * 목표 1인분 단가 (미지정 시 기본 2,500원 기준 적용)
     */
    private BigDecimal targetCost;

    /**
     * 원가 급등 판정 임계 상승률(%) (기본값: 10.0%)
     */
    @Builder.Default
    private BigDecimal surgeThresholdRate = new BigDecimal("10.0");
}
