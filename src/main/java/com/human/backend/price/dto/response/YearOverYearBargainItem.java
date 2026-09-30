package com.human.backend.price.dto.response;

import java.math.BigDecimal;

public record YearOverYearBargainItem(
        Long seriesId,
        Long ingredientId,
        String ingredientName,
        String category,
        String variety,
        String grade,
        String unit,
        BigDecimal currentPrice,
        BigDecimal previousYearPrice,
        BigDecimal savingRatePercent) {
}
