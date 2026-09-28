package com.human.backend.mealplan.dto.request;

import com.human.backend.menu.domain.MenuSlot;

import java.time.LocalDate;

public record MealPlanItemRequest(
        LocalDate mealDate,
        String mealType,
        MenuSlot slot,
        Long menuId
) {
}
