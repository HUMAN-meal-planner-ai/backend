package com.human.backend.automation.dto.response;

import com.human.backend.automation.entity.BudgetAlert;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [AUTO-002] 예산 초과 위험 경고 알림 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class BudgetAlertResponse {

    private final Long alertId;
    private final Long facilityId;
    private final String facilityName;
    private final String alertType;
    private final String riskLevel;
    private final boolean isRisk;

    private final LocalDate baseDate;
    private final String budgetMonth;

    private final BigDecimal monthlyBudget;
    private final BigDecimal currentSpentCost;
    private final BigDecimal monthlyRemainingBudget;

    private final BigDecimal thisWeekExpectedCost;
    private final BigDecimal nextWeekExpectedCost;
    private final BigDecimal twoWeeksTotalExpectedCost;

    private final BigDecimal projectedRemainingBudget;
    private final BigDecimal exceededAmount;

    private final String warningMessage;
    private final boolean isRead;
    private final LocalDateTime createdAt;
    private final LocalDateTime readAt;

    /**
     * Entity -> DTO 변환 팩토리 메서드
     */
    public static BudgetAlertResponse from(BudgetAlert alert) {
        if (alert == null) {
            return null;
        }
        return BudgetAlertResponse.builder()
                .alertId(alert.getAlertId())
                .facilityId(alert.getFacilityId())
                .facilityName(alert.getFacilityName())
                .alertType(alert.getAlertType())
                .riskLevel(alert.getRiskLevel())
                .isRisk(alert.isRisk())
                .baseDate(alert.getBaseDate())
                .budgetMonth(alert.getBudgetMonth())
                .monthlyBudget(alert.getMonthlyBudget())
                .currentSpentCost(alert.getCurrentSpentCost())
                .monthlyRemainingBudget(alert.getMonthlyRemainingBudget())
                .thisWeekExpectedCost(alert.getThisWeekExpectedCost())
                .nextWeekExpectedCost(alert.getNextWeekExpectedCost())
                .twoWeeksTotalExpectedCost(alert.getTwoWeeksTotalExpectedCost())
                .projectedRemainingBudget(alert.getProjectedRemainingBudget())
                .exceededAmount(alert.getExceededAmount())
                .warningMessage(alert.getWarningMessage())
                .isRead(alert.isRead())
                .createdAt(alert.getCreatedAt())
                .readAt(alert.getReadAt())
                .build();
    }
}
