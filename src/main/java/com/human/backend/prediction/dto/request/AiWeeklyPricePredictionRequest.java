package com.human.backend.prediction.dto.request;

import java.util.List;

public record AiWeeklyPricePredictionRequest(List<AiWeeklyPriceSeriesRequest> series) {
}
