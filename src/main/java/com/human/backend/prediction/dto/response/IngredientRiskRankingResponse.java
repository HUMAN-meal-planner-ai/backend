package com.human.backend.prediction.dto.response;

import java.util.List;

public record IngredientRiskRankingResponse(
        int requestedLimit,
        int rankedCount,
        int excludedSeriesCount,
        List<IngredientRiskRankItem> rankings) {
}
