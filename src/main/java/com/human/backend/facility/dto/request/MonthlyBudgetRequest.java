package com.human.backend.facility.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record MonthlyBudgetRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal budgetAmount) {
}