package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.ingredient.repository.IngredientRepository;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.config.KamisPriceTarget;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.util.KamisPriceValueParser;

class PriceStorageServiceTest {

    private final IngredientRepository ingredientRepository = mock(IngredientRepository.class);
    private final PriceSeriesRepository priceSeriesRepository = mock(PriceSeriesRepository.class);
    private final IngredientPriceRepository ingredientPriceRepository = mock(IngredientPriceRepository.class);
    private PriceStorageService service;
    private Ingredient ingredient;
    private PriceSeries series;

    @BeforeEach
    void setUp() {
        service = new PriceStorageService(
                ingredientRepository, priceSeriesRepository, ingredientPriceRepository,
                new KamisPriceValueParser());
        ingredient = mock(Ingredient.class);
        series = mock(PriceSeries.class);
        when(ingredient.getId()).thenReturn(509L);
        when(ingredient.getStandardUnit()).thenReturn("g");
        when(ingredient.isActive()).thenReturn(true);
        when(series.getId()).thenReturn(77L);
        when(ingredientRepository.findByIngredientCode("F00993")).thenReturn(Optional.of(ingredient));
        when(priceSeriesRepository.findByNaturalKey(
                eq(509L), eq("KAMIS"), eq("212"), eq("양배추(1kg)"), eq("상품"),
                eq("WHOLESALE"), eq("가락도매"), eq("서울"), eq("kg"), eq(BigDecimal.ONE)))
                .thenReturn(Optional.of(series));
    }

    @Test
    void reusesSeriesAndStoresOneKilogramAsOneThousandGrams() {
        when(ingredientPriceRepository.existsBySeries_IdAndPriceDate(
                77L, LocalDate.of(2026, 9, 15))).thenReturn(false);

        PriceStorageService.StoreResult result = service.store(target(), List.of(item("5,340")));

        ArgumentCaptor<IngredientPrice> captor = ArgumentCaptor.forClass(IngredientPrice.class);
        verify(ingredientPriceRepository).save(captor.capture());
        assertEquals(new BigDecimal("5340"), captor.getValue().getOriginalPrice());
        assertEquals(new BigDecimal("1000"), captor.getValue().getUnitQuantity());
        assertEquals(0, result.seriesCreated());
        assertEquals(1, result.pricesInserted());
    }

    @Test
    void skipsExistingSeriesDate() {
        when(ingredientPriceRepository.existsBySeries_IdAndPriceDate(
                77L, LocalDate.of(2026, 9, 15))).thenReturn(true);

        PriceStorageService.StoreResult result = service.store(target(), List.of(item("5,340")));

        verify(ingredientPriceRepository, never()).save(any());
        assertEquals(0, result.pricesInserted());
        assertEquals(1, result.duplicatesSkipped());
    }

    private KamisPriceTarget target() {
        return new KamisPriceTarget(
                "F00993", "200", "212", "양배추", "00", "양배추(1kg)", "04", "상품");
    }

    private KamisPriceItemDto item(String price) {
        return new KamisPriceItemDto(
                "양배추", "양배추(1kg)", "서울", "가락도매", "2026", "09/15", price);
    }
}
