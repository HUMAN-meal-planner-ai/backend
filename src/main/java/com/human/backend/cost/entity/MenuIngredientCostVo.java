package com.human.backend.cost.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

public class MenuIngredientCostVo { // 메뉴 식재료의 vo
    private Long ingredientId; // 기본키
    private String ingredientName; // 재료이름(식재료명 테이블에서 가져온다.)
    private BigDecimal quantity;          // ERD: menu_ingredient.quantity (1인 사용 중량, g)
    private BigDecimal standardUnitPrice; // ERD: ingredient_price.standard_unit_price (최신 단가)
    private LocalDate priceDate;          // 가격 수집 기준일

    // 생성자
    public MenuIngredientCostVo(Long ingredientId, String ingredientName, BigDecimal quantity, BigDecimal standardUnitPrice, LocalDate priceDate) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.quantity = quantity;
        this.standardUnitPrice = standardUnitPrice;
        this.priceDate = priceDate;
    }

    // getter
    public Long getIngredientId() { return ingredientId; }
    public String getIngredientName() { return ingredientName; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getStandardUnitPrice() { return standardUnitPrice; }
    public LocalDate getPriceDate() { return priceDate; }
}
