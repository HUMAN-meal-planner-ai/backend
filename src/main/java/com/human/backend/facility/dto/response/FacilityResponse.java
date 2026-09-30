package com.human.backend.facility.dto.response;

import java.math.BigDecimal;
import java.time.YearMonth;

import com.human.backend.facility.entity.Facility;

public record FacilityResponse(
        Long facilityId,
        String name,
        String facilityType,
        String address,
        String contactName,
        Integer defaultMealCount,
        String monthlyBudgetMonth,
        BigDecimal monthlyBudget,
        Integer breakfastMealCount,
        Integer lunchMealCount,
        Integer dinnerMealCount,
        BigDecimal targetFoodCost) {

    public static FacilityResponse from(Facility facility, YearMonth budgetMonth, BigDecimal monthlyBudget) {
        return new FacilityResponse(facility.getId(), facility.getName(), facility.getFacilityType(),
            facility.getAddress(), facility.getContactName(), facility.getDefaultMealCount(),
            budgetMonth == null ? null : budgetMonth.toString(),
            monthlyBudget,
            facility.getBreakfastMealCount(), facility.getLunchMealCount(),
            facility.getDinnerMealCount(), facility.getTargetFoodCost());
    }
}
