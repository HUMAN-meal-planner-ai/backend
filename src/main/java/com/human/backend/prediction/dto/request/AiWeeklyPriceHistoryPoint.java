package com.human.backend.prediction.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AiWeeklyPriceHistoryPoint(
        LocalDate priceDate,
        BigDecimal representativePrice) {
}
