package com.human.backend.facility.dto.response;

import java.math.BigDecimal;

import com.human.backend.facility.entity.Facility;

public record FacilityResponse(
        Long facilityId,
        String name,
        String facilityType,
        String address,
        String contactName,
        Integer defaultMealCount,
        Integer breakfastMealCount,
        Integer lunchMealCount,
        Integer dinnerMealCount,
        BigDecimal targetFoodCost) {

    public static FacilityResponse from(Facility facility) {
        return new FacilityResponse(facility.getId(), facility.getName(), facility.getFacilityType(),
            facility.getAddress(), facility.getContactName(), facility.getDefaultMealCount(),
            facility.getBreakfastMealCount(), facility.getLunchMealCount(),
            facility.getDinnerMealCount(), facility.getTargetFoodCost());
    }
}
