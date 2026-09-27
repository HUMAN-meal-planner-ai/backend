package com.human.backend.prediction.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.human.backend.global.exception.ApiException;
import com.human.backend.integration.aiserver.AiPricePredictionClient;
import com.human.backend.integration.aiserver.AiPricePredictionClientException;
import com.human.backend.prediction.dto.request.AiWeeklyPriceHistoryPoint;
import com.human.backend.prediction.dto.request.AiWeeklyPricePredictionRequest;
import com.human.backend.prediction.dto.request.AiWeeklyPriceSeriesRequest;
import com.human.backend.prediction.dto.response.AiPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiPricePredictionResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionResponse;
import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.PricePredictionTarget;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

@Service
public class PricePredictionService {

    private final PriceSeriesRepository seriesRepository;
    private final AiPricePredictionClient client;
    private final PricePredictionStorageService storageService;
    private final Set<Long> supportedWeeklySeriesIds;

    public PricePredictionService(
            PriceSeriesRepository seriesRepository,
            AiPricePredictionClient client,
            PricePredictionStorageService storageService,
            @Value("${ai.server.weekly-supported-series-ids:}")
            String supportedWeeklySeriesIds) {
        this.seriesRepository = seriesRepository;
        this.client = client;
        this.storageService = storageService;
        this.supportedWeeklySeriesIds = supportedWeeklySeriesIds.isBlank()
                ? Set.of()
                : java.util.Arrays.stream(supportedWeeklySeriesIds.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .map(Long::valueOf)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public PricePredictionCollectionResult generateNextPredictions() {
        return generatePredictions(1, client::predictNext);
    }

    public PricePredictionCollectionResult generateSevenDayPredictions() {
        List<PricePredictionTarget> activeTargets =
                seriesRepository.findAllActivePredictionTargets();
        if (activeTargets.isEmpty()) {
            return new PricePredictionCollectionResult(0, 0, 0, 0);
        }

        List<Long> activeSeriesIds = activeTargets.stream()
                .map(PricePredictionTarget::getSeriesId)
                .filter(supportedWeeklySeriesIds::contains)
                .toList();
        if (activeSeriesIds.isEmpty()) {
            return new PricePredictionCollectionResult(0, 0, 0, 0);
        }
        List<AiWeeklyPriceSeriesRequest> seriesRequests = buildWeeklySeriesRequests(
                seriesRepository.findWeeklyRepresentativePrices(activeSeriesIds));
        if (seriesRequests.isEmpty()) {
            return new PricePredictionCollectionResult(0, 0, 0, 0);
        }

        AiWeeklyPricePredictionBatchResponse response;
        try {
            response = client.predictSevenDays(
                    new AiWeeklyPricePredictionRequest(seriesRequests));
        } catch (AiPricePredictionClientException exception) {
            throw toApiException(exception);
        }

        List<AiPricePredictionResponse> predictions = validateWeeklyResponse(
                seriesRequests, response);
        PricePredictionStorageService.StoreResult stored = storageService.store(predictions);
        return new PricePredictionCollectionResult(
                seriesRequests.size(), predictions.size(),
                stored.inserted(), stored.duplicatesSkipped());
    }

    private List<AiWeeklyPriceSeriesRequest> buildWeeklySeriesRequests(
            List<WeeklyRepresentativePrice> representativePrices) {
        Map<Long, List<WeeklyRepresentativePrice>> pricesBySeries =
                representativePrices.stream().collect(
                        java.util.stream.Collectors.groupingBy(
                                WeeklyRepresentativePrice::getSeriesId,
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));

        return pricesBySeries.values().stream()
                .map(prices -> {
                    WeeklyRepresentativePrice identity = prices.get(0);
                    List<AiWeeklyPriceHistoryPoint> history = prices.stream()
                            .map(price -> new AiWeeklyPriceHistoryPoint(
                                    price.getPriceDate(), price.getRepresentativePrice()))
                            .toList();
                    return new AiWeeklyPriceSeriesRequest(
                            identity.getSeriesId(),
                            identity.getIngredientCode(),
                            identity.getStandardUnit(),
                            history);
                })
                .toList();
    }

    private List<AiPricePredictionResponse> validateWeeklyResponse(
            List<AiWeeklyPriceSeriesRequest> requests,
            AiWeeklyPricePredictionBatchResponse response) {
        if (response == null || response.predictions() == null) {
            throw invalidResponse("AI 주간 가격예측 서버 응답에 predictions가 없습니다.");
        }

        Map<Long, WeeklyPredictionTarget> targets = new HashMap<>();
        for (AiWeeklyPriceSeriesRequest request : requests) {
            AiWeeklyPriceHistoryPoint latest = request.prices()
                    .get(request.prices().size() - 1);
            targets.put(request.seriesId(), new WeeklyPredictionTarget(
                    latest.priceDate(), latest.representativePrice(), request.standardUnit()));
        }

        Set<Long> receivedSeriesIds = new HashSet<>();
        List<AiPricePredictionResponse> storageResponses = response.predictions().stream()
                .map(prediction -> validateWeeklyPrediction(
                        prediction, targets, receivedSeriesIds))
                .toList();
        if (!receivedSeriesIds.equals(targets.keySet())) {
            throw invalidResponse("AI 주간 가격예측 서버 응답의 시계열 목록이 요청과 일치하지 않습니다.");
        }
        return storageResponses;
    }

    private AiPricePredictionResponse validateWeeklyPrediction(
            AiWeeklyPricePredictionResponse prediction,
            Map<Long, WeeklyPredictionTarget> targets,
            Set<Long> receivedSeriesIds) {
        if (prediction == null || prediction.seriesId() == null) {
            throw invalidResponse("AI 주간 가격예측 서버 응답에 시계열 ID가 없습니다.");
        }
        WeeklyPredictionTarget target = targets.get(prediction.seriesId());
        if (target == null || !receivedSeriesIds.add(prediction.seriesId())) {
            throw invalidResponse("AI 주간 가격예측 서버 응답에 요청하지 않았거나 중복된 시계열이 있습니다.");
        }
        if (prediction.baseDate() == null || prediction.targetDate() == null
                || prediction.basePrice() == null || prediction.predictedPrice() == null
                || prediction.predictedMaxPrice() == null || prediction.standardUnit() == null
                || prediction.ridgeScore() == null || prediction.volatilityScore() == null
                || prediction.combinedRiskScore() == null || prediction.modelName() == null
                || prediction.modelName().isBlank() || prediction.modelVersion() == null
                || prediction.modelVersion().isBlank() || prediction.generatedAt() == null) {
            throw invalidResponse("AI 주간 가격예측 서버 응답에 필수 값이 없습니다.");
        }
        if (!prediction.baseDate().equals(target.baseDate())
                || !prediction.targetDate().equals(target.baseDate().plusDays(7))
                || prediction.basePrice().compareTo(target.basePrice()) != 0) {
            throw invalidResponse("AI 주간 가격예측 기준 가격 또는 목표일이 요청과 일치하지 않습니다.");
        }
        if (!prediction.standardUnit().equalsIgnoreCase(target.standardUnit())) {
            throw invalidResponse("AI 주간 가격예측 기준 단위가 식재료 기준 단위와 일치하지 않습니다.");
        }
        if (prediction.basePrice().compareTo(BigDecimal.ZERO) <= 0
                || prediction.predictedPrice().compareTo(BigDecimal.ZERO) < 0
                || prediction.predictedMaxPrice().compareTo(BigDecimal.ZERO) < 0
                || !isScore(prediction.ridgeScore())
                || !isScore(prediction.volatilityScore())
                || !isScore(prediction.combinedRiskScore())) {
            throw invalidResponse("AI 주간 가격예측 값이 허용 범위를 벗어났습니다.");
        }
        if (prediction.modelName().length() > 60 || prediction.modelVersion().length() > 80) {
            throw invalidResponse("AI 주간 가격예측 모델 정보가 저장 가능한 길이를 초과했습니다.");
        }
        return prediction.toStorageResponse();
    }

    private boolean isScore(Double score) {
        return Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    private PricePredictionCollectionResult generatePredictions(
            int horizonDays,
            Function<List<Long>, AiPricePredictionBatchResponse> predictionCall) {
        List<PricePredictionTarget> targets = seriesRepository.findAllActivePredictionTargets();
        if (targets.isEmpty()) {
            return new PricePredictionCollectionResult(0, 0, 0, 0);
        }

        List<Long> seriesIds = targets.stream().map(PricePredictionTarget::getSeriesId).toList();
        AiPricePredictionBatchResponse response;
        try {
            response = predictionCall.apply(seriesIds);
        } catch (AiPricePredictionClientException exception) {
            throw toApiException(exception);
        }

        List<AiPricePredictionResponse> predictions = validateResponse(
                targets, response, horizonDays);
        PricePredictionStorageService.StoreResult stored = storageService.store(predictions);
        return new PricePredictionCollectionResult(
                targets.size(), predictions.size(), stored.inserted(), stored.duplicatesSkipped());
    }

    private List<AiPricePredictionResponse> validateResponse(
            List<PricePredictionTarget> targets,
            AiPricePredictionBatchResponse response,
            int horizonDays) {
        if (response == null || response.predictions() == null) {
            throw invalidResponse("AI 가격예측 서버 응답에 predictions가 없습니다.");
        }

        Map<Long, PricePredictionTarget> targetById = new HashMap<>();
        for (PricePredictionTarget target : targets) {
            targetById.put(target.getSeriesId(), target);
        }
        Set<PredictionKey> expectedKeys = new HashSet<>();
        for (PricePredictionTarget target : targets) {
            for (int daysAhead = 1; daysAhead <= horizonDays; daysAhead++) {
                expectedKeys.add(new PredictionKey(
                        target.getSeriesId(), target.getBaseDate().plusDays(daysAhead)));
            }
        }
        Set<PredictionKey> receivedKeys = new HashSet<>();
        for (AiPricePredictionResponse prediction : response.predictions()) {
            validatePrediction(prediction, targetById, expectedKeys, receivedKeys);
        }
        if (!receivedKeys.equals(expectedKeys)) {
            throw invalidResponse("AI 가격예측 서버 응답의 시계열 목록이 요청과 일치하지 않습니다.");
        }
        return List.copyOf(response.predictions());
    }

    private void validatePrediction(
            AiPricePredictionResponse prediction,
            Map<Long, PricePredictionTarget> targetById,
            Set<PredictionKey> expectedKeys,
            Set<PredictionKey> receivedKeys) {
        if (prediction == null || prediction.seriesId() == null) {
            throw invalidResponse("AI 가격예측 서버 응답에 시계열 ID가 없습니다.");
        }
        PricePredictionTarget target = targetById.get(prediction.seriesId());
        PredictionKey predictionKey = new PredictionKey(
                prediction.seriesId(), prediction.targetDate());
        if (target == null || !expectedKeys.contains(predictionKey)
                || !receivedKeys.add(predictionKey)) {
            throw invalidResponse("AI 가격예측 서버 응답에 요청하지 않았거나 중복된 시계열이 있습니다.");
        }
        if (prediction.baseDate() == null || prediction.targetDate() == null
                || prediction.basePrice() == null || prediction.predictedPrice() == null
                || prediction.standardUnit() == null || prediction.modelName() == null
                || prediction.modelName().isBlank() || prediction.modelVersion() == null
                || prediction.modelVersion().isBlank() || prediction.generatedAt() == null) {
            throw invalidResponse("AI 가격예측 서버 응답에 필수 값이 없습니다.");
        }
        long horizonDays = ChronoUnit.DAYS.between(
                prediction.baseDate(), prediction.targetDate());
        if (horizonDays < 1 || horizonDays > 7) {
            throw invalidResponse("AI 가격예측 목표일은 기준일 이후 7일 이내여야 합니다.");
        }
        if (!prediction.baseDate().equals(target.getBaseDate())
                || prediction.basePrice().compareTo(target.getBasePrice()) != 0) {
            throw invalidResponse("AI 가격예측 기준 가격이 최신 실측 가격과 일치하지 않습니다.");
        }
        if (!prediction.standardUnit().equalsIgnoreCase(target.getStandardUnit())) {
            throw invalidResponse("AI 가격예측 기준 단위가 식재료 기준 단위와 일치하지 않습니다.");
        }
        if (prediction.basePrice().compareTo(BigDecimal.ZERO) <= 0
                || prediction.predictedPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw invalidResponse("AI 가격예측 가격 값이 허용 범위를 벗어났습니다.");
        }
        if (prediction.modelName().length() > 60 || prediction.modelVersion().length() > 80) {
            throw invalidResponse("AI 가격예측 모델 정보가 저장 가능한 길이를 초과했습니다.");
        }
    }

    private ApiException toApiException(AiPricePredictionClientException exception) {
        return switch (exception.getFailureType()) {
            case TIMEOUT -> new ApiException(HttpStatus.GATEWAY_TIMEOUT,
                    "AI_PRICE_PREDICTION_TIMEOUT", exception.getMessage());
            case UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PRICE_PREDICTION_UNAVAILABLE", exception.getMessage());
            case NOT_FOUND, SERVER_ERROR, INVALID_RESPONSE -> new ApiException(
                    HttpStatus.BAD_GATEWAY, "AI_PRICE_PREDICTION_FAILED", exception.getMessage());
        };
    }

    private ApiException invalidResponse(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY,
                "AI_PRICE_PREDICTION_INVALID_RESPONSE", message);
    }

    private record PredictionKey(Long seriesId, LocalDate targetDate) {
    }

    private record WeeklyPredictionTarget(
            LocalDate baseDate,
            BigDecimal basePrice,
            String standardUnit) {
    }
}
