package com.human.backend.price.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class KamisPriceCatalogTest {

    @Test
    void returnsEveryTargetForOneIngredient() {
        KamisPriceCatalog catalog = catalog(Map.of(
                "domestic", target("00", "국산(1kg)", null, null),
                "china", target("02", "중국(1kg)", "05", "중품"),
                "peru", target("03", "페루(1kg)", "05", "중품")));

        List<KamisPriceTarget> targets = catalog.findAllByIngredientCode("f00426");

        assertEquals(3, targets.size());
    }

    @Test
    void usesTargetSpecificRankWhenConfigured() {
        KamisPriceCatalog catalog = catalog(Map.of(
                "china", target("02", "중국(1kg)", "05", "중품")));

        KamisPriceTarget target = catalog.findAllByIngredientCode("F00426").get(0);

        assertEquals("05", target.rankCode());
        assertEquals("중품", target.rankName());
    }

    @Test
    void fallsBackToDefaultRankWhenTargetRankIsMissing() {
        KamisPriceCatalog catalog = catalog(Map.of(
                "domestic", target("00", "국산(1kg)", null, "  ")));

        KamisPriceTarget target = catalog.findAllByIngredientCode("F00426").get(0);

        assertEquals("04", target.rankCode());
        assertEquals("상품", target.rankName());
    }

    @Test
    void rejectsDifferentKindCodesThatConvergeToSameStorageIdentity() {
        Map<String, KamisPriceCatalog.TargetProperties> targets = new LinkedHashMap<>();
        targets.put("first", target("00", "국산  (1kg)", "04", "상품"));
        targets.put("second", target("99", " 국산 (1kg) ", "04", "상품"));
        KamisPriceCatalog catalog = catalog(targets);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class, catalog::validateConfiguration);

        assertTrue(exception.getMessage().contains("different kind-code"));
        assertTrue(exception.getMessage().contains("first"));
        assertTrue(exception.getMessage().contains("second"));
    }

    private KamisPriceCatalog catalog(
            Map<String, KamisPriceCatalog.TargetProperties> targets) {
        KamisPriceCatalog catalog = new KamisPriceCatalog();
        catalog.setDefaultRankCode("04");
        catalog.setDefaultRankName("상품");
        catalog.setTargets(new LinkedHashMap<>(targets));
        return catalog;
    }

    private KamisPriceCatalog.TargetProperties target(
            String kindCode, String kindName, String rankCode, String rankName) {
        KamisPriceCatalog.TargetProperties target = new KamisPriceCatalog.TargetProperties();
        target.setIngredientCode("F00426");
        target.setItemCategoryCode("100");
        target.setItemCode("143");
        target.setItemName("녹두");
        target.setKindCode(kindCode);
        target.setKindName(kindName);
        target.setRankCode(rankCode);
        target.setRankName(rankName);
        return target;
    }
}
