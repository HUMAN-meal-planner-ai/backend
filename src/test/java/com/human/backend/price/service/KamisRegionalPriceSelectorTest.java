package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.util.KamisPriceValueParser;

class KamisRegionalPriceSelectorTest {

    private final KamisRegionalPriceSelector selector =
            new KamisRegionalPriceSelector(new KamisPriceValueParser());

    @Test
    void selectsOnlySeriesRegion() {
        PriceSeries series = series("부산");

        List<KamisPriceItemDto> selected = selector.select(series, List.of(
                item("서울", "가락도매", "1,300"),
                item("부산", "엄궁도매", "1,200"),
                item("대전", "오정도매", "1,250")));

        assertEquals(1, selected.size());
        assertEquals("부산", selected.get(0).countyName());
        assertEquals("1,200", selected.get(0).price());
    }

    @Test
    void normalizesSameDateAndSamePriceToOneRow() {
        PriceSeries series = series("부산");

        List<KamisPriceItemDto> selected = selector.select(series, List.of(
                item("부산", "남포동건어물", "1,200"),
                item("부산", "엄궁도매", "1,200")));

        assertEquals(1, selected.size());
    }

    @Test
    void rejectsDifferentPricesForSameRegionAndDate() {
        PriceSeries series = series("부산");

        assertThrows(IllegalStateException.class, () -> selector.select(series, List.of(
                item("부산", "남포동건어물", "1,200"),
                item("부산", "엄궁도매", "1,250"))));
    }

    @Test
    void selectsValidPriceWhenDuplicateContainsMissingPrice() {
        PriceSeries series = series("부산");

        List<KamisPriceItemDto> selected = selector.select(series, List.of(
                item("부산", "남포동건어물", "-"),
                item("부산", "엄궁도매", "1,200")));

        assertEquals(1, selected.size());
        assertEquals("1,200", selected.get(0).price());
    }

    private PriceSeries series(String region) {
        PriceSeries series = mock(PriceSeries.class);
        when(series.getId()).thenReturn(77L);
        when(series.getRegion()).thenReturn(region);
        return series;
    }

    private KamisPriceItemDto item(String region, String market, String price) {
        return new KamisPriceItemDto(
                "양배추", "양배추(1kg)", region, market, "2026", "09/15", price);
    }
}
