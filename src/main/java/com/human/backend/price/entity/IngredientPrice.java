package com.human.backend.price.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

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
@Table(name = "ingredient_price", schema = "mealfit")
public class IngredientPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "price_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false, updatable = false)
    private PriceSeries series;

    @Column(name = "price_date", nullable = false, updatable = false)
    private LocalDate priceDate;

    @Column(name = "original_price", nullable = false, updatable = false)
    private BigDecimal originalPrice;

    @Column(name = "unit_quantity", nullable = false, updatable = false)
    private BigDecimal unitQuantity;

    // PostgreSQL generated column: original_price / unit_quantity
    @Column(name = "standard_unit_price", insertable = false, updatable = false)
    private BigDecimal standardUnitPrice;

    @Column(name = "collected_at", insertable = false, updatable = false)
    private Instant collectedAt;

    protected IngredientPrice() {
    }

    public IngredientPrice(PriceSeries series, LocalDate priceDate, BigDecimal originalPrice,
            BigDecimal unitQuantity) {
        this.series = series;
        this.priceDate = priceDate;
        this.originalPrice = originalPrice;
        this.unitQuantity = unitQuantity;
    }

    public Long getId() { return id; }
    public PriceSeries getSeries() { return series; }
    public LocalDate getPriceDate() { return priceDate; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getUnitQuantity() { return unitQuantity; }
    public BigDecimal getStandardUnitPrice() { return standardUnitPrice; }
    public Instant getCollectedAt() { return collectedAt; }
}
