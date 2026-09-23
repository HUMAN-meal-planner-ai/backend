package com.human.backend.prediction.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_prediction", schema = "mealfit")
public class PricePrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "prediction_id")
    private Long id;

    @Column(name = "series_id", nullable = false, updatable = false)
    private Long seriesId;

    @Column(name = "base_date", nullable = false, updatable = false)
    private LocalDate baseDate;

    @Column(name = "target_date", nullable = false, updatable = false)
    private LocalDate targetDate;

    @Column(name = "base_price", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal basePrice;

    @Column(name = "predicted_price", nullable = false, precision = 18, scale = 6, updatable = false)
    private BigDecimal predictedPrice;

    @Column(name = "model_name", nullable = false, length = 60, updatable = false)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 80, updatable = false)
    private String modelVersion;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    protected PricePrediction() {
    }

    public Long getId() { return id; }
    public Long getSeriesId() { return seriesId; }
    public LocalDate getBaseDate() { return baseDate; }
    public LocalDate getTargetDate() { return targetDate; }
    public BigDecimal getBasePrice() { return basePrice; }
    public BigDecimal getPredictedPrice() { return predictedPrice; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public Instant getGeneratedAt() { return generatedAt; }
}
