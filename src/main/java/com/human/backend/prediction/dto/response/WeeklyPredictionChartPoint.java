package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeeklyPredictionChartPoint(
        LocalDate baseDate,
        LocalDate targetDate,
        BigDecimal basePrice,
        BigDecimal predictedPrice,
        String predictionMeaning) {
}
