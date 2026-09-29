package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.price.dto.response.YearOverYearBargainResponse;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;

class YearOverYearBargainServiceTest {

    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 9, 28);
    private static final LocalDate PREVIOUS_DATE = LocalDate.of(2025, 9, 26);

    private final IngredientPriceRepository repository = mock(IngredientPriceRepository.class);
    private final YearOverYearBargainService service = new YearOverYearBargainService(repository);

    @Test
    void returnsOnlyCheaperItemsOrderedBySavingRate() {
        when(repository.findLatestPriceDate()).thenReturn(CURRENT_DATE);
        when(repository.findLatestPriceDateBetween(
                CURRENT_DATE.minusYears(1).minusDays(14), CURRENT_DATE.minusYears(1)))
                .thenReturn(PREVIOUS_DATE);
        List<IngredientPrice> prices = List.of(
                price(1L, "감자", CURRENT_DATE, "700"),
                price(1L, "감자", PREVIOUS_DATE, "1000"),
                price(2L, "사과", CURRENT_DATE, "900"),
                price(2L, "사과", PREVIOUS_DATE, "1000"),
                price(3L, "대파", CURRENT_DATE, "1100"),
                price(3L, "대파", PREVIOUS_DATE, "1000"));
        when(repository.findAllByPriceDateInOrderByIdDesc(List.of(CURRENT_DATE, PREVIOUS_DATE)))
                .thenReturn(prices);

        YearOverYearBargainResponse response = service.getBargains(4);

        assertEquals(CURRENT_DATE, response.asOfDate());
        assertEquals(PREVIOUS_DATE, response.comparisonDate());
        assertEquals(2, response.itemCount());
        assertEquals("감자", response.items().get(0).ingredientName());
        assertEquals(new BigDecimal("30.0"), response.items().get(0).savingRatePercent());
        assertEquals("사과", response.items().get(1).ingredientName());
    }

    @Test
    void returnsEmptyResponseWhenPreviousYearDataDoesNotExist() {
        when(repository.findLatestPriceDate()).thenReturn(CURRENT_DATE);
        when(repository.findLatestPriceDateBetween(
                CURRENT_DATE.minusYears(1).minusDays(14), CURRENT_DATE.minusYears(1))).thenReturn(null);

        YearOverYearBargainResponse response = service.getBargains(4);

        assertEquals(0, response.itemCount());
        assertEquals(List.of(), response.items());
    }

    private IngredientPrice price(long seriesId, String ingredientName, LocalDate date, String value) {
        Ingredient ingredient = mock(Ingredient.class);
        when(ingredient.getName()).thenReturn(ingredientName);
        when(ingredient.getCategory()).thenReturn("농산물");
        when(ingredient.isActive()).thenReturn(true);

        PriceSeries series = mock(PriceSeries.class);
        when(series.getId()).thenReturn(seriesId);
        when(series.getIngredient()).thenReturn(ingredient);
        when(series.getSourceName()).thenReturn("KAMIS");
        when(series.getVariety()).thenReturn("일반");
        when(series.getGrade()).thenReturn("상품");
        when(series.getOriginalUnit()).thenReturn("kg");

        IngredientPrice price = mock(IngredientPrice.class);
        when(price.getSeries()).thenReturn(series);
        when(price.getPriceDate()).thenReturn(date);
        when(price.getOriginalPrice()).thenReturn(new BigDecimal(value));
        return price;
    }
}
