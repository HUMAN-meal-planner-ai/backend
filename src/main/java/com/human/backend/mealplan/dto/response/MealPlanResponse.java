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
                Long planId,
                LocalDate mealDate,
                String mealType,
                MenuSlot slot,
                Long menuId,
                String menuName,
                BigDecimal costPerPerson,
                List<MealMenuItemResponse> menuItems
    ) {
        public MealResponse(
                Long planId,
                LocalDate mealDate,
                String mealType,
                MenuSlot slot,
                Long menuId,
                String menuName,
                BigDecimal costPerPerson
        ) {
            this(
                        planId,
                        mealDate,
                        mealType,
                        slot,
                        menuId,
                        menuName,
                        costPerPerson,
                        menuId != null ? List.of(new MealMenuItemResponse(menuId, menuName)) : List.of()
            );
        }
    }

    public record MealMenuItemResponse(
            Long menuId,
            String menuName
    ) {
    }
}
