package com.human.backend.prediction.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PricePredictionChartResponse(
        Long seriesId,
        String ingredientCode,
        String ingredientName,
        String standardUnit,
        LocalDate actualStartDate,
        LocalDate actualEndDate,
        List<ActualPriceChartPoint> actualPrices,
        WeeklyPredictionChartPoint weeklyPrediction) {
}
