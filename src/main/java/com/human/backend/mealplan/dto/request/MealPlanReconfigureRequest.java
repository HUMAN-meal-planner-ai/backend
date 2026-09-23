package com.human.backend.mealplan.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MealPlanReconfigureRequest(
        @NotNull LocalDate weekStartDate,
        @Min(1) Integer mealCount,
        @NotNull BigDecimal targetCost
) {
}
