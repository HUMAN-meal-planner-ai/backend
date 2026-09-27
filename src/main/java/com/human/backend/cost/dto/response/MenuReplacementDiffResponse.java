package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [BUDG-005 / COST 연동] 기존 메뉴와 대체 메뉴 간의 1인분 및 총 식수 원가 차이 분석 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuReplacementDiffResponse {

    private final Long originalMenuId;
    private final String originalMenuName;
    private final BigDecimal originalCostPerPerson;

    private final Long replacementMenuId;
    private final String replacementMenuName;
    private final BigDecimal replacementCostPerPerson;

    private final LocalDate targetDate;
    private final Integer mealCount;

    // 1인분 기준 차액 및 절감액
    // costDiffPerPerson: original - replacement (양수: 절감, 음수: 증가)
    private final BigDecimal costDiffPerPerson;
    private final BigDecimal savingsPerPerson;
    private final BigDecimal diffRate; // 변동률 (%)

    // 식수 기준 총액 및 절감액
    private final BigDecimal originalTotalCost;
    private final BigDecimal replacementTotalCost;
    private final BigDecimal totalSavings; // originalTotalCost - replacementTotalCost

    // 상태: SAVINGS (절감), INCREASED (원가 상승), UNCHANGED (동일)
    private final String savingsStatus;

    // 대체 메뉴의 가격 변동 위험도 정보 (MenuRiskService 연동)
    private final String replacementRiskLevel; // SAFE, CAUTION, WARNING
    private final boolean replacementIsRisk;
    private final BigDecimal replacementIncreaseRate;
}
