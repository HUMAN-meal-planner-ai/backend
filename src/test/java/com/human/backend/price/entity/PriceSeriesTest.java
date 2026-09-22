package com.human.backend.price.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.human.backend.ingredient.entity.Ingredient;

class PriceSeriesTest {

    @Test
    void trimsSourceCodesBeforePersistence() {
        PriceSeries series = new PriceSeries(
                mock(Ingredient.class), " KAMIS ", " 200 ", " 212 ",
                " 00 ", " 04 ", "양배추(1kg)", "상품",
                "WHOLESALE", "가락도매", "서울", "kg", BigDecimal.ONE);

        assertEquals("KAMIS", series.getSourceName());
        assertEquals("200", series.getSourceCategoryCode());
        assertEquals("212", series.getSourceItemCode());
        assertEquals("00", series.getSourceKindCode());
        assertEquals("04", series.getSourceRankCode());
    }

    @Test
    void allowsKamisDisplayNamesToBeRefreshed() {
        PriceSeries series = new PriceSeries(
                mock(Ingredient.class), "KAMIS", "200", "212", "00", "04",
                "old variety", "old grade", "WHOLESALE",
                "가락도매", "서울", "kg", BigDecimal.ONE);

        series.updateDisplayNames(" 양배추(1kg) ", " 상품 ");

        assertEquals("양배추(1kg)", series.getVariety());
        assertEquals("상품", series.getGrade());
    }
}
