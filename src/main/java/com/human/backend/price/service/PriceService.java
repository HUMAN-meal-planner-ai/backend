package com.human.backend.price.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import com.human.backend.integration.priceapi.KamisPriceApiClient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.integration.priceapi.dto.KamisPriceResponseDto;
import com.human.backend.price.config.KamisPriceCatalog;
import com.human.backend.price.config.KamisPriceTarget;
import com.human.backend.price.dto.response.PriceCollectionResult;
import com.human.backend.price.util.KamisPriceValueParser;
import org.springframework.stereotype.Service;

@Service
public class PriceService {
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
        KamisPriceTarget target = priceCatalog.findByIngredientCode(ingredientCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "KAMIS 수집 설정이 없는 ingredient_code입니다: " + ingredientCode));

        KamisPriceResponseDto response = kamisPriceApiClient.getPriceData(target, startDate, endDate);

        if (response == null
                || response.data() == null
                || !"000".equals(response.data().errorCode())
                || response.data().items() == null) {
            String errorCode = response == null || response.data() == null
                    ? "NO_RESPONSE"
                    : response.data().errorCode();
            throw new IllegalStateException("KAMIS 가격 조회 실패: error_code=" + errorCode);
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
        return new PriceCollectionResult(
                target.ingredientCode(), target.itemCode(), startDate, endDate,
                actualItems.size(), outOfRangeRowsSkipped,
                stored.seriesCreated(), stored.pricesInserted(),
                stored.duplicatesSkipped(), stored.invalidRowsSkipped());
    }

    private boolean isInRequestedRange(
            KamisPriceItemDto item, LocalDate startDate, LocalDate endDate) {
        try {
            LocalDate priceDate = valueParser.parseDate(item.year(), item.regDay());
            return !priceDate.isBefore(startDate) && !priceDate.isAfter(endDate);
        } catch (IllegalArgumentException exception) {
            // 형식 오류 행은 저장 서비스에서 일관되게 집계하고 경고를 남깁니다.
            return true;
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("수집 시작일과 종료일은 필수입니다.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("수집 종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > 365) {
            throw new IllegalArgumentException("KAMIS 조회 기간은 최대 1년입니다.");
        }
    }
}
