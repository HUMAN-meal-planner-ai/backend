package com.human.backend.facility.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 시설 기본 정보와 급식 운영 기준을 생성·수정할 때 공통으로 사용하는 요청입니다.
 * 기본 식수와 목표 식재료비는 필수이며, 끼니별 식수는 운영하지 않는 끼니를 고려해 null도 허용합니다.
 * 모든 숫자는 0 이상으로 제한해 음수 인원이나 음수 비용이 저장되지 않게 합니다.
 */
public record FacilityRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 30) String facilityType,
        @Size(max = 255) String address,
        @Size(max = 50) String contactName,
        @NotNull @Min(0) Integer defaultMealCount,
        @Min(0) Integer breakfastMealCount,
        @Min(0) Integer lunchMealCount,
        @Min(0) Integer dinnerMealCount,
        @NotNull @DecimalMin("0.00") BigDecimal targetFoodCost,
        @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal monthlyBudget) {
}
