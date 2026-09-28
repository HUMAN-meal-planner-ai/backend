package com.human.backend.price.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DailyPriceResponse(
        Long seriesId,
        String ingredientCode,
        String standardUnit,
        LocalDate startDate,
        LocalDate endDate,
        int observationDays,
        List<DailyPricePoint> prices) {
}
