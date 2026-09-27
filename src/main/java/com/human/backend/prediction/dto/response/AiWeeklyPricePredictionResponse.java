package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AiWeeklyPricePredictionResponse(
        Long seriesId,
        LocalDate baseDate,
        LocalDate targetDate,
        BigDecimal basePrice,
        BigDecimal predictedPrice,
        BigDecimal predictedMaxPrice,
        String standardUnit,
        Double ridgeScore,
        Double volatilityScore,
        Double combinedRiskScore,
        String modelName,
        String modelVersion,
        Instant generatedAt) {

    public AiPricePredictionResponse toStorageResponse() {
        return new AiPricePredictionResponse(
                seriesId,
                baseDate,
                targetDate,
                basePrice,
                predictedPrice,
                standardUnit,
                modelName,
                modelVersion,
                generatedAt);
    }
}
