package com.human.backend.admin.dto.response;

import java.math.BigDecimal;

import com.human.backend.price.entity.IngredientPriceMapping;

public record AdminPriceMappingResponse(
        Long mappingId,
        String ingredientCode,
        String ingredientName,
        Long seriesId,
        String sourceName,
        String variety,
        String grade,
        String mappingType,
        BigDecimal conversionFactor,
        BigDecimal confidenceScore,
        int priority,
        String reviewStatus,
        boolean active) {

    public static AdminPriceMappingResponse from(IngredientPriceMapping mapping) {
        return new AdminPriceMappingResponse(
                mapping.getId(),
                mapping.getIngredient().getIngredientCode(),
                mapping.getIngredient().getName(),
                mapping.getSeries().getId(),
                mapping.getSeries().getSourceName(),
                mapping.getSeries().getVariety(),
                mapping.getSeries().getGrade(),
                mapping.getMappingType(),
                mapping.getConversionFactor(),
                mapping.getConfidenceScore(),
                mapping.getPriority(),
                mapping.getReviewStatus(),
                mapping.isActive());
    }
}

