package com.human.backend.price.scheduler;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import com.human.backend.price.dto.response.PriceTargetCollectionResult;
import com.human.backend.price.service.PriceService;
import org.junit.jupiter.api.Test;

class KamisPriceCollectionSchedulerTest {

    @Test
    void collectsConfiguredNumberOfDaysIncludingExecutionDate() {
        PriceService priceService = mock(PriceService.class);
        KamisPriceCollectionScheduler scheduler = new KamisPriceCollectionScheduler(
                priceService, 7, "Asia/Seoul");
        LocalDate executionDate = LocalDate.of(2026, 9, 21);
        when(priceService.collectAll(LocalDate.of(2026, 9, 15), executionDate))
                .thenReturn(List.of(successResult()));

        scheduler.collectRecentPrices(executionDate);

        verify(priceService).collectAll(LocalDate.of(2026, 9, 15), executionDate);
    }

    @Test
    void rejectsNonPositiveLookbackDays() {
        PriceService priceService = mock(PriceService.class);

        assertThrows(IllegalArgumentException.class,
                () -> new KamisPriceCollectionScheduler(priceService, 0, "Asia/Seoul"));
    }

    private PriceTargetCollectionResult successResult() {
        return new PriceTargetCollectionResult(
                3L, "200", "212", "00", "국산(1kg)", "04", "상품",
                true, null, 5, 1, 0, 2, 2, 0);
    }
}
