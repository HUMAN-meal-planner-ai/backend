package com.human.backend.price.dto.response;

import java.time.LocalDate;
import java.util.List;

public record PriceCollectionResult(
        String ingredientCode,
        LocalDate startDate,
        LocalDate endDate,
        int targetCount,
        int targetsSucceeded,
        int targetsFailed,
        int fetchedRows,
        int outOfRangeRowsSkipped,
        int seriesCreated,
        int pricesInserted,
        int duplicatesSkipped,
        int invalidRowsSkipped,
        List<PriceTargetCollectionResult> targets) {
}
