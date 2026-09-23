package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AiPricePredictionResponse(
        Long seriesId,
        LocalDate baseDate,
        LocalDate targetDate,
        BigDecimal basePrice,
        BigDecimal predictedPrice,
        String standardUnit,
        String modelName,
        String modelVersion,
        Instant generatedAt) {
}
