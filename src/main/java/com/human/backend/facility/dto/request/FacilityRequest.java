package com.human.backend.facility.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FacilityRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String facilityType,
        @Size(max = 255) String address,
        @Size(max = 50) String contactName,
        @NotNull @Min(0) Integer defaultMealCount,
        @Min(0) Integer breakfastMealCount,
        @Min(0) Integer lunchMealCount,
        @Min(0) Integer dinnerMealCount,
        @NotNull @DecimalMin("0.00") BigDecimal targetFoodCost) {
}
