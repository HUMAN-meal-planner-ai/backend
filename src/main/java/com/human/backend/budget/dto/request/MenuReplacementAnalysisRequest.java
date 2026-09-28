package com.human.backend.budget.dto.request;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * [BUDG-005] 대체 메뉴 적용 전후 예상 비용 차이 및 절감액 분석 요청 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuReplacementAnalysisRequest {

    /**
     * 시설 ID (기본값: 1)
     */
    @Builder.Default
    private Long facilityId = 1L;

    /**
     * 특정 식단 계획 ID (선택 사항: 식단 계획이 지정되면 해당 끼니의 식수 및 날짜, 끼니타입 자동 연동)
     */
    private Long planId;

    /**
     * 분석 대상 기준 일자 (미입력 시 planId의 날짜 또는 기본 기준일)
     */
    private LocalDate targetDate;

    /**
     * 적용 식수 인원 (미입력 시 planId의 식수 또는 1명)
     */
    private Integer mealCount;

    /**
     * 교체 대상 기존 메뉴 ID (필수)
     */
    private Long originalMenuId;

    /**
     * 대체 메뉴 ID (단일 대체 메뉴 시뮬레이션용)
     */
    private Long replacementMenuId;

    /**
     * 다중 대체 메뉴 후보군 ID 목록 (여러 대체 메뉴 비교 시뮬레이션용)
     */
    private List<Long> replacementMenuIds;

    /**
     * 주간 식단 내 해당 기존 메뉴의 모든 등장 끼니에 일괄 교체 적용 여부 (기본값: false)
     */
    @Builder.Default
    private Boolean applyToAllOccurrences = false;
}
