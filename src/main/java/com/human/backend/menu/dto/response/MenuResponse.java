package com.human.backend.menu.dto.response;

import com.human.backend.menu.domain.MenuSlot;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter 
@AllArgsConstructor 
public class MenuResponse {
    private Long menuId;
    private String menuCode;
    private String menuName;
    private String mainCategory;
    private String subCategory;
    private MenuSlot slot;
    private Double weight;
    private Integer foodCount;
    private List<IngredientResponse> ingredients;

    @Getter
    @AllArgsConstructor
    public static class IngredientResponse {
        private Long ingredientId;
        private String ingredientName;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private LocalDate priceDate;
    }
}
