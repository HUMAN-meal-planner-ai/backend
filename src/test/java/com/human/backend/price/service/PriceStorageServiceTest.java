package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.util.KamisPriceValueParser;

class PriceStorageServiceTest {

    private final IngredientPriceRepository ingredientPriceRepository =
            mock(IngredientPriceRepository.class);
    private PriceStorageService service;
    private PriceSeries series;

    @BeforeEach
    void setUp() {
        service = new PriceStorageService(
                ingredientPriceRepository, new KamisPriceValueParser());
        Ingredient ingredient = mock(Ingredient.class);
        series = mock(PriceSeries.class);
        when(ingredient.getStandardUnit()).thenReturn("g");
        when(ingredient.isActive()).thenReturn(true);
        when(series.getId()).thenReturn(77L);
        when(series.getSourceName()).thenReturn("KAMIS");
        when(series.getIngredient()).thenReturn(ingredient);
        when(series.getOriginalUnit()).thenReturn("kg");
        when(series.getUnitQuantity()).thenReturn(BigDecimal.ONE);
    }

    @Test
    void storesPriceOnTheExistingSeries() {
        when(ingredientPriceRepository.existsBySeries_IdAndPriceDate(
                77L, LocalDate.of(2026, 9, 15))).thenReturn(false);

        PriceStorageService.StoreResult result = service.store(series, List.of(item("5,340")));

        ArgumentCaptor<IngredientPrice> captor = ArgumentCaptor.forClass(IngredientPrice.class);
        verify(ingredientPriceRepository).save(captor.capture());
        assertEquals(series, captor.getValue().getSeries());
        assertEquals(new BigDecimal("5340"), captor.getValue().getOriginalPrice());
        assertEquals(new BigDecimal("1000"), captor.getValue().getUnitQuantity());
        assertEquals(0, result.seriesCreated());
        assertEquals(1, result.pricesInserted());
    }

    @Test
    void skipsExistingSeriesDate() {
        when(ingredientPriceRepository.existsBySeries_IdAndPriceDate(
                77L, LocalDate.of(2026, 9, 15))).thenReturn(true);

        PriceStorageService.StoreResult result = service.store(series, List.of(item("5,340")));

        verify(ingredientPriceRepository, never()).save(any());
        assertEquals(0, result.pricesInserted());
        assertEquals(1, result.duplicatesSkipped());
    }

    private KamisPriceItemDto item(String price) {
        return new KamisPriceItemDto(
                "양배추", "양배추(1kg)", "서울", "가락도매", "2026", "09/15", price);
    }
}
