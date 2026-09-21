package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceDataDto;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.util.KamisPriceValueParser;

class PriceServiceTest {

    @Test
    void excludesRowsOutsideRequestedRangeEvenIfKamisReturnsThem() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        PriceSeriesRepository repository = mock(PriceSeriesRepository.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        PriceSeries series = series(1L, "00", "국산(1kg)", "04", "상품");
        LocalDate requestedDate = LocalDate.of(2026, 9, 15);
        KamisPriceItemDto outOfRange = item("국산(1kg)", "06/08");
        KamisPriceItemDto inRange = item("국산(1kg)", "09/15");

        when(repository.findAllActiveKamisCollectionTargetsByIngredientCode("F00426"))
                .thenReturn(List.of(series));
        when(client.getPriceData(series, requestedDate, requestedDate)).thenReturn(
                response(outOfRange, inRange));
        when(storage.store(eq(series), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(0, 0, 1, 0));

        PriceCollectionResult result = service(client, repository, storage)
                .collectOne("F00426", requestedDate, requestedDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KamisPriceItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(storage).store(eq(series), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("09/15", captor.getValue().get(0).regDay());
        assertEquals(1, result.outOfRangeRowsSkipped());
    }

    @Test
    void collectsAllSeriesForOneIngredient() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        PriceSeriesRepository repository = mock(PriceSeriesRepository.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        LocalDate date = LocalDate.of(2026, 9, 15);
        PriceSeries domestic = series(1L, "00", "국산(1kg)", "04", "상품");
        PriceSeries china = series(2L, "02", "중국(1kg)", "05", "중품");
        PriceSeries peru = series(3L, "03", "페루(1kg)", "05", "중품");

        when(repository.findAllActiveKamisCollectionTargetsByIngredientCode("F00426"))
                .thenReturn(List.of(domestic, china, peru));
        for (PriceSeries series : List.of(domestic, china, peru)) {
            String variety = series.getVariety();
            when(client.getPriceData(series, date, date))
                    .thenReturn(response(item(variety, "09/15")));
            when(storage.store(eq(series), anyList()))
                    .thenReturn(new PriceStorageService.StoreResult(0, 1, 0, 0));
        }

        PriceCollectionResult result = service(client, repository, storage)
                .collectOne("F00426", date, date);

        verify(client).getPriceData(domestic, date, date);
        verify(client).getPriceData(china, date, date);
        verify(client).getPriceData(peru, date, date);
        assertEquals(3, result.targetCount());
        assertEquals(3, result.targetsSucceeded());
        assertEquals(0, result.targetsFailed());
        assertEquals(3, result.pricesInserted());
    }

    @Test
    void continuesWithRemainingSeriesWhenOneSeriesFails() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        PriceSeriesRepository repository = mock(PriceSeriesRepository.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        LocalDate date = LocalDate.of(2026, 9, 15);
        PriceSeries domestic = series(1L, "00", "국산(1kg)", "04", "상품");
        PriceSeries china = series(2L, "02", "중국(1kg)", "05", "중품");
        PriceSeries peru = series(3L, "03", "페루(1kg)", "05", "중품");

        when(repository.findAllActiveKamisCollectionTargetsByIngredientCode("F00426"))
                .thenReturn(List.of(domestic, china, peru));
        String domesticVariety = domestic.getVariety();
        String peruVariety = peru.getVariety();
        when(client.getPriceData(domestic, date, date))
                .thenReturn(response(item(domesticVariety, "09/15")));
        when(client.getPriceData(china, date, date))
                .thenThrow(new IllegalStateException("일시적인 KAMIS 오류"));
        when(client.getPriceData(peru, date, date))
                .thenReturn(response(item(peruVariety, "09/15")));
        when(storage.store(eq(domestic), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(0, 1, 0, 0));
        when(storage.store(eq(peru), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(0, 1, 0, 0));

        PriceCollectionResult result = service(client, repository, storage)
                .collectOne("F00426", date, date);

        verify(client).getPriceData(peru, date, date);
        verify(storage).store(eq(peru), anyList());
        assertEquals(2, result.targetsSucceeded());
        assertEquals(1, result.targetsFailed());
        assertFalse(result.targets().get(1).success());
        assertEquals("일시적인 KAMIS 오류", result.targets().get(1).errorMessage());
    }

    private PriceService service(
            KamisPriceApiClient client, PriceSeriesRepository repository,
            PriceStorageService storage) {
        return new PriceService(client, repository, storage, new KamisPriceValueParser());
    }

    private PriceSeries series(
            long id, String kindCode, String variety, String rankCode, String grade) {
        Ingredient ingredient = mock(Ingredient.class);
        PriceSeries series = mock(PriceSeries.class);
        when(ingredient.getIngredientCode()).thenReturn("F00426");
        when(series.getId()).thenReturn(id);
        when(series.getIngredient()).thenReturn(ingredient);
        when(series.getSourceCategoryCode()).thenReturn("100");
        when(series.getSourceItemCode()).thenReturn("143");
        when(series.getSourceKindCode()).thenReturn(kindCode);
        when(series.getSourceRankCode()).thenReturn(rankCode);
        when(series.getVariety()).thenReturn(variety);
        when(series.getGrade()).thenReturn(grade);
        return series;
    }

    private KamisPriceResponseDto response(KamisPriceItemDto... items) {
        return new KamisPriceResponseDto(new KamisPriceDataDto("000", List.of(items)));
    }

    private KamisPriceItemDto item(String kindName, String regDay) {
        return new KamisPriceItemDto(
                "녹두", kindName, "서울", "가락도매", "2026", regDay, "5,325");
    }
}
