package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 저장된 주간 예측과 현재 모델의 위험 점수를 Frontend에 함께 제공한다.
 * expectedIncreaseRate는 백분율이 아닌 비율이며, 계산할 수 없으면 null이다.
 */
public record WeeklyPricePredictionRiskResponse(
        Long seriesId,
        String ingredientCode,
        String ingredientName,
        String standardUnit,
        LocalDate baseDate,
        LocalDate targetDate,
        BigDecimal basePrice,
        BigDecimal predictedPrice,
        BigDecimal expectedIncreaseRate,
        Double combinedRiskScore,
        double riskThreshold,
        boolean risky,
        String modelName,
        String modelVersion,
        Instant generatedAt) {
}
