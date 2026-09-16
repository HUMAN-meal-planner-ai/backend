package com.human.backend.facility.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "facility", schema = "mealfit")
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_id")
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "facility_type", nullable = false, length = 30)
    private String facilityType;

    @Column(length = 255)
    private String address;

    @Column(name = "contact_name", length = 50)
    private String contactName;

    @Column(name = "default_meal_count", nullable = false)
    private Integer defaultMealCount;

    @Column(name = "breakfast_meal_count")
    private Integer breakfastMealCount;

    @Column(name = "lunch_meal_count")
    private Integer lunchMealCount;

    @Column(name = "dinner_meal_count")
    private Integer dinnerMealCount;

    @Column(name = "target_food_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal targetFoodCost;

    // 생성/수정 시각은 PostgreSQL 기본값과 트리거가 관리합니다.
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    protected Facility() {
    }

    public Facility(String name, String facilityType, String address, String contactName,
            Integer defaultMealCount, Integer breakfastMealCount, Integer lunchMealCount,
            Integer dinnerMealCount, BigDecimal targetFoodCost) {
        update(name, facilityType, address, contactName, defaultMealCount,
            breakfastMealCount, lunchMealCount, dinnerMealCount, targetFoodCost);
    }

    public void update(String name, String facilityType, String address, String contactName,
            Integer defaultMealCount, Integer breakfastMealCount, Integer lunchMealCount,
            Integer dinnerMealCount, BigDecimal targetFoodCost) {
        this.name = name;
        this.facilityType = facilityType;
        this.address = address;
        this.contactName = contactName;
        this.defaultMealCount = defaultMealCount;
        this.breakfastMealCount = breakfastMealCount;
        this.lunchMealCount = lunchMealCount;
        this.dinnerMealCount = dinnerMealCount;
        this.targetFoodCost = targetFoodCost;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getFacilityType() { return facilityType; }
    public String getAddress() { return address; }
    public String getContactName() { return contactName; }
    public Integer getDefaultMealCount() { return defaultMealCount; }
    public Integer getBreakfastMealCount() { return breakfastMealCount; }
    public Integer getLunchMealCount() { return lunchMealCount; }
    public Integer getDinnerMealCount() { return dinnerMealCount; }
    public BigDecimal getTargetFoodCost() { return targetFoodCost; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
