package com.human.backend.price.dto.response;

import java.time.LocalDate;
import java.util.List;

public record YearOverYearBargainResponse(
        LocalDate asOfDate,
        LocalDate comparisonDate,
        int itemCount,
        List<YearOverYearBargainItem> items) {
}
