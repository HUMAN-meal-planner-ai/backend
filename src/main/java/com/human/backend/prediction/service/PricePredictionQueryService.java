package com.human.backend.prediction.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.human.backend.global.exception.ApiException;
import com.human.backend.integration.aiserver.AiPricePredictionClient;
import com.human.backend.integration.aiserver.AiPricePredictionClientException;
import com.human.backend.prediction.dto.request.AiWeeklyPriceHistoryPoint;
import com.human.backend.prediction.dto.request.AiWeeklyPricePredictionRequest;
import com.human.backend.prediction.dto.request.AiWeeklyPriceSeriesRequest;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionResponse;
import com.human.backend.prediction.dto.response.ActualPriceChartPoint;
import com.human.backend.prediction.dto.response.IngredientRiskRankItem;
import com.human.backend.prediction.dto.response.IngredientRiskRankingResponse;
import com.human.backend.prediction.dto.response.PricePredictionChartResponse;
import com.human.backend.prediction.dto.response.WeeklyPricePredictionRiskResponse;
import com.human.backend.prediction.dto.response.WeeklyPredictionChartPoint;
import com.human.backend.prediction.repository.PricePredictionRepository;
import com.human.backend.prediction.repository.PricePredictionRepository.StoredWeeklyPrediction;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

@Service
public class PricePredictionQueryService {

    static final double RISK_THRESHOLD = 0.75;
    private static final int INCREASE_RATE_SCALE = 6;
    private static final BigDecimal STORED_PRICE_TOLERANCE = new BigDecimal("0.0000005");

    private final PricePredictionRepository predictionRepository;
    private final PriceSeriesRepository seriesRepository;
    private final AiPricePredictionClient aiClient;
    private final Set<Long> supportedWeeklySeriesIds;

    public PricePredictionQueryService(
            PricePredictionRepository predictionRepository,
            PriceSeriesRepository seriesRepository,
            AiPricePredictionClient aiClient,
            @Value("${ai.server.weekly-supported-series-ids:}")
            String supportedWeeklySeriesIds) {
        this.predictionRepository = predictionRepository;
        this.seriesRepository = seriesRepository;
        this.aiClient = aiClient;
        this.supportedWeeklySeriesIds = parseSeriesIds(supportedWeeklySeriesIds);
    }

    public WeeklyPricePredictionRiskResponse getLatestWeeklyPrediction(long seriesId) {
        if (!supportedWeeklySeriesIds.contains(seriesId)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "WEEKLY_PRICE_PREDICTION_UNSUPPORTED_SERIES",
                    "현재 주간 가격예측 모델이 지원하지 않는 시계열입니다.");
        }

