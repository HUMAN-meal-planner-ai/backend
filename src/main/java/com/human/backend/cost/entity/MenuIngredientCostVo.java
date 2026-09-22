package com.human.backend.cost.entity;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

// 메뉴 식재료의 VO (Value Object)
// 데이터와 함께 '식재료 1인분 원가 계산' 책임을 스스로 수행하여 높은 응집도를 가집니다.
@Getter
public class MenuIngredientCostVo { 
    private final Long ingredientId;        // 기본키
    private final String ingredientName;    // 재료이름(식재료명 테이블에서 가져온다.)
    private final BigDecimal quantity;      // ERD: menu_ingredient.quantity (1인 사용 중량, g)
    private final BigDecimal standardUnitPrice; // ERD: ingredient_price.standard_unit_price (최신 단가)
    private final LocalDate priceDate;      // 가격 수집 기준일

    // 생성자 (불변 객체로 생성)
    public MenuIngredientCostVo(Long ingredientId, String ingredientName, BigDecimal quantity, BigDecimal standardUnitPrice, LocalDate priceDate) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.quantity = quantity != null ? quantity : BigDecimal.ZERO;
        this.standardUnitPrice = standardUnitPrice != null ? standardUnitPrice : BigDecimal.ZERO;
        this.priceDate = priceDate;
    }

    /**
     * 식재료 1인분 원가 계산 (사용량 * 최신 단가)
     * [응집도 향상]: 자신의 데이터(quantity, standardUnitPrice)를 활용한 계산을 스스로 수행(정보 전문가 패턴)
     * 
     * @return 1인분 재료 원가 (소수점 첫째자리에서 반올림하여 정수 단위 원으로 산출)
     */
    public BigDecimal calculateLineCost() {
        return this.quantity.multiply(this.standardUnitPrice).setScale(0, RoundingMode.HALF_UP);
    }
}
