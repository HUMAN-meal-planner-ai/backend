package com.human.backend.price.dto.response;

import java.time.LocalDate;

public record PriceCollectionResult(
        String ingredientCode,
        String kamisItemCode,
        LocalDate startDate,
        LocalDate endDate,
        int fetchedRows,
        int outOfRangeRowsSkipped,
        int seriesCreated,
        int pricesInserted,
        int duplicatesSkipped,
        int invalidRowsSkipped) {
}
