package com.human.backend.price.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class KamisPriceValueParserTest {

    private final KamisPriceValueParser parser = new KamisPriceValueParser();

    @Test
    void parsesCommaSeparatedPrice() {
        assertEquals(new BigDecimal("12345.67"), parser.parsePrice(" 12,345.67 "));
    }

    @Test
    void rejectsNonNumericPrice() {
        assertThrows(IllegalArgumentException.class, () -> parser.parsePrice("-")).getMessage();
    }

    @Test
    void parsesKamisMonthDayWithYear() {
        assertEquals(LocalDate.of(2026, 9, 15), parser.parseDate("2026", "09/15"));
    }

    @Test
    void parsesIsoDate() {
        assertEquals(LocalDate.of(2026, 9, 15), parser.parseDate(null, "2026-09-15"));
    }
}
