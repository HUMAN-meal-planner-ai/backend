package com.human.backend.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
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
import com.human.backend.prediction.dto.request.AiWeeklyPricePredictionRequest;
import com.human.backend.prediction.dto.response.AiPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiPricePredictionResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionResponse;
import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.PricePredictionTarget;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

class PricePredictionServiceTest {

    private final PriceSeriesRepository seriesRepository = mock(PriceSeriesRepository.class);
    private final AiPricePredictionClient client = mock(AiPricePredictionClient.class);
    private final PricePredictionStorageService storage = mock(PricePredictionStorageService.class);
    private final PricePredictionService service =
            new PricePredictionService(seriesRepository, client, storage, "3");

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
        PricePredictionTarget unsupportedTarget = target(4L, "g");
        List<WeeklyRepresentativePrice> history = weeklyHistory(3L, "g");
        AiWeeklyPricePredictionResponse weeklyPrediction = weeklyPrediction(3L, "g");
        AiPricePredictionResponse storagePrediction = weeklyPrediction.toStorageResponse();
        when(seriesRepository.findAllActivePredictionTargets())
                .thenReturn(List.of(target, unsupportedTarget));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(3L)))
                .thenReturn(history);
        when(client.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(new AiWeeklyPricePredictionBatchResponse(
                        List.of(weeklyPrediction)));
        when(storage.store(List.of(storagePrediction)))
                .thenReturn(new PricePredictionStorageService.StoreResult(1, 0));

        PricePredictionCollectionResult result = service.generateSevenDayPredictions();

        assertEquals(1, result.requestedSeries());
        assertEquals(1, result.receivedPredictions());
        assertEquals(1, result.insertedPredictions());
        assertEquals(0, result.duplicatesSkipped());
        verify(seriesRepository).findWeeklyRepresentativePrices(List.of(3L));
        verify(storage).store(List.of(storagePrediction));
    }

    @Test
    void doesNotStoreIncompleteSevenDayResponse() {
        PricePredictionTarget target = target(3L, "g");
        List<WeeklyRepresentativePrice> history = weeklyHistory(3L, "g");
        when(seriesRepository.findAllActivePredictionTargets()).thenReturn(List.of(target));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(3L)))
                .thenReturn(history);
        when(client.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(new AiWeeklyPricePredictionBatchResponse(List.of()));

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

    private List<WeeklyRepresentativePrice> weeklyHistory(long seriesId, String unit) {
        return IntStream.rangeClosed(0, 28)
                .mapToObj(day -> weeklyPrice(
                        seriesId,
                        unit,
                        LocalDate.of(2026, 8, 21).plusDays(day)))
                .toList();
    }

    private WeeklyRepresentativePrice weeklyPrice(
            long seriesId, String unit, LocalDate priceDate) {
        WeeklyRepresentativePrice price = mock(WeeklyRepresentativePrice.class);
        when(price.getSeriesId()).thenReturn(seriesId);
        when(price.getIngredientCode()).thenReturn("F00993");
        when(price.getPriceDate()).thenReturn(priceDate);
        when(price.getRepresentativePrice()).thenReturn(new BigDecimal("0.791000"));
        when(price.getStandardUnit()).thenReturn(unit);
        return price;
    }

    private AiWeeklyPricePredictionResponse weeklyPrediction(long seriesId, String unit) {
        return new AiWeeklyPricePredictionResponse(
                seriesId,
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 25),
                new BigDecimal("0.791000"),
                new BigDecimal("0.812000"),
                new BigDecimal("0.844000"),
                unit,
                0.73,
                0.81,
                0.77,
                "weekly_mean_ridge",
                "weekly_ridge_v1",
                Instant.parse("2026-09-23T04:00:00Z"));
    }
}
