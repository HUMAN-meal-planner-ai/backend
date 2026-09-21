package com.human.backend.mealplan.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record MealPlanSaveRequest(
        @NotNull LocalDate weekStartDate,
        @Min(1) Integer mealCount,
        @NotEmpty List<@Valid MealPlanItemRequest> meals
) {
}
