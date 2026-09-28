package com.human.backend.prediction.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import com.human.backend.global.exception.ApiException;
import com.human.backend.integration.aiserver.AiPricePredictionClient;
import com.human.backend.prediction.dto.request.AiWeeklyPricePredictionRequest;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionResponse;
import com.human.backend.prediction.dto.response.IngredientRiskRankingResponse;
import com.human.backend.prediction.dto.response.PricePredictionChartResponse;
import com.human.backend.prediction.dto.response.WeeklyPricePredictionRiskResponse;
import com.human.backend.prediction.repository.PricePredictionRepository;
import com.human.backend.prediction.repository.PricePredictionRepository.StoredWeeklyPrediction;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

class PricePredictionQueryServiceTest {

    private static final long SERIES_ID = 3L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 18);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 9, 25);

    private final PricePredictionRepository predictionRepository =
            mock(PricePredictionRepository.class);
    private final PriceSeriesRepository seriesRepository = mock(PriceSeriesRepository.class);
    private final AiPricePredictionClient aiClient = mock(AiPricePredictionClient.class);
    private final PricePredictionQueryService service = new PricePredictionQueryService(
            predictionRepository, seriesRepository, aiClient, "3,17");

    @BeforeEach
    void setUpAvailablePrediction() {
        StoredWeeklyPrediction stored = storedPrediction(new BigDecimal("100.000000"));
        WeeklyRepresentativePrice history = historyPoint(new BigDecimal("100.000000"));
        when(predictionRepository.findLatestWeeklyPrediction(SERIES_ID))
                .thenReturn(Optional.of(stored));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(SERIES_ID)))
                .thenReturn(List.of(history));
    }

    @Test
    void returnsStoredPredictionAndMarksExactThresholdAsRisky() {
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(aiResponse(new BigDecimal("100.000000"), 0.75));

        WeeklyPricePredictionRiskResponse result =
                service.getLatestWeeklyPrediction(SERIES_ID);

        assertEquals(new BigDecimal("100.000000"), result.basePrice());
        assertEquals(new BigDecimal("110.000000"), result.predictedPrice());
        assertEquals(new BigDecimal("0.100000"), result.expectedIncreaseRate());
        assertEquals(0.75, result.combinedRiskScore());
        assertEquals(0.75, result.riskThreshold());
        assertTrue(result.risky());
        assertEquals("F00993", result.ingredientCode());
    }

    @Test
    void marksScoreBelowThresholdAsNotRisky() {
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(aiResponse(new BigDecimal("100.000000"), 0.749999));

        WeeklyPricePredictionRiskResponse result =
                service.getLatestWeeklyPrediction(SERIES_ID);

        assertFalse(result.risky());
    }

    @Test
    void sendsOnlyHistoryThroughStoredBaseDateToAi() {
        WeeklyRepresentativePrice previous = historyPoint(
                BASE_DATE.minusDays(1), new BigDecimal("99.000000"));
        WeeklyRepresentativePrice base = historyPoint(
                BASE_DATE, new BigDecimal("100.000000"));
        WeeklyRepresentativePrice future = historyPoint(
                BASE_DATE.plusDays(1), new BigDecimal("101.000000"));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(SERIES_ID)))
                .thenReturn(List.of(previous, base, future));
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(aiResponse(new BigDecimal("100.000000"), 0.70));

        service.getLatestWeeklyPrediction(SERIES_ID);

        ArgumentCaptor<AiWeeklyPricePredictionRequest> requestCaptor =
                ArgumentCaptor.forClass(AiWeeklyPricePredictionRequest.class);
        verify(aiClient).predictSevenDays(requestCaptor.capture());
        assertIterableEquals(
                List.of(BASE_DATE.minusDays(1), BASE_DATE),
                requestCaptor.getValue().series().get(0).prices().stream()
                        .map(point -> point.priceDate())
                        .toList());
    }

    @Test
    void returnsNullIncreaseRateWhenBasePriceIsZero() {
        StoredWeeklyPrediction stored = storedPrediction(BigDecimal.ZERO);
        WeeklyRepresentativePrice history = historyPoint(BigDecimal.ZERO);
        when(predictionRepository.findLatestWeeklyPrediction(SERIES_ID))
                .thenReturn(Optional.of(stored));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(SERIES_ID)))
                .thenReturn(List.of(history));
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(aiResponse(BigDecimal.ZERO, 0.80));

        WeeklyPricePredictionRiskResponse result =
                service.getLatestWeeklyPrediction(SERIES_ID);

        assertNull(result.expectedIncreaseRate());
    }

    @Test
    void rejectsUnsupportedSeriesBeforeDatabaseLookup() {
        ApiException exception = assertThrows(
                ApiException.class, () -> service.getLatestWeeklyPrediction(99L));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exception.getStatus());
        assertEquals("WEEKLY_PRICE_PREDICTION_UNSUPPORTED_SERIES", exception.getCode());
        verify(predictionRepository, never()).findLatestWeeklyPrediction(99L);
    }

    @Test
    void returnsNotFoundWhenNoStoredWeeklyPredictionExists() {
        when(predictionRepository.findLatestWeeklyPrediction(17L))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class, () -> service.getLatestWeeklyPrediction(17L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
        assertEquals("WEEKLY_PRICE_PREDICTION_NOT_FOUND", exception.getCode());
    }

    @Test
    void rejectsMissingHistoryWithoutCallingAi() {
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(SERIES_ID)))
                .thenReturn(List.of());

        ApiException exception = assertThrows(
                ApiException.class, () -> service.getLatestWeeklyPrediction(SERIES_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exception.getStatus());
        assertEquals("WEEKLY_PRICE_HISTORY_NOT_FOUND", exception.getCode());
        verify(aiClient, never()).predictSevenDays(any(AiWeeklyPricePredictionRequest.class));
    }

    @Test
    void rejectsHistoryWithoutStoredBaseDateBeforeCallingAi() {
        WeeklyRepresentativePrice previous = historyPoint(
                BASE_DATE.minusDays(1), new BigDecimal("99.000000"));
        WeeklyRepresentativePrice future = historyPoint(
                BASE_DATE.plusDays(1), new BigDecimal("101.000000"));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(SERIES_ID)))
                .thenReturn(List.of(previous, future));

        ApiException exception = assertThrows(
                ApiException.class, () -> service.getLatestWeeklyPrediction(SERIES_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, exception.getStatus());
        assertEquals("WEEKLY_PRICE_HISTORY_NOT_FOUND", exception.getCode());
        verify(aiClient, never()).predictSevenDays(any(AiWeeklyPricePredictionRequest.class));
    }

    @Test
    void returnsActualHistoryAndOneWeeklyAveragePredictionPointForChart() {
        LocalDate startDate = BASE_DATE.minusDays(2);
        List<WeeklyRepresentativePrice> chartHistory = List.of(
                historyPoint(startDate, new BigDecimal("98.000000")),
                historyPoint(BASE_DATE, new BigDecimal("100.000000")));
        when(seriesRepository.findDailyRepresentativePrices(SERIES_ID, startDate, BASE_DATE))
                .thenReturn(chartHistory);

        PricePredictionChartResponse result = service.getWeeklyPredictionChart(
                SERIES_ID, startDate, TARGET_DATE);

        assertEquals(2, result.actualPrices().size());
        assertEquals(BASE_DATE, result.actualEndDate());
        assertEquals(TARGET_DATE, result.weeklyPrediction().targetDate());
        assertEquals(new BigDecimal("110.000000"), result.weeklyPrediction().predictedPrice());
        assertEquals("NEXT_7_DAY_AVERAGE", result.weeklyPrediction().predictionMeaning());
        verify(seriesRepository).findDailyRepresentativePrices(SERIES_ID, startDate, BASE_DATE);
        verify(aiClient, never()).predictSevenDays(any(AiWeeklyPricePredictionRequest.class));
    }

    @Test
    void ranksWithOneBatchCallByRidgeThenCombinedThenSeriesId() {
        PricePredictionQueryService rankingService = new PricePredictionQueryService(
                predictionRepository, seriesRepository, aiClient, "3,17,21");
        StoredWeeklyPrediction series3 = storedPrediction(
                3L, "F00003", "감자", "100.000000", "110.000000");
        StoredWeeklyPrediction series17 = storedPrediction(
                17L, "F00017", "양파", "200.000000", "210.000000");
        StoredWeeklyPrediction series21 = storedPrediction(
                21L, "F00021", "당근", "300.000000", "310.000000");
        when(predictionRepository.findLatestWeeklyPredictions(List.of(3L, 17L, 21L)))
                .thenReturn(List.of(series3, series17, series21));
        List<WeeklyRepresentativePrice> histories = List.of(
                historyPoint(3L, "F00003", BASE_DATE.minusDays(28), "95.000000"),
                historyPoint(3L, "F00003", BASE_DATE, "100.000000"),
                historyPoint(17L, "F00017", BASE_DATE.minusDays(28), "195.000000"),
                historyPoint(17L, "F00017", BASE_DATE, "200.000000"),
                historyPoint(21L, "F00021", BASE_DATE.minusDays(28), "295.000000"),
                historyPoint(21L, "F00021", BASE_DATE, "300.000000"));
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(3L, 17L, 21L)))
                .thenReturn(histories);
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(new AiWeeklyPricePredictionBatchResponse(List.of(
                        aiPrediction(3L, "100.000000", "110.000000", 0.90, 0.40, 0.65),
                        aiPrediction(17L, "200.000000", "210.000000", 0.90, 0.50, 0.70),
                        aiPrediction(21L, "300.000000", "310.000000", 0.80, 0.90, 0.85))));

        IngredientRiskRankingResponse result = rankingService.getWeeklyRiskRankings(3);

        assertIterableEquals(
                List.of(17L, 3L, 21L),
                result.rankings().stream().map(item -> item.seriesId()).toList());
        assertIterableEquals(
                List.of(1, 2, 3),
                result.rankings().stream().map(item -> item.rank()).toList());
        verify(aiClient, times(1)).predictSevenDays(any(AiWeeklyPricePredictionRequest.class));
    }

    @Test
    void excludesSeriesWithoutEnoughHistoryInsteadOfFailingRanking() {
        StoredWeeklyPrediction available = storedPrediction(
                3L, "F00003", "감자", "100.000000", "110.000000");
        StoredWeeklyPrediction missingHistory = storedPrediction(
                17L, "F00017", "양파", "200.000000", "210.000000");
        when(predictionRepository.findLatestWeeklyPredictions(List.of(3L, 17L)))
                .thenReturn(List.of(available, missingHistory));
        WeeklyRepresentativePrice availableHistoryStart = historyPoint(
                3L, "F00003", BASE_DATE.minusDays(28), "95.000000");
        WeeklyRepresentativePrice availableHistory = historyPoint(
                3L, "F00003", BASE_DATE, "100.000000");
        when(seriesRepository.findWeeklyRepresentativePrices(List.of(3L, 17L)))
                .thenReturn(List.of(availableHistoryStart, availableHistory));
        when(aiClient.predictSevenDays(any(AiWeeklyPricePredictionRequest.class)))
                .thenReturn(new AiWeeklyPricePredictionBatchResponse(List.of(
                        aiPrediction(3L, "100.000000", "110.000000", 0.80, 0.70, 0.75))));

        IngredientRiskRankingResponse result = service.getWeeklyRiskRankings(10);

        assertEquals(1, result.rankedCount());
        assertEquals(1, result.excludedSeriesCount());
        ArgumentCaptor<AiWeeklyPricePredictionRequest> requestCaptor =
                ArgumentCaptor.forClass(AiWeeklyPricePredictionRequest.class);
        verify(aiClient).predictSevenDays(requestCaptor.capture());
        assertEquals(List.of(3L), requestCaptor.getValue().series().stream()
                .map(request -> request.seriesId()).toList());
    }

    @Test
    void rejectsRiskRankingLimitOutsideAllowedRangeBeforeCallingDependencies() {
        ApiException exception = assertThrows(
                ApiException.class, () -> service.getWeeklyRiskRankings(0));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("INVALID_RISK_RANKING_LIMIT", exception.getCode());
        verify(predictionRepository, never()).findLatestWeeklyPredictions(any());
        verify(aiClient, never()).predictSevenDays(any(AiWeeklyPricePredictionRequest.class));
    }

    private StoredWeeklyPrediction storedPrediction(BigDecimal basePrice) {
        return storedPrediction(
                SERIES_ID, "F00993", "배추",
                basePrice.toPlainString(), "110.000000");
    }

    private StoredWeeklyPrediction storedPrediction(
            long seriesId, String ingredientCode, String ingredientName,
            String basePrice, String predictedPrice) {
        StoredWeeklyPrediction stored = mock(StoredWeeklyPrediction.class);
        when(stored.getSeriesId()).thenReturn(seriesId);
        when(stored.getIngredientCode()).thenReturn(ingredientCode);
        when(stored.getIngredientName()).thenReturn(ingredientName);
        when(stored.getStandardUnit()).thenReturn("g");
        when(stored.getBaseDate()).thenReturn(BASE_DATE);
        when(stored.getTargetDate()).thenReturn(TARGET_DATE);
        when(stored.getBasePrice()).thenReturn(new BigDecimal(basePrice));
        when(stored.getPredictedPrice()).thenReturn(new BigDecimal(predictedPrice));
        when(stored.getModelName()).thenReturn("weekly_mean_ridge");
        when(stored.getModelVersion()).thenReturn("weekly_ridge_v1");
        when(stored.getGeneratedAt()).thenReturn(Instant.parse("2026-09-23T04:00:00Z"));
        return stored;
    }

    private WeeklyRepresentativePrice historyPoint(BigDecimal price) {
        return historyPoint(BASE_DATE, price);
    }

    private WeeklyRepresentativePrice historyPoint(LocalDate priceDate, BigDecimal price) {
        return historyPoint(SERIES_ID, "F00993", priceDate, price.toPlainString());
    }

    private WeeklyRepresentativePrice historyPoint(
            long seriesId, String ingredientCode, LocalDate priceDate, String priceValue) {
        WeeklyRepresentativePrice history = mock(WeeklyRepresentativePrice.class);
        when(history.getSeriesId()).thenReturn(seriesId);
        when(history.getIngredientCode()).thenReturn(ingredientCode);
        when(history.getPriceDate()).thenReturn(priceDate);
        when(history.getRepresentativePrice()).thenReturn(new BigDecimal(priceValue));
        when(history.getStandardUnit()).thenReturn("g");
        return history;
    }

    private AiWeeklyPricePredictionBatchResponse aiResponse(
            BigDecimal basePrice, double combinedRiskScore) {
        return new AiWeeklyPricePredictionBatchResponse(List.of(
                aiPrediction(
                        SERIES_ID, basePrice.toPlainString(), "110.000000",
                        0.80, 0.70, combinedRiskScore)));
    }

    private AiWeeklyPricePredictionResponse aiPrediction(
            long seriesId, String basePrice, String predictedPrice,
            double ridgeScore, double volatilityScore, double combinedRiskScore) {
        return new AiWeeklyPricePredictionResponse(
                seriesId,
                BASE_DATE,
                TARGET_DATE,
                new BigDecimal(basePrice),
                new BigDecimal(predictedPrice),
                new BigDecimal(predictedPrice).add(new BigDecimal("5.000000")),
                "g",
                ridgeScore,
                volatilityScore,
                combinedRiskScore,
                "weekly_mean_ridge",
                "weekly_ridge_v1",
                Instant.parse("2026-09-24T04:00:00Z"));
    }
}
