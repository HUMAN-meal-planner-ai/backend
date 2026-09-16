package com.human.backend.cost.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 가격을 보내는 응답상자 
public class MenuCostResponse {

    private Long menuId;
    private String menuName;
    private BigDecimal totalCost; // 메뉴 1인분 총 현재 원가
    private List<IngredientDetail> details;

    // 생성자
    public MenuCostResponse(Long menuId, String menuName, BigDecimal totalCost, List<IngredientDetail> details) {
        this.menuId = menuId;
        this.menuName = menuName;
        this.totalCost = totalCost;
        this.details = details;
    }

    // static 정적 중첩 클래스(원활한 관리를 위해 식재료 정보를 DTO 내부에 작성)
    // 독립된 클래스로서 안전하게 json 변환이 가능
    public static class IngredientDetail {
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal quantity;          // 사용량 (g)
        private BigDecimal standardUnitPrice; // 1g당 단가
        private BigDecimal lineCost;          // quantity * standardUnitPrice
        private LocalDate priceDate;          // 단가 기준일


        // 식재료 정보 생성자 
        public IngredientDetail(Long ingredientId, String ingredientName, BigDecimal quantity, BigDecimal standardUnitPrice, BigDecimal lineCost, LocalDate priceDate) {
            this.ingredientId = ingredientId;
            this.ingredientName = ingredientName;
            this.quantity = quantity;
            this.standardUnitPrice = standardUnitPrice;
            this.lineCost = lineCost;
            this.priceDate = priceDate;
        }

        // 식재료 정보 getter
        public Long getIngredientId() { return ingredientId; }
        public String getIngredientName() { return ingredientName; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getStandardUnitPrice() { return standardUnitPrice; }
        public BigDecimal getLineCost() { return lineCost; }
        public LocalDate getPriceDate() { return priceDate; }
    }

    // dto getter
    public Long getMenuId() { return menuId; }
    public String getMenuName() { return menuName; }
    public BigDecimal getTotalCost() { return totalCost; }
    public List<IngredientDetail> getDetails() { return details; }
}
