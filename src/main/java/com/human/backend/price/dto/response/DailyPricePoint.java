package com.human.backend.price.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPricePoint(
        LocalDate priceDate,
        BigDecimal price) {
}
