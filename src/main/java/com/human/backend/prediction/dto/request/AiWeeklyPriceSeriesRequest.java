package com.human.backend.prediction.dto.request;

import java.util.List;

public record AiWeeklyPriceSeriesRequest(
        Long seriesId,
        String ingredientCode,
        String standardUnit,
        List<AiWeeklyPriceHistoryPoint> prices) {
}
