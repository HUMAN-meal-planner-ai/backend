package com.human.backend.mealplan.dto.response;

import com.human.backend.menu.domain.MenuSlot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MealPlanResponse(
        LocalDate weekStartDate,
        Integer mealCount,
        BigDecimal totalCost,
        List<MealResponse> meals
) {
    public record MealResponse(
            LocalDate mealDate,
            MenuSlot slot,
            Long menuId,
            String menuName,
            BigDecimal costPerPerson
    ) {
    }
}
