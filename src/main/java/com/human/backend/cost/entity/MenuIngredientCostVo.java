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
    private final String ingredientCategory;// 식재료 카테고리
    private final Boolean isPrimary;        // 주재료 여부
    private final BigDecimal quantity;      // ERD: menu_ingredient.quantity (1인 사용 중량, g)
    private final BigDecimal standardUnitPrice; // ERD: ingredient_price.standard_unit_price (최신 단가)
    private final LocalDate priceDate;      // 가격 수집 기준일
    private final String priceSource;       // 가격 출처(KAMIS, CSV 등)
    private final String mappingType;       // 직접/대체 매핑 유형(EXACT, VARIETY, RAW_PROXY, CATEGORY_PROXY)
    private final BigDecimal confidenceScore; // 대체 가격의 신뢰도(0~1)

    /**
     * DB 가격 매핑 정보까지 포함해 메뉴 원가 항목을 생성합니다.
     * 매핑 부가 정보는 화면에서 대체 가격 사용 여부를 구분할 때 사용합니다.
     */
    public MenuIngredientCostVo(Long ingredientId, String ingredientName, String ingredientCategory,
                                Boolean isPrimary, BigDecimal quantity, BigDecimal standardUnitPrice,
                                LocalDate priceDate, String priceSource, String mappingType,
                                BigDecimal confidenceScore) {
        this.ingredientId = ingredientId;
        this.ingredientName = ingredientName;
        this.ingredientCategory = ingredientCategory;
        this.isPrimary = isPrimary != null ? isPrimary : false;
        this.quantity = quantity != null ? quantity : BigDecimal.ZERO;
        this.standardUnitPrice = standardUnitPrice != null ? standardUnitPrice : BigDecimal.ZERO;
        this.priceDate = priceDate;
        this.priceSource = priceSource;
        this.mappingType = mappingType;
        this.confidenceScore = confidenceScore;
    }

    /**
     * 기존 CSV 데이터와 테스트 코드가 사용하는 호환 생성자입니다.
     * 가격 매핑 정보가 없는 데이터는 부가 필드를 null로 보관합니다.
     */
    public MenuIngredientCostVo(Long ingredientId, String ingredientName, String ingredientCategory,
                                Boolean isPrimary, BigDecimal quantity, BigDecimal standardUnitPrice,
                                LocalDate priceDate) {
        this(ingredientId, ingredientName, ingredientCategory, isPrimary, quantity,
                standardUnitPrice, priceDate, null, null, null);
    }

    public MenuIngredientCostVo(Long ingredientId, String ingredientName, BigDecimal quantity, BigDecimal standardUnitPrice, LocalDate priceDate) {
        this(ingredientId, ingredientName, null, false, quantity, standardUnitPrice, priceDate);
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
