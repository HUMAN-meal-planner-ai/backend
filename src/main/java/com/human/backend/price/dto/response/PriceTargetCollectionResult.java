package com.human.backend.price.dto.response;

public record PriceTargetCollectionResult(
        Long seriesId,
        String kamisCategoryCode,
        String kamisItemCode,
        String kamisKindCode,
        String kamisKindName,
        String kamisRankCode,
        String kamisRankName,
        boolean success,
        String errorMessage,
        int fetchedRows,
        int outOfRangeRowsSkipped,
        int seriesCreated,
        int pricesInserted,
        int duplicatesSkipped,
        int invalidRowsSkipped) {
}
