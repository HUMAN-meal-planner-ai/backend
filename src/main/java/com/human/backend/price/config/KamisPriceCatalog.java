package com.human.backend.price.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kamis.collection")
public class KamisPriceCatalog {

    private String defaultRankCode = "04";
    private String defaultRankName = "상품";
    private Map<String, TargetProperties> targets = new LinkedHashMap<>();

    public Optional<KamisPriceTarget> findByIngredientCode(String ingredientCode) {
        if (ingredientCode == null) {
            return Optional.empty();
        }
        return targets.values().stream()
                .map(this::toTarget)
                .filter(target -> target.ingredientCode().equalsIgnoreCase(ingredientCode))
                .findFirst();
    }

    public Map<String, TargetProperties> getTargets() { return targets; }
    public void setTargets(Map<String, TargetProperties> targets) { this.targets = targets; }
    public String getDefaultRankCode() { return defaultRankCode; }
    public void setDefaultRankCode(String defaultRankCode) { this.defaultRankCode = defaultRankCode; }
    public String getDefaultRankName() { return defaultRankName; }
    public void setDefaultRankName(String defaultRankName) { this.defaultRankName = defaultRankName; }

    private KamisPriceTarget toTarget(TargetProperties properties) {
        return new KamisPriceTarget(
                required(properties.ingredientCode, "ingredient-code"),
                required(properties.itemCategoryCode, "item-category-code"),
                required(properties.itemCode, "item-code"),
                required(properties.itemName, "item-name"),
                required(properties.kindCode, "kind-code"),
                required(properties.kindName, "kind-name"),
                required(defaultRankCode, "default-rank-code"),
                required(defaultRankName, "default-rank-name"));
    }

    private String required(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("KAMIS 수집 설정이 누락되었습니다: " + propertyName);
        }
        return value.trim();
    }

    public static class TargetProperties {
        private String ingredientCode;
        private String itemCategoryCode;
        private String itemCode;
        private String itemName;
        private String kindCode;
        private String kindName;

        public String getIngredientCode() { return ingredientCode; }
        public void setIngredientCode(String ingredientCode) { this.ingredientCode = ingredientCode; }
        public String getItemCategoryCode() { return itemCategoryCode; }
        public void setItemCategoryCode(String itemCategoryCode) { this.itemCategoryCode = itemCategoryCode; }
        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public String getKindCode() { return kindCode; }
        public void setKindCode(String kindCode) { this.kindCode = kindCode; }
        public String getKindName() { return kindName; }
        public void setKindName(String kindName) { this.kindName = kindName; }
    }
}