        StoredWeeklyPrediction stored = predictionRepository.findLatestWeeklyPrediction(seriesId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WEEKLY_PRICE_PREDICTION_NOT_FOUND",
                        "저장된 주간 가격예측을 찾을 수 없습니다."));

        List<WeeklyRepresentativePrice> history = seriesRepository
                .findWeeklyRepresentativePrices(List.of(seriesId)).stream()
                .filter(price -> !price.getPriceDate().isAfter(stored.getBaseDate()))
                .toList();
        boolean containsStoredBaseDate = history.stream()
                .anyMatch(price -> price.getPriceDate().equals(stored.getBaseDate()));
        if (history.isEmpty() || !containsStoredBaseDate) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "WEEKLY_PRICE_HISTORY_NOT_FOUND",
                    "저장된 예측 기준일의 가격 이력을 찾을 수 없습니다.");
        }

        AiWeeklyPriceSeriesRequest seriesRequest = new AiWeeklyPriceSeriesRequest(
                seriesId,
                stored.getIngredientCode(),
                stored.getStandardUnit(),
                history.stream()
                        .map(price -> new AiWeeklyPriceHistoryPoint(
                                price.getPriceDate(), price.getRepresentativePrice()))
                        .toList());

        AiWeeklyPricePredictionBatchResponse aiResponse;
        try {
            aiResponse = aiClient.predictSevenDays(
                    new AiWeeklyPricePredictionRequest(List.of(seriesRequest)));
        } catch (AiPricePredictionClientException exception) {
            throw toApiException(exception);
        }

        AiWeeklyPricePredictionResponse riskPrediction = validateRiskPrediction(
                seriesId, stored, aiResponse);
        BigDecimal expectedIncreaseRate = calculateIncreaseRate(
                stored.getBasePrice(), stored.getPredictedPrice());
        double combinedRiskScore = riskPrediction.combinedRiskScore();

        return new WeeklyPricePredictionRiskResponse(
                stored.getSeriesId(),
                stored.getIngredientCode(),
                stored.getIngredientName(),
                stored.getStandardUnit(),
                stored.getBaseDate(),
                stored.getTargetDate(),
                stored.getBasePrice(),
                stored.getPredictedPrice(),
                expectedIncreaseRate,
                combinedRiskScore,
                RISK_THRESHOLD,
                combinedRiskScore >= RISK_THRESHOLD,
                stored.getModelName(),
                stored.getModelVersion(),
                stored.getGeneratedAt());
    }

    public PricePredictionChartResponse getWeeklyPredictionChart(
            long seriesId, LocalDate startDate, LocalDate endDate) {
        requireSupportedSeries(seriesId);
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PRICE_CHART_PERIOD",
                    "차트 조회 시작일은 종료일보다 늦을 수 없습니다.");
        }

        StoredWeeklyPrediction stored = predictionRepository.findLatestWeeklyPrediction(seriesId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "WEEKLY_PRICE_PREDICTION_NOT_FOUND",
                        "저장된 주간 가격 예측을 찾을 수 없습니다."));
        LocalDate actualEndDate = endDate.isAfter(stored.getBaseDate())
                ? stored.getBaseDate()
                : endDate;
        if (startDate.isAfter(actualEndDate)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "PRICE_CHART_HISTORY_NOT_FOUND",
                    "예측 기준일까지의 실제 가격 조회 기간이 없습니다.");
        }

        List<WeeklyRepresentativePrice> history = seriesRepository
                .findDailyRepresentativePrices(seriesId, startDate, actualEndDate);
        if (history.isEmpty()) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "PRICE_CHART_HISTORY_NOT_FOUND",
                    "차트에 표시할 실제 가격 이력이 없습니다.");
        }

        return new PricePredictionChartResponse(
                stored.getSeriesId(),
                stored.getIngredientCode(),
                stored.getIngredientName(),
                stored.getStandardUnit(),
                startDate,
                actualEndDate,
                history.stream()
                        .map(value -> new ActualPriceChartPoint(
                                value.getPriceDate(), value.getRepresentativePrice()))
                        .toList(),
                new WeeklyPredictionChartPoint(
                        stored.getBaseDate(),
                        stored.getTargetDate(),
                        stored.getBasePrice(),
                        stored.getPredictedPrice(),
                        "NEXT_7_DAY_AVERAGE"));
    }

    public IngredientRiskRankingResponse getWeeklyRiskRankings(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RISK_RANKING_LIMIT",
                    "위험 순위 조회 개수는 1 이상 100 이하여야 합니다.");
        }
        if (supportedWeeklySeriesIds.isEmpty()) {
            return new IngredientRiskRankingResponse(limit, 0, 0, List.of());
        }

        List<Long> supportedIds = supportedWeeklySeriesIds.stream().sorted().toList();
        Map<Long, StoredWeeklyPrediction> storedBySeries = predictionRepository
                .findLatestWeeklyPredictions(supportedIds).stream()
                .collect(Collectors.toMap(
                        StoredWeeklyPrediction::getSeriesId,
                        Function.identity(),
                        (first, ignored) -> first));
        Map<Long, List<WeeklyRepresentativePrice>> historyBySeries = seriesRepository
                .findWeeklyRepresentativePrices(supportedIds).stream()
                .collect(Collectors.groupingBy(WeeklyRepresentativePrice::getSeriesId));

        List<AiWeeklyPriceSeriesRequest> requests = new ArrayList<>();
        for (Long seriesId : supportedIds) {
            StoredWeeklyPrediction stored = storedBySeries.get(seriesId);
            if (stored == null) {
                continue;
            }
            List<WeeklyRepresentativePrice> history = historyBySeries
                    .getOrDefault(seriesId, List.of()).stream()
                    .filter(value -> !value.getPriceDate().isAfter(stored.getBaseDate()))
                    .toList();
            boolean containsBaseDate = history.stream()
                    .anyMatch(value -> value.getPriceDate().equals(stored.getBaseDate()));
            boolean containsRequiredHistory = history.size() >= 2
                    && history.stream().anyMatch(value -> !value.getPriceDate()
                            .isAfter(stored.getBaseDate().minusDays(28)))
                    && history.stream().allMatch(value ->
                            value.getRepresentativePrice() != null
                                    && value.getRepresentativePrice().compareTo(BigDecimal.ZERO) > 0);
            if (!containsBaseDate || !containsRequiredHistory) {
                continue;
            }
            requests.add(new AiWeeklyPriceSeriesRequest(
                    seriesId,
                    stored.getIngredientCode(),
                    stored.getStandardUnit(),
                    history.stream()
                            .map(value -> new AiWeeklyPriceHistoryPoint(
                                    value.getPriceDate(), value.getRepresentativePrice()))
                            .toList()));
        }

        if (requests.isEmpty()) {
            return new IngredientRiskRankingResponse(
                    limit, 0, supportedIds.size(), List.of());
        }

        AiWeeklyPricePredictionBatchResponse response;
        try {
            response = aiClient.predictSevenDays(new AiWeeklyPricePredictionRequest(requests));
        } catch (AiPricePredictionClientException exception) {
            throw toApiException(exception);
        }
        if (response == null || response.predictions() == null) {
            throw invalidAiResponse("AI 주간 위험 순위 응답이 비어 있습니다.");
        }

        List<RiskCandidate> candidates = response.predictions().stream()
                .filter(prediction -> prediction != null && prediction.seriesId() != null)
                .map(prediction -> new RiskCandidate(
                        storedBySeries.get(prediction.seriesId()), prediction))
                .filter(candidate -> isValidRankingCandidate(candidate, requests))
                .sorted(Comparator
                        .comparingDouble((RiskCandidate value) -> value.prediction().ridgeScore())
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(
                                (RiskCandidate value) -> value.prediction().combinedRiskScore())
                                .reversed())
                        .thenComparing(value -> value.stored().getSeriesId()))
                .toList();

        Map<String, RiskCandidate> bestByIngredient = new LinkedHashMap<>();
        candidates.forEach(candidate -> bestByIngredient.putIfAbsent(
                candidate.stored().getIngredientCode(), candidate));
        long validSeriesCount = candidates.stream()
                .map(candidate -> candidate.stored().getSeriesId())
                .distinct()
                .count();
        List<RiskCandidate> rankedCandidates = bestByIngredient.values().stream()
                .limit(limit)
                .toList();
        List<IngredientRiskRankItem> rankings = new ArrayList<>();
        for (int index = 0; index < rankedCandidates.size(); index++) {
            RiskCandidate candidate = rankedCandidates.get(index);
            StoredWeeklyPrediction stored = candidate.stored();
            AiWeeklyPricePredictionResponse prediction = candidate.prediction();
            rankings.add(new IngredientRiskRankItem(
                    index + 1,
                    stored.getSeriesId(),
                    stored.getIngredientCode(),
                    stored.getIngredientName(),
                    stored.getStandardUnit(),
                    stored.getBaseDate(),
                    stored.getTargetDate(),
                    stored.getBasePrice(),
                    stored.getPredictedPrice(),
                    prediction.predictedMaxPrice(),
                    calculateIncreaseRate(stored.getBasePrice(), stored.getPredictedPrice()),
                    prediction.ridgeScore(),
                    prediction.volatilityScore(),
                    prediction.combinedRiskScore(),
                    prediction.combinedRiskScore() >= RISK_THRESHOLD));
        }

        return new IngredientRiskRankingResponse(
                limit,
                rankings.size(),
                Math.max(0, supportedIds.size() - (int) validSeriesCount),
                List.copyOf(rankings));
    }

    private boolean isValidRankingCandidate(
            RiskCandidate candidate, List<AiWeeklyPriceSeriesRequest> requests) {
        StoredWeeklyPrediction stored = candidate.stored();
        AiWeeklyPricePredictionResponse prediction = candidate.prediction();
        if (stored == null
                || requests.stream().noneMatch(request -> request.seriesId().equals(prediction.seriesId()))
                || prediction.baseDate() == null || prediction.targetDate() == null
                || prediction.basePrice() == null || prediction.predictedPrice() == null
                || prediction.predictedMaxPrice() == null || prediction.ridgeScore() == null
                || prediction.volatilityScore() == null || prediction.combinedRiskScore() == null
                || prediction.modelVersion() == null) {
            return false;
        }
        return prediction.baseDate().equals(stored.getBaseDate())
                && prediction.targetDate().equals(stored.getTargetDate())
                && prediction.modelVersion().equals(stored.getModelVersion())
                && !priceDiffers(prediction.basePrice(), stored.getBasePrice())
                && !priceDiffers(prediction.predictedPrice(), stored.getPredictedPrice())
                && isScore(prediction.ridgeScore())
                && isScore(prediction.volatilityScore())
                && isScore(prediction.combinedRiskScore());
    }

    private boolean isScore(Double score) {
        return score != null && Double.isFinite(score) && score >= 0.0 && score <= 1.0;
    }

    private void requireSupportedSeries(long seriesId) {
        if (!supportedWeeklySeriesIds.contains(seriesId)) {
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "WEEKLY_PRICE_PREDICTION_UNSUPPORTED_SERIES",
                    "현재 주간 가격예측 모델이 지원하지 않는 시계열입니다.");
        }
    }

    private record RiskCandidate(
            StoredWeeklyPrediction stored,
            AiWeeklyPricePredictionResponse prediction) {
    }

    private AiWeeklyPricePredictionResponse validateRiskPrediction(
            long seriesId,
            StoredWeeklyPrediction stored,
            AiWeeklyPricePredictionBatchResponse response) {
        if (response == null || response.predictions() == null
                || response.predictions().size() != 1) {
            throw invalidAiResponse("AI 주간 위험도 응답은 정확히 1건이어야 합니다.");
        }

        AiWeeklyPricePredictionResponse prediction = response.predictions().get(0);
        if (prediction == null || prediction.seriesId() == null
                || prediction.baseDate() == null || prediction.targetDate() == null
                || prediction.basePrice() == null || prediction.predictedPrice() == null
                || prediction.combinedRiskScore() == null
                || prediction.modelVersion() == null) {
            throw invalidAiResponse("AI 주간 위험도 응답에 필수 값이 없습니다.");
        }

        if (prediction.seriesId() != seriesId
                || !prediction.baseDate().equals(stored.getBaseDate())
                || !prediction.targetDate().equals(stored.getTargetDate())
                || !prediction.modelVersion().equals(stored.getModelVersion())
                || priceDiffers(prediction.basePrice(), stored.getBasePrice())
                || priceDiffers(prediction.predictedPrice(), stored.getPredictedPrice())) {
            throw invalidAiResponse("AI 주간 위험도 응답이 저장된 예측과 일치하지 않습니다.");
        }

        double score = prediction.combinedRiskScore();
        if (!Double.isFinite(score) || score < 0.0 || score > 1.0) {
            throw invalidAiResponse("AI 주간 위험도 점수가 허용 범위를 벗어났습니다.");
        }
        return prediction;
    }

    private BigDecimal calculateIncreaseRate(BigDecimal basePrice, BigDecimal predictedPrice) {
        if (basePrice == null || predictedPrice == null
                || basePrice.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return predictedPrice.subtract(basePrice)
                .divide(basePrice, INCREASE_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private boolean priceDiffers(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(STORED_PRICE_TOLERANCE) > 0;
    }

    private Set<Long> parseSeriesIds(String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private ApiException toApiException(AiPricePredictionClientException exception) {
        return switch (exception.getFailureType()) {
            case TIMEOUT -> new ApiException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "AI_PRICE_PREDICTION_TIMEOUT",
                    exception.getMessage());
            case UNAVAILABLE -> new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_PRICE_PREDICTION_UNAVAILABLE",
                    exception.getMessage());
            case NOT_FOUND, SERVER_ERROR, INVALID_RESPONSE -> new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_PRICE_PREDICTION_INVALID_RESPONSE",
                    exception.getMessage());
        };
    }

    private ApiException invalidAiResponse(String message) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_PRICE_PREDICTION_INVALID_RESPONSE",
                message);
    }
}
