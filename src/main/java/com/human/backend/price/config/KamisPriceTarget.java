package com.human.backend.price.config;

public record KamisPriceTarget(
        String ingredientCode,
        String itemCategoryCode,
        String itemCode,
        String itemName,
        String kindCode,
        String kindName,
        String rankCode,
        String rankName) {
}
