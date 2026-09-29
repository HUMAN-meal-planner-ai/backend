package com.human.backend.price.entity;

import java.math.BigDecimal;
import java.time.Instant;

import com.human.backend.ingredient.entity.Ingredient;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ingredient_price_mapping", schema = "mealfit")
public class IngredientPriceMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private PriceSeries series;

    @Column(name = "mapping_type", nullable = false, length = 20)
    private String mappingType;

    @Column(name = "conversion_factor", nullable = false)
    private BigDecimal conversionFactor;

    @Column(name = "confidence_score", nullable = false)
    private BigDecimal confidenceScore;

    @Column(nullable = false)
    private int priority;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected IngredientPriceMapping() {}

    public void review(String status) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new IllegalArgumentException("검토 상태는 APPROVED 또는 REJECTED여야 합니다.");
        }
        this.reviewStatus = status;
        this.active = "APPROVED".equals(status);
    }

    public Long getId() { return id; }
    public Ingredient getIngredient() { return ingredient; }
    public PriceSeries getSeries() { return series; }
    public String getMappingType() { return mappingType; }
    public BigDecimal getConversionFactor() { return conversionFactor; }
    public BigDecimal getConfidenceScore() { return confidenceScore; }
    public int getPriority() { return priority; }
    public String getReviewStatus() { return reviewStatus; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

