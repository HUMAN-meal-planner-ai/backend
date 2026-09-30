package com.human.backend.facility.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.human.backend.facility.entity.Facility;

public final class MonthlyBudgetCalculator {

    private MonthlyBudgetCalculator() {
    }

    public static BigDecimal calculate(Facility facility, Integer totalMealCount) {
        if (facility.getTargetFoodCost() == null
                || totalMealCount == null) {
            throw new IllegalArgumentException("1인 목표 식재료비와 해당 월 식수 합계가 필요합니다.");
        }

        return facility.getTargetFoodCost()
                .multiply(BigDecimal.valueOf(totalMealCount))
                .setScale(2, RoundingMode.HALF_UP);
    }
}