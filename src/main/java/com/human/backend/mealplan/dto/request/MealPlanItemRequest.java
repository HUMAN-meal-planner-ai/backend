package com.human.backend.mealplan.dto.request;

import com.human.backend.menu.domain.MenuSlot;

import java.time.LocalDate;

public record MealPlanItemRequest(
        LocalDate mealDate,
        MenuSlot slot,
        Long menuId
) {
}
