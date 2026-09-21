package com.human.backend.price.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.dto.response.PriceTargetCollectionResult;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.util.KamisPriceValueParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final KamisPriceApiClient kamisPriceApiClient;
    private final PriceSeriesRepository priceSeriesRepository;
    private final PriceStorageService priceStorageService;
    private final KamisPriceValueParser valueParser;

    public PriceService(
            KamisPriceApiClient kamisPriceApiClient,
            PriceSeriesRepository priceSeriesRepository,
            PriceStorageService priceStorageService,
            KamisPriceValueParser valueParser) {
        this.kamisPriceApiClient = kamisPriceApiClient;
        this.priceSeriesRepository = priceSeriesRepository;
        this.priceStorageService = priceStorageService;
        this.valueParser = valueParser;
    }

    public PriceCollectionResult collectOne(
            String ingredientCode, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        List<PriceSeries> seriesList = priceSeriesRepository
                .findAllActiveKamisCollectionTargetsByIngredientCode(ingredientCode);
        if (seriesList.isEmpty()) {
            throw new IllegalArgumentException(
                    "활성 KAMIS 가격 시계열을 찾을 수 없습니다. 식재료 코드: " + ingredientCode);
        }

        List<PriceTargetCollectionResult> targetResults = collectTargets(
                seriesList, startDate, endDate);

        return aggregate(ingredientCode, startDate, endDate, targetResults);
    }

    public List<PriceTargetCollectionResult> collectAll(
            LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        List<PriceSeries> seriesList = priceSeriesRepository
                .findAllActiveKamisCollectionTargets();
        return collectTargets(seriesList, startDate, endDate);
    }

    private List<PriceTargetCollectionResult> collectTargets(
            List<PriceSeries> seriesList, LocalDate startDate, LocalDate endDate) {
        List<PriceTargetCollectionResult> targetResults = new ArrayList<>();
        for (PriceSeries series : seriesList) {
            try {
                targetResults.add(collectSeries(series, startDate, endDate));
            } catch (RuntimeException exception) {
                log.warn(
                        "KAMIS 가격 시계열 수집에 실패했습니다. 시계열 ID={}, 품목 코드={}, "
                                + "품종 코드={}, 등급 코드={}, 사유={}",
                        series.getId(), series.getSourceItemCode(), series.getSourceKindCode(),
                        series.getSourceRankCode(), exception.getMessage());
                targetResults.add(failedResult(series, exception));
            }
        }
        return List.copyOf(targetResults);
    }

    private PriceTargetCollectionResult collectSeries(
            PriceSeries series, LocalDate startDate, LocalDate endDate) {
        KamisPriceResponseDto response = kamisPriceApiClient.getPriceData(series, startDate, endDate);

        if (response == null
                || response.data() == null
                || !"000".equals(response.data().errorCode())
                || response.data().items() == null) {
            String errorCode = response == null || response.data() == null
                    ? "응답 없음"
                    : response.data().errorCode();
            throw new IllegalStateException("KAMIS 가격 조회에 실패했습니다. 오류 코드=" + errorCode);
        }

        List<KamisPriceItemDto> actualItems = response.data().items().stream()
                .filter(item -> item.itemName() != null)
                .filter(item -> item.marketName() != null)
                .toList();

        List<KamisPriceItemDto> inRangeItems = actualItems.stream()
                .filter(item -> isInRequestedRange(item, startDate, endDate))
                .toList();
        int outOfRangeRowsSkipped = actualItems.size() - inRangeItems.size();

        PriceStorageService.StoreResult stored = priceStorageService.store(series, inRangeItems);
        return new PriceTargetCollectionResult(
                series.getId(), series.getSourceCategoryCode(), series.getSourceItemCode(),
                series.getSourceKindCode(), series.getVariety(),
                series.getSourceRankCode(), series.getGrade(), true, null,
                actualItems.size(), outOfRangeRowsSkipped,
                stored.seriesCreated(), stored.pricesInserted(),
                stored.duplicatesSkipped(), stored.invalidRowsSkipped());
    }

    private PriceTargetCollectionResult failedResult(
            PriceSeries series, RuntimeException exception) {
        return new PriceTargetCollectionResult(
                series.getId(), series.getSourceCategoryCode(), series.getSourceItemCode(),
                series.getSourceKindCode(), series.getVariety(),
                series.getSourceRankCode(), series.getGrade(), false, exception.getMessage(),
                0, 0, 0, 0, 0, 0);
    }

    private PriceCollectionResult aggregate(
            String ingredientCode, LocalDate startDate, LocalDate endDate,
            List<PriceTargetCollectionResult> targetResults) {
        int succeeded = (int) targetResults.stream()
                .filter(PriceTargetCollectionResult::success)
                .count();
        return new PriceCollectionResult(
                ingredientCode, startDate, endDate,
                targetResults.size(), succeeded, targetResults.size() - succeeded,
                targetResults.stream().mapToInt(PriceTargetCollectionResult::fetchedRows).sum(),
                targetResults.stream().mapToInt(PriceTargetCollectionResult::outOfRangeRowsSkipped).sum(),
                targetResults.stream().mapToInt(PriceTargetCollectionResult::seriesCreated).sum(),
                targetResults.stream().mapToInt(PriceTargetCollectionResult::pricesInserted).sum(),
                targetResults.stream().mapToInt(PriceTargetCollectionResult::duplicatesSkipped).sum(),
                targetResults.stream().mapToInt(PriceTargetCollectionResult::invalidRowsSkipped).sum(),
                List.copyOf(targetResults));
    }

    private boolean isInRequestedRange(
            KamisPriceItemDto item, LocalDate startDate, LocalDate endDate) {
        try {
            LocalDate priceDate = valueParser.parseDate(item.year(), item.regDay());
            return !priceDate.isBefore(startDate) && !priceDate.isAfter(endDate);
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("가격 수집 시작일과 종료일이 모두 필요합니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("가격 수집 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new IllegalArgumentException("KAMIS 가격 조회 기간은 1년을 초과할 수 없습니다.");
        }
    }
}
