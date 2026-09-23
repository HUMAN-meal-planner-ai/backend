package com.human.backend.integration.priceapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class KamisRegionTest {

    @Test
    void resolvesSupportedRegionCodes() {
        assertEquals("1101", KamisRegion.fromSeriesRegion("서울").countryCode());
        assertEquals("2100", KamisRegion.fromSeriesRegion("부산").countryCode());
        assertEquals("2501", KamisRegion.fromSeriesRegion("대전").countryCode());
    }

    @Test
    void rejectsUnsupportedRegion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> KamisRegion.fromSeriesRegion("광주"));
    }
}
