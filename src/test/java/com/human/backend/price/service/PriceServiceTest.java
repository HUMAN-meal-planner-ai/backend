package com.human.backend.price.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        KamisPriceTarget target = new KamisPriceTarget(
                "F00993", "200", "212", "양배추", "00", "양배추(1kg)", "04", "상품");
        LocalDate requestedDate = LocalDate.of(2026, 9, 15);
        KamisPriceItemDto outOfRange = item("06/08");
        KamisPriceItemDto inRange = item("09/15");

        when(catalog.findByIngredientCode("F00993")).thenReturn(Optional.of(target));
        when(client.getPriceData(target, requestedDate, requestedDate)).thenReturn(
                new KamisPriceResponseDto(new KamisPriceDataDto("000", List.of(outOfRange, inRange))));
        when(storage.store(eq(target), org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new PriceStorageService.StoreResult(0, 0, 1, 0));

        PriceService service = new PriceService(
                client, catalog, storage, new KamisPriceValueParser());
        PriceCollectionResult result = service.collectOne("F00993", requestedDate, requestedDate);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KamisPriceItemDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(storage).store(eq(target), captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals("09/15", captor.getValue().get(0).regDay());
        assertEquals(1, result.outOfRangeRowsSkipped());
    }

    private KamisPriceItemDto item(String regDay) {
        return new KamisPriceItemDto(
                "양배추", "양배추(1kg)", "서울", "가락도매", "2026", regDay, "791");
    }
}
