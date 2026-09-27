package com.human.backend.prediction.dto.response;

import java.util.List;

public record AiPricePredictionBatchResponse(List<AiPricePredictionResponse> predictions) {
}
