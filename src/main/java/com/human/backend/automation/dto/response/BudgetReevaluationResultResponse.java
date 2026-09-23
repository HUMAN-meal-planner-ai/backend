package com.human.backend.automation.dto.response;

import com.human.backend.cost.dto.response.BudgetRiskResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [AUTO-002] 예산 초과 위험 재평가 실행 결과 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class BudgetReevaluationResultResponse {

    private final Long facilityId;
    private final String facilityName;
    private final LocalDate evaluatedDate;
    private final String budgetMonth;

    private final BigDecimal monthlyBudget;
    private final BigDecimal monthlyRemainingBudget;
    private final BigDecimal thisWeekExpectedCost;
    private final BigDecimal nextWeekExpectedCost;
    private final BigDecimal twoWeeksTotalExpectedCost;
    private final BigDecimal exceededAmount;

    private final String riskLevel;
    private final boolean isRisk;
    private final boolean alertCreated;
    private final Long alertId;
    private final String warningMessage;
    private final LocalDateTime evaluatedAt;

    private final BudgetRiskResponse riskDetails;
}
