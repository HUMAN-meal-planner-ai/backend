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

import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceDataDto;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import com.human.backend.price.config.KamisPriceCatalog;
import com.human.backend.price.config.KamisPriceTarget;
import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.util.KamisPriceValueParser;

class PriceServiceTest {

    @Test
    void excludesRowsOutsideRequestedRangeEvenIfKamisReturnsThem() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        KamisPriceCatalog catalog = mock(KamisPriceCatalog.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        KamisPriceTarget target = target("00", "국산(1kg)", "04", "상품");
        LocalDate requestedDate = LocalDate.of(2026, 9, 15);
        KamisPriceItemDto outOfRange = item("국산(1kg)", "06/08");
        KamisPriceItemDto inRange = item("국산(1kg)", "09/15");

        when(catalog.findAllByIngredientCode("F00426")).thenReturn(List.of(target));
        when(client.getPriceData(target, requestedDate, requestedDate)).thenReturn(
                response(outOfRange, inRange));
        when(storage.store(eq(target), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(0, 0, 1, 0));

        PriceCollectionResult result = service(client, catalog, storage)
                .collectOne("F00426", requestedDate, requestedDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KamisPriceItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(storage).store(eq(target), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("09/15", captor.getValue().get(0).regDay());
        assertEquals(1, result.outOfRangeRowsSkipped());
    }

    @Test
    void collectsAllThreeTargetsForOneIngredient() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        KamisPriceCatalog catalog = mock(KamisPriceCatalog.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        LocalDate date = LocalDate.of(2026, 9, 15);
        KamisPriceTarget domestic = target("00", "국산(1kg)", "04", "상품");
        KamisPriceTarget china = target("02", "중국(1kg)", "05", "중품");
        KamisPriceTarget peru = target("03", "페루(1kg)", "05", "중품");

        when(catalog.findAllByIngredientCode("F00426"))
                .thenReturn(List.of(domestic, china, peru));
        for (KamisPriceTarget target : List.of(domestic, china, peru)) {
            when(client.getPriceData(target, date, date))
                    .thenReturn(response(item(target.kindName(), "09/15")));
            when(storage.store(eq(target), anyList()))
                    .thenReturn(new PriceStorageService.StoreResult(1, 1, 0, 0));
        }

        PriceCollectionResult result = service(client, catalog, storage)
                .collectOne("F00426", date, date);

        verify(client).getPriceData(domestic, date, date);
        verify(client).getPriceData(china, date, date);
        verify(client).getPriceData(peru, date, date);
        verify(storage).store(eq(domestic), anyList());
        verify(storage).store(eq(china), anyList());
        verify(storage).store(eq(peru), anyList());
        assertEquals(3, result.targetCount());
        assertEquals(3, result.targetsSucceeded());
        assertEquals(0, result.targetsFailed());
        assertEquals(3, result.pricesInserted());
    }

    @Test
    void continuesWithRemainingTargetsWhenOneTargetFails() {
        KamisPriceApiClient client = mock(KamisPriceApiClient.class);
        KamisPriceCatalog catalog = mock(KamisPriceCatalog.class);
        PriceStorageService storage = mock(PriceStorageService.class);
        LocalDate date = LocalDate.of(2026, 9, 15);
        KamisPriceTarget domestic = target("00", "국산(1kg)", "04", "상품");
        KamisPriceTarget china = target("02", "중국(1kg)", "05", "중품");
        KamisPriceTarget peru = target("03", "페루(1kg)", "05", "중품");

        when(catalog.findAllByIngredientCode("F00426"))
                .thenReturn(List.of(domestic, china, peru));
        when(client.getPriceData(domestic, date, date))
                .thenReturn(response(item(domestic.kindName(), "09/15")));
        when(client.getPriceData(china, date, date))
                .thenThrow(new IllegalStateException("temporary KAMIS failure"));
        when(client.getPriceData(peru, date, date))
                .thenReturn(response(item(peru.kindName(), "09/15")));
        when(storage.store(eq(domestic), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(1, 1, 0, 0));
        when(storage.store(eq(peru), anyList()))
                .thenReturn(new PriceStorageService.StoreResult(1, 1, 0, 0));

        PriceCollectionResult result = service(client, catalog, storage)
                .collectOne("F00426", date, date);

        verify(client).getPriceData(peru, date, date);
        verify(storage).store(eq(peru), anyList());
        assertEquals(2, result.targetsSucceeded());
        assertEquals(1, result.targetsFailed());
        assertFalse(result.targets().get(1).success());
        assertEquals("temporary KAMIS failure", result.targets().get(1).errorMessage());
    }

    private PriceService service(
            KamisPriceApiClient client, KamisPriceCatalog catalog, PriceStorageService storage) {
        return new PriceService(client, catalog, storage, new KamisPriceValueParser());
    }

    private KamisPriceTarget target(
            String kindCode, String kindName, String rankCode, String rankName) {
        return new KamisPriceTarget(
                "F00426", "100", "143", "녹두",
                kindCode, kindName, rankCode, rankName);
    }

    private KamisPriceResponseDto response(KamisPriceItemDto... items) {
        return new KamisPriceResponseDto(new KamisPriceDataDto("000", List.of(items)));
    }

    private KamisPriceItemDto item(String kindName, String regDay) {
        return new KamisPriceItemDto(
                "녹두", kindName, "서울", "가락도매", "2026", regDay, "5,325");
    }
}
