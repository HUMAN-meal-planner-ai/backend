package com.human.backend.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.human.backend.global.exception.ApiException;
import com.human.backend.integration.aiserver.AiPricePredictionClient;
import com.human.backend.integration.aiserver.AiPricePredictionClientException;
import com.human.backend.prediction.dto.response.AiPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiPricePredictionResponse;
import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.PricePredictionTarget;

class PricePredictionServiceTest {

    private final PriceSeriesRepository seriesRepository = mock(PriceSeriesRepository.class);
    private final AiPricePredictionClient client = mock(AiPricePredictionClient.class);
    private final PricePredictionStorageService storage = mock(PricePredictionStorageService.class);
    private final PricePredictionService service =
            new PricePredictionService(seriesRepository, client, storage);

    @Test
    void validatesAndStoresPredictions() {
        PricePredictionTarget target = target(3L, "g");
        AiPricePredictionResponse prediction = prediction(3L, "g");
        when(seriesRepository.findAllActivePredictionTargets()).thenReturn(List.of(target));
        when(client.predictNext(List.of(3L)))
                .thenReturn(new AiPricePredictionBatchResponse(List.of(prediction)));
        when(storage.store(List.of(prediction)))
                .thenReturn(new PricePredictionStorageService.StoreResult(1, 0));

        PricePredictionCollectionResult result = service.generateNextPredictions();

        assertEquals(1, result.requestedSeries());
        assertEquals(1, result.insertedPredictions());
        verify(storage).store(List.of(prediction));
    }

    @Test
    void doesNotStoreInvalidUnitResponse() {
        PricePredictionTarget target = target(3L, "g");
        when(seriesRepository.findAllActivePredictionTargets())
                .thenReturn(List.of(target));
        when(client.predictNext(List.of(3L)))
                .thenReturn(new AiPricePredictionBatchResponse(List.of(prediction(3L, "kg"))));

        ApiException exception = assertThrows(
                ApiException.class, service::generateNextPredictions);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        verify(storage, never()).store(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void doesNotStoreWhenAiCallTimesOut() {
        PricePredictionTarget target = target(3L, "g");
        when(seriesRepository.findAllActivePredictionTargets())
                .thenReturn(List.of(target));
        when(client.predictNext(List.of(3L))).thenThrow(new AiPricePredictionClientException(
                AiPricePredictionClientException.FailureType.TIMEOUT,
                "AI 가격예측 서버 호출 시간이 초과되었습니다.", null));

        ApiException exception = assertThrows(
                ApiException.class, service::generateNextPredictions);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.getStatus());
        verify(storage, never()).store(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void validatesAndStoresSevenDayPredictions() {
        PricePredictionTarget target = target(3L, "g");
        List<AiPricePredictionResponse> predictions = IntStream.rangeClosed(1, 7)
                .mapToObj(day -> prediction(3L, "g", day))
                .toList();
        when(seriesRepository.findAllActivePredictionTargets()).thenReturn(List.of(target));
        when(client.predictSevenDays(List.of(3L)))
                .thenReturn(new AiPricePredictionBatchResponse(predictions));
        when(storage.store(predictions))
                .thenReturn(new PricePredictionStorageService.StoreResult(6, 1));

        PricePredictionCollectionResult result = service.generateSevenDayPredictions();

        assertEquals(1, result.requestedSeries());
        assertEquals(7, result.receivedPredictions());
        assertEquals(6, result.insertedPredictions());
        assertEquals(1, result.duplicatesSkipped());
        verify(storage).store(predictions);
    }

    @Test
    void doesNotStoreIncompleteSevenDayResponse() {
        PricePredictionTarget target = target(3L, "g");
        List<AiPricePredictionResponse> predictions = IntStream.rangeClosed(1, 6)
                .mapToObj(day -> prediction(3L, "g", day))
                .toList();
        when(seriesRepository.findAllActivePredictionTargets()).thenReturn(List.of(target));
        when(client.predictSevenDays(List.of(3L)))
                .thenReturn(new AiPricePredictionBatchResponse(predictions));

        ApiException exception = assertThrows(
                ApiException.class, service::generateSevenDayPredictions);

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        verify(storage, never()).store(org.mockito.ArgumentMatchers.anyList());
    }

    private PricePredictionTarget target(long seriesId, String unit) {
        PricePredictionTarget target = mock(PricePredictionTarget.class);
        when(target.getSeriesId()).thenReturn(seriesId);
        when(target.getBaseDate()).thenReturn(LocalDate.of(2026, 9, 18));
        when(target.getBasePrice()).thenReturn(new BigDecimal("0.791000"));
        when(target.getStandardUnit()).thenReturn(unit);
        return target;
    }

    private AiPricePredictionResponse prediction(long seriesId, String unit) {
        return prediction(seriesId, unit, 1);
    }

    private AiPricePredictionResponse prediction(long seriesId, String unit, int daysAhead) {
        return new AiPricePredictionResponse(
                seriesId,
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 18).plusDays(daysAhead),
                new BigDecimal("0.791"),
                new BigDecimal("0.791"),
                unit,
                "lag_1_baseline",
                "lag_1_baseline_v1",
                Instant.parse("2026-09-22T04:00:00Z"));
    }
}
