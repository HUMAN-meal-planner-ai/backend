package com.human.backend.prediction.dto.response;

import java.util.List;

public record AiWeeklyPricePredictionBatchResponse(
        List<AiWeeklyPricePredictionResponse> predictions) {
}
