package com.human.backend.facility.dto.response;

import java.math.BigDecimal;

public record MonthlyBudgetResponse(String month, BigDecimal budgetAmount, BigDecimal executedAmount) {
}