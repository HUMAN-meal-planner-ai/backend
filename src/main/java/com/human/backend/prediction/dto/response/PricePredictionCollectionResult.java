package com.human.backend.prediction.dto.response;

public record PricePredictionCollectionResult(
        int requestedSeries,
        int receivedPredictions,
        int insertedPredictions,
        int duplicatesSkipped) {
}
