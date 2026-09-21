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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_series", schema = "mealfit")
public class PriceSeries {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "series_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false, updatable = false)
    private Ingredient ingredient;

    @Column(name = "source_name", nullable = false, length = 80, updatable = false)
    private String sourceName;

    @Column(name = "source_item_code", nullable = false, length = 80, updatable = false)
    private String sourceItemCode;

    @Column(name = "source_category_code", length = 80, updatable = false)
    private String sourceCategoryCode;

    @Column(name = "source_kind_code", length = 80, updatable = false)
    private String sourceKindCode;

    @Column(name = "source_rank_code", length = 80, updatable = false)
    private String sourceRankCode;

    @Column(nullable = false, length = 80)
    private String variety;

    @Column(nullable = false, length = 40)
    private String grade;

    @Column(name = "price_type", nullable = false, length = 20, updatable = false)
    private String priceType;

    @Column(nullable = false, length = 80, updatable = false)
    private String market;

    @Column(nullable = false, length = 80, updatable = false)
    private String region;

    @Column(name = "original_unit", nullable = false, length = 30, updatable = false)
    private String originalUnit;

    @Column(name = "unit_quantity", nullable = false, updatable = false)
    private BigDecimal unitQuantity;

    @Column(name = "is_cost_basis", nullable = false)
    private boolean costBasis;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected PriceSeries() {
    }

    public PriceSeries(Ingredient ingredient, String sourceName,
            String sourceCategoryCode, String sourceItemCode,
            String sourceKindCode, String sourceRankCode,
            String variety, String grade, String priceType, String market, String region,
            String originalUnit, BigDecimal unitQuantity) {
        this.ingredient = ingredient;
        this.sourceName = trim(sourceName);
        this.sourceCategoryCode = trimToNull(sourceCategoryCode);
        this.sourceItemCode = trim(sourceItemCode);
        this.sourceKindCode = trimToNull(sourceKindCode);
        this.sourceRankCode = trimToNull(sourceRankCode);
        this.variety = variety;
        this.grade = grade;
        this.priceType = priceType;
        this.market = market;
        this.region = region;
        this.originalUnit = originalUnit;
        this.unitQuantity = unitQuantity;
        this.costBasis = false;
    }

    @PrePersist
    private void normalizeSourceCodes() {
        sourceName = trim(sourceName);
        sourceCategoryCode = trimToNull(sourceCategoryCode);
        sourceItemCode = trim(sourceItemCode);
        sourceKindCode = trimToNull(sourceKindCode);
        sourceRankCode = trimToNull(sourceRankCode);
    }

    public void updateDisplayNames(String variety, String grade) {
        this.variety = trim(variety);
        this.grade = trim(grade);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    public Long getId() { return id; }
    public Ingredient getIngredient() { return ingredient; }
    public String getSourceName() { return sourceName; }
    public String getSourceCategoryCode() { return sourceCategoryCode; }
    public String getSourceItemCode() { return sourceItemCode; }
    public String getSourceKindCode() { return sourceKindCode; }
    public String getSourceRankCode() { return sourceRankCode; }
    public String getVariety() { return variety; }
    public String getGrade() { return grade; }
    public String getPriceType() { return priceType; }
    public String getMarket() { return market; }
    public String getRegion() { return region; }
    public String getOriginalUnit() { return originalUnit; }
    public BigDecimal getUnitQuantity() { return unitQuantity; }
    public boolean isCostBasis() { return costBasis; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
