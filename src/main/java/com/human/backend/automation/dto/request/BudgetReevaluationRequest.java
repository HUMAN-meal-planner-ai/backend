package com.human.backend.automation.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * [AUTO-002] 예산 초과 위험 재평가 실행 요청 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetReevaluationRequest {

    /**
     * 대상 시설 ID (미지정 시 기본 시설 적용)
     */
    private Long facilityId;

    /**
     * 재평가 기준 일자 (미지정 시 기본 기준일 적용)
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate baseDate;

    /**
     * 위험 감지 시 알림 자동 생성/저장 여부 (기본값: true)
     */
    @Builder.Default
    private Boolean autoSaveAlert = true;
}
