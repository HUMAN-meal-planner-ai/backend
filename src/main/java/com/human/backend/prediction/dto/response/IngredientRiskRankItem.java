package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record IngredientRiskRankItem(
        int rank,
        Long seriesId,
        String ingredientCode,
        String ingredientName,
        String standardUnit,
        LocalDate baseDate,
        LocalDate targetDate,
        BigDecimal basePrice,
        BigDecimal predictedPrice,
        BigDecimal predictedMaxPrice,
        BigDecimal expectedIncreaseRate,
        double ridgeScore,
        double volatilityScore,
        double combinedRiskScore,
        boolean risky) {
}
