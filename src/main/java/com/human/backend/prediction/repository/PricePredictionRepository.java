package com.human.backend.prediction.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.human.backend.prediction.entity.PricePrediction;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {

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
}
