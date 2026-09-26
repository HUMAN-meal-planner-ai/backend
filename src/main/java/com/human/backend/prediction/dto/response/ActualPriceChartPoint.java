package com.human.backend.prediction.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ActualPriceChartPoint(
        LocalDate priceDate,
        BigDecimal price) {
}
