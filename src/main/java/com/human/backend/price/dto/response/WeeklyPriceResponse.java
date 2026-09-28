package com.human.backend.price.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyPriceResponse(
        Long seriesId,
        String ingredientCode,
        String standardUnit,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        int observationDays,
        BigDecimal averagePrice,
        PriceDataStatus status,
        BigDecimal previousWeekAveragePrice,
        boolean previousWeekComparable,
        BigDecimal weekOverWeekChangeRatePercent) {
}
