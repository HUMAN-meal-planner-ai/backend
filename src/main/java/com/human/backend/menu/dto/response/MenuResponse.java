package com.human.backend.menu.dto.response;

import com.human.backend.menu.domain.MenuSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter 
@Builder
@AllArgsConstructor 
public class MenuResponse {
    private Long menuId;
    private String menuCode;
    private String menuName;
    private String mainCategory;
    private String subCategory;
    private MenuSlot slot;
    private Double weight;
    private Double energyKcal;
    private Double proteinG;
    private Double fatG;
    private Double carbohydrateG;
    private Double sodiumMg;
    private Integer foodCount;
    private List<IngredientResponse> ingredients;

    public MenuResponse(Long menuId, String menuCode, String menuName, String mainCategory, String subCategory,
                        MenuSlot slot, Double weight, Integer foodCount, List<IngredientResponse> ingredients) {
        this(menuId, menuCode, menuName, mainCategory, subCategory, slot, weight,
                null, null, null, null, null, foodCount, ingredients);
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class IngredientResponse {
        private Long ingredientId;
        private String ingredientName;
        private String ingredientCategory;
        private Boolean isPrimary;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private LocalDate priceDate;

        public IngredientResponse(Long ingredientId, String ingredientName, BigDecimal quantity, BigDecimal unitPrice, LocalDate priceDate) {
            this(ingredientId, ingredientName, null, false, quantity, unitPrice, priceDate);
        }
    }
}
