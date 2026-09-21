package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [COST-014] 설정된 예산 대비 예상 사용액과 사용률 분석 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetUsageRateResponse {

    // 시설 식별자 및 시설명
    private Long facilityId;
    private String facilityName;

    // 분석 대상 연월 (예: "2026-09") 및 분석 기준일자
    private String yearMonth;
    private LocalDate baseDate;

    // 1. 설정된 월 배정 예산
    private BigDecimal monthlyBudget;

    // 2. 기준일 이전 기 집행(과거 누적) 식재료비
    private BigDecimal actualSpentCost;

    // 3. 기준일부터 월말까지의 향후 잔여 예상 사용액
    private BigDecimal projectedRemainingCost;

    // 4. [핵심] 월 총 예상 사용액 (기 집행액 + 잔여 예상액)
    private BigDecimal totalExpectedCost;

    // 5. 현재 시점 기 집행률 (%) = (기 집행액 / 월 예산) * 100
    private BigDecimal currentUsageRate;

    // 6. [핵심] 최종 예상 예산 사용률 (%) = (총 예상 사용액 / 월 예산) * 100
    private BigDecimal expectedUsageRate;

    // 7. 최종 예상 잔여 예산 (월 예산 - 총 예상 사용액)
    private BigDecimal remainingBudget;

    // 8. 예산 초과 여부 및 초과 금액
    private boolean isExceeded;
    private BigDecimal exceededAmount;

    // 9. 예산 관리 상태 (STABLE, CAUTION, WARNING, EXCEEDED)
    private String status;

    // 10. 직관적인 상태 요약 메시지
    private String statusMessage;
}
