package com.human.backend.budget.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [BUDG-003] 주간 식단 고비용 기여 메뉴 식별 및 변경 검토 후보 요청 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyHighCostMenuRequest {

    /**
     * 시설 ID (기본값: 1)
     */
    private Long facilityId;

    /**
     * 대상 주차 시작일 (월요일 기준, 미지정 시 기본 기준일의 해당 주 월요일)
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate weekStartDate;

    /**
     * 반환할 상위 변경 검토 후보 메뉴 개수 (기본값: 5)
     */
    private Integer topN;

    /**
     * 최소 비용 기여율 임계값 (%, 예: 10.0 입력 시 10% 이상 기여하는 메뉴만 필터링)
     */
    private BigDecimal minContributionRate;
}
