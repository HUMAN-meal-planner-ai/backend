package com.human.backend.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.human.backend.prediction.dto.response.AiPricePredictionResponse;
import com.human.backend.prediction.repository.PricePredictionRepository;

class PricePredictionStorageServiceTest {

    @Test
    void insertsNewPredictionsAndSkipsConflicts() {
        PricePredictionRepository repository = mock(PricePredictionRepository.class);
        when(repository.insertIfAbsent(
                any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1, 0);
        PricePredictionStorageService service = new PricePredictionStorageService(repository);

        PricePredictionStorageService.StoreResult result = service.store(List.of(
                prediction(3L), prediction(4L)));

        assertEquals(1, result.inserted());
        assertEquals(1, result.duplicatesSkipped());
    }

    private AiPricePredictionResponse prediction(long seriesId) {
        return new AiPricePredictionResponse(
                seriesId,
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 19),
                new BigDecimal("0.791000"),
                new BigDecimal("0.791000"),
                "g",
                "lag_1_baseline",
                "lag_1_baseline_v1",
                Instant.parse("2026-09-22T04:00:00Z"));
    }
}
