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

    @Column(nullable = false, length = 80, updatable = false)
    private String variety;

    @Column(nullable = false, length = 40, updatable = false)
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

    public PriceSeries(Ingredient ingredient, String sourceName, String sourceItemCode,
            String variety, String grade, String priceType, String market, String region,
            String originalUnit, BigDecimal unitQuantity) {
        this.ingredient = ingredient;
        this.sourceName = sourceName;
        this.sourceItemCode = sourceItemCode;
        this.variety = variety;
        this.grade = grade;
        this.priceType = priceType;
        this.market = market;
        this.region = region;
        this.originalUnit = originalUnit;
        this.unitQuantity = unitQuantity;
        this.costBasis = false;
    }

    public Long getId() { return id; }
    public Ingredient getIngredient() { return ingredient; }
    public String getSourceName() { return sourceName; }
    public String getSourceItemCode() { return sourceItemCode; }
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
