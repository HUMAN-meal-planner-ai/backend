package com.human.backend.price.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import com.human.backend.price.config.KamisPriceCatalog;
import com.human.backend.price.config.KamisPriceTarget;
import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.dto.response.PriceTargetCollectionResult;
import com.human.backend.price.util.KamisPriceValueParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PriceService {
    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final KamisPriceApiClient kamisPriceApiClient;
    private final KamisPriceCatalog priceCatalog;
    private final PriceStorageService priceStorageService;
    private final KamisPriceValueParser valueParser;

    public PriceService(
            KamisPriceApiClient kamisPriceApiClient,
            KamisPriceCatalog priceCatalog,
            PriceStorageService priceStorageService,
            KamisPriceValueParser valueParser) {
        this.kamisPriceApiClient = kamisPriceApiClient;
        this.priceCatalog = priceCatalog;
        this.priceStorageService = priceStorageService;
        this.valueParser = valueParser;
    }

    public PriceCollectionResult collectOne(
            String ingredientCode, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        List<KamisPriceTarget> targets = priceCatalog.findAllByIngredientCode(ingredientCode);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException(
                    "KAMIS collection target not found for ingredient_code: " + ingredientCode);
        }

        List<PriceTargetCollectionResult> targetResults = new ArrayList<>();
        for (KamisPriceTarget target : targets) {
            try {
                targetResults.add(collectTarget(target, startDate, endDate));
            } catch (RuntimeException exception) {
                log.warn(
                        "KAMIS target collection failed. ingredientCode={}, itemCode={}, "
                                + "kindCode={}, rankCode={}, reason={}",
                        target.ingredientCode(), target.itemCode(), target.kindCode(),
                        target.rankCode(), exception.getMessage());
                targetResults.add(failedResult(target, exception));
            }
        }

        return aggregate(ingredientCode, startDate, endDate, targetResults);
    }

    private PriceTargetCollectionResult collectTarget(
            KamisPriceTarget target, LocalDate startDate, LocalDate endDate) {
        KamisPriceResponseDto response = kamisPriceApiClient.getPriceData(target, startDate, endDate);

        if (response == null
                || response.data() == null
                || !"000".equals(response.data().errorCode())
                || response.data().items() == null) {
            String errorCode = response == null || response.data() == null
                    ? "NO_RESPONSE"
                    : response.data().errorCode();
            throw new IllegalStateException("KAMIS price lookup failed: error_code=" + errorCode);
        }

        List<KamisPriceItemDto> actualItems = response.data().items().stream()
                .filter(item -> item.itemName() != null)
                .filter(item -> item.marketName() != null)
                .toList();

        List<KamisPriceItemDto> inRangeItems = actualItems.stream()
                .filter(item -> isInRequestedRange(item, startDate, endDate))
                .toList();
        int outOfRangeRowsSkipped = actualItems.size() - inRangeItems.size();

        PriceStorageService.StoreResult stored = priceStorageService.store(target, inRangeItems);
        return new PriceTargetCollectionResult(
                target.itemCode(), target.kindCode(), target.kindName(),
                target.rankCode(), target.rankName(), true, null,
                actualItems.size(), outOfRangeRowsSkipped,
                stored.seriesCreated(), stored.pricesInserted(),
                stored.duplicatesSkipped(), stored.invalidRowsSkipped());
    }

    private PriceTargetCollectionResult failedResult(
            KamisPriceTarget target, RuntimeException exception) {
        return new PriceTargetCollectionResult(
                target.itemCode(), target.kindCode(), target.kindName(),
                target.rankCode(), target.rankName(), false, exception.getMessage(),
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
            throw new IllegalArgumentException("Collection start and end dates are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Collection end date cannot be before start date.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new IllegalArgumentException("KAMIS lookup range cannot exceed one year.");
        }
    }
}
