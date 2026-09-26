package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.human.backend.price.dto.response.DailyPriceResponse;
import com.human.backend.price.dto.response.PriceDataStatus;
import com.human.backend.price.dto.response.WeeklyPriceResponse;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

class PriceQueryServiceTest {

    private static final long SERIES_ID = 3L;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 9, 21);

    private final PriceSeriesRepository repository = mock(PriceSeriesRepository.class);
    private final PriceQueryService service = new PriceQueryService(repository);

    @Test
    void returnsDailyPricesForRequestedSeriesAndPeriod() {
        LocalDate startDate = LocalDate.of(2026, 9, 21);
        LocalDate endDate = LocalDate.of(2026, 9, 22);
        List<WeeklyRepresentativePrice> observations = List.of(
                price(startDate, "100"), price(endDate, "120"));
        when(repository.findDailyRepresentativePrices(SERIES_ID, startDate, endDate))
                .thenReturn(observations);

        DailyPriceResponse response = service.getDailyPrices(SERIES_ID, startDate, endDate);

        assertEquals(2, response.observationDays());
        assertEquals("F00993", response.ingredientCode());
        assertEquals(new BigDecimal("100"), response.prices().get(0).price());
        verify(repository).findDailyRepresentativePrices(SERIES_ID, startDate, endDate);
    }

    @Test
    void calculatesCompleteWeeklyAverageAndPreviousWeekChangeRate() {
        List<WeeklyRepresentativePrice> observations = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            observations.add(price(WEEK_START.minusWeeks(1).plusDays(day), "100"));
            observations.add(price(WEEK_START.plusDays(day), "110"));
        }
        when(repository.findDailyRepresentativePrices(
                SERIES_ID, WEEK_START.minusWeeks(1), WEEK_START.plusDays(6)))
                .thenReturn(observations);

        WeeklyPriceResponse response = service.getWeeklyPrice(SERIES_ID, WEEK_START);

        assertEquals(WEEK_START, response.weekStartDate());
        assertEquals(WEEK_START.plusDays(6), response.weekEndDate());
        assertEquals(7, response.observationDays());
        assertEquals(new BigDecimal("110.000000"), response.averagePrice());
        assertEquals(PriceDataStatus.COMPLETE, response.status());
        assertEquals(new BigDecimal("100.000000"), response.previousWeekAveragePrice());
        assertTrue(response.previousWeekComparable());
        assertEquals(new BigDecimal("10.000000"), response.weekOverWeekChangeRatePercent());
    }

    @Test
    void marksPartialWeekAndDoesNotReplaceMissingPreviousAverageWithZero() {
        List<WeeklyRepresentativePrice> observations = List.of(
                price(WEEK_START, "100"),
                price(WEEK_START.plusDays(2), "120"));
        when(repository.findDailyRepresentativePrices(
                SERIES_ID, WEEK_START.minusWeeks(1), WEEK_START.plusDays(6)))
                .thenReturn(observations);

        WeeklyPriceResponse response = service.getWeeklyPrice(SERIES_ID, WEEK_START);

        assertEquals(2, response.observationDays());
        assertEquals(new BigDecimal("110.000000"), response.averagePrice());
        assertEquals(PriceDataStatus.PARTIAL, response.status());
        assertNull(response.previousWeekAveragePrice());
        assertFalse(response.previousWeekComparable());
        assertNull(response.weekOverWeekChangeRatePercent());
    }

    @Test
    void doesNotCalculateChangeRateWhenPreviousAverageIsZero() {
        List<WeeklyRepresentativePrice> observations = List.of(
                price(WEEK_START.minusWeeks(1), "0"),
                price(WEEK_START, "100"));
        when(repository.findDailyRepresentativePrices(
                SERIES_ID, WEEK_START.minusWeeks(1), WEEK_START.plusDays(6)))
                .thenReturn(observations);

        WeeklyPriceResponse response = service.getWeeklyPrice(SERIES_ID, WEEK_START);

        assertEquals(BigDecimal.ZERO.setScale(6), response.previousWeekAveragePrice());
        assertFalse(response.previousWeekComparable());
        assertNull(response.weekOverWeekChangeRatePercent());
    }

    @Test
    void returnsNoDataWithoutInventingPrices() {
        when(repository.findDailyRepresentativePrices(
                SERIES_ID, WEEK_START.minusWeeks(1), WEEK_START.plusDays(6)))
                .thenReturn(List.of());

        WeeklyPriceResponse response = service.getWeeklyPrice(SERIES_ID, WEEK_START);

        assertEquals(0, response.observationDays());
        assertEquals(PriceDataStatus.NO_DATA, response.status());
        assertNull(response.averagePrice());
        assertNull(response.weekOverWeekChangeRatePercent());
    }

    private WeeklyRepresentativePrice price(LocalDate date, String value) {
        WeeklyRepresentativePrice price = mock(WeeklyRepresentativePrice.class);
        when(price.getSeriesId()).thenReturn(SERIES_ID);
        when(price.getIngredientCode()).thenReturn("F00993");
        when(price.getStandardUnit()).thenReturn("g");
        when(price.getPriceDate()).thenReturn(date);
        when(price.getRepresentativePrice()).thenReturn(new BigDecimal(value));
        return price;
    }
}
