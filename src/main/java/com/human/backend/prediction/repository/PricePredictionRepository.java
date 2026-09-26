package com.human.backend.prediction.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.human.backend.prediction.entity.PricePrediction;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {

    interface StoredWeeklyPrediction {
        Long getSeriesId();
        String getIngredientCode();
        String getIngredientName();
        String getStandardUnit();
        LocalDate getBaseDate();
        LocalDate getTargetDate();
        BigDecimal getBasePrice();
        BigDecimal getPredictedPrice();
        String getModelName();
        String getModelVersion();
        Instant getGeneratedAt();
    }

    @Modifying
    @Query(value = """
            INSERT INTO mealfit.price_prediction (
                series_id, base_date, target_date, base_price, predicted_price,
                model_name, model_version, generated_at
            ) VALUES (
                :seriesId, :baseDate, :targetDate, :basePrice, :predictedPrice,
                :modelName, :modelVersion, :generatedAt
            )
            ON CONFLICT (series_id, base_date, target_date) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("seriesId") Long seriesId,
            @Param("baseDate") LocalDate baseDate,
            @Param("targetDate") LocalDate targetDate,
            @Param("basePrice") BigDecimal basePrice,
            @Param("predictedPrice") BigDecimal predictedPrice,
            @Param("modelName") String modelName,
            @Param("modelVersion") String modelVersion,
            @Param("generatedAt") Instant generatedAt);

    @Query(value = """
            SELECT
                prediction.series_id AS "seriesId",
                ingredient.ingredient_code AS "ingredientCode",
                ingredient.name AS "ingredientName",
                ingredient.standard_unit AS "standardUnit",
                prediction.base_date AS "baseDate",
                prediction.target_date AS "targetDate",
                prediction.base_price AS "basePrice",
                prediction.predicted_price AS "predictedPrice",
                prediction.model_name AS "modelName",
                prediction.model_version AS "modelVersion",
                prediction.generated_at AS "generatedAt"
            FROM mealfit.price_prediction prediction
            JOIN mealfit.price_series series
              ON series.series_id = prediction.series_id
            JOIN mealfit.ingredient ingredient
              ON ingredient.ingredient_id = series.ingredient_id
            WHERE prediction.series_id = :seriesId
              AND prediction.model_version = 'weekly_ridge_v1'
              AND prediction.target_date = prediction.base_date + 7
            ORDER BY prediction.base_date DESC,
                     prediction.generated_at DESC,
                     prediction.prediction_id DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<StoredWeeklyPrediction> findLatestWeeklyPrediction(
            @Param("seriesId") Long seriesId);

    @Query(value = """
            SELECT DISTINCT ON (prediction.series_id)
                prediction.series_id AS "seriesId",
                ingredient.ingredient_code AS "ingredientCode",
                ingredient.name AS "ingredientName",
                ingredient.standard_unit AS "standardUnit",
                prediction.base_date AS "baseDate",
                prediction.target_date AS "targetDate",
                prediction.base_price AS "basePrice",
                prediction.predicted_price AS "predictedPrice",
                prediction.model_name AS "modelName",
                prediction.model_version AS "modelVersion",
                prediction.generated_at AS "generatedAt"
            FROM mealfit.price_prediction prediction
            JOIN mealfit.price_series series
              ON series.series_id = prediction.series_id
            JOIN mealfit.ingredient ingredient
              ON ingredient.ingredient_id = series.ingredient_id
            WHERE prediction.series_id IN (:seriesIds)
              AND prediction.model_version = 'weekly_ridge_v1'
              AND prediction.target_date = prediction.base_date + 7
            ORDER BY prediction.series_id,
                     prediction.base_date DESC,
                     prediction.generated_at DESC,
                     prediction.prediction_id DESC
            """, nativeQuery = true)
    List<StoredWeeklyPrediction> findLatestWeeklyPredictions(
            @Param("seriesIds") List<Long> seriesIds);
}
