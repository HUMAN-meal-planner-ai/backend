package com.human.backend.price.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kamis.collection")
public class KamisPriceCatalog {

    private String defaultRankCode = "04";
    private String defaultRankName = "상품";
    private Map<String, TargetProperties> targets = new LinkedHashMap<>();

    public List<KamisPriceTarget> findAllByIngredientCode(String ingredientCode) {
        if (ingredientCode == null) {
            return List.of();
        }
        return targets.values().stream()
                .map(this::toTarget)
                .filter(target -> target.ingredientCode().equalsIgnoreCase(ingredientCode))
                .toList();
    }

    public Map<String, TargetProperties> getTargets() { return targets; }
    public void setTargets(Map<String, TargetProperties> targets) { this.targets = targets; }
    public String getDefaultRankCode() { return defaultRankCode; }
    public void setDefaultRankCode(String defaultRankCode) { this.defaultRankCode = defaultRankCode; }
    public String getDefaultRankName() { return defaultRankName; }
    public void setDefaultRankName(String defaultRankName) { this.defaultRankName = defaultRankName; }

    @PostConstruct
    public void validateConfiguration() {
        Map<StorageIdentity, ConfiguredKind> identities = new LinkedHashMap<>();

        targets.forEach((targetName, properties) -> {
            KamisPriceTarget target = toTarget(properties);
            StorageIdentity identity = new StorageIdentity(
                    normalize(target.ingredientCode()),
                    normalize(target.itemCode()),
                    normalize(target.kindName()),
                    normalize(target.rankName()));
            ConfiguredKind previous = identities.putIfAbsent(
                    identity, new ConfiguredKind(targetName, target.kindCode()));

            if (previous != null
                    && !previous.kindCode().equalsIgnoreCase(target.kindCode())) {
                throw new IllegalStateException(
                        "KAMIS targets '" + previous.targetName() + "' and '" + targetName
                                + "' have different kind-code values but converge to the same "
                                + "price_series identity: ingredient-code=" + target.ingredientCode()
                                + ", item-code=" + target.itemCode()
                                + ", kind-name=" + target.kindName()
                                + ", rank-name=" + target.rankName());
            }
        });
    }

    private KamisPriceTarget toTarget(TargetProperties properties) {
        return new KamisPriceTarget(
                required(properties.ingredientCode, "ingredient-code"),
                required(properties.itemCategoryCode, "item-category-code"),
                required(properties.itemCode, "item-code"),
                required(properties.itemName, "item-name"),
                required(properties.kindCode, "kind-code"),
                required(properties.kindName, "kind-name"),
                valueOrDefault(properties.rankCode, defaultRankCode, "rank-code"),
                valueOrDefault(properties.rankName, defaultRankName, "rank-name"));
    }

    private String valueOrDefault(String value, String defaultValue, String propertyName) {
        return value == null || value.isBlank()
                ? required(defaultValue, "default-" + propertyName)
                : value.trim();
    }

    private String normalize(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
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
        private String rankCode;
        private String rankName;

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
        public String getRankCode() { return rankCode; }
        public void setRankCode(String rankCode) { this.rankCode = rankCode; }
        public String getRankName() { return rankName; }
        public void setRankName(String rankName) { this.rankName = rankName; }
    }

    private record StorageIdentity(
            String ingredientCode, String itemCode, String kindName, String rankName) {
    }

    private record ConfiguredKind(String targetName, String kindCode) {
    }
}
