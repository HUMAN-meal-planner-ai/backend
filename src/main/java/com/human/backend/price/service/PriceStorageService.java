package com.human.backend.price.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.util.KamisPriceValueParser;

@Service
public class PriceStorageService {

    private static final Logger log = LoggerFactory.getLogger(PriceStorageService.class);
    private static final BigDecimal PRICE_UNIT_QUANTITY = BigDecimal.valueOf(1000);

    private final IngredientPriceRepository ingredientPriceRepository;
    private final KamisPriceValueParser valueParser;

    public PriceStorageService(
            IngredientPriceRepository ingredientPriceRepository,
            KamisPriceValueParser valueParser) {
        this.ingredientPriceRepository = ingredientPriceRepository;
        this.valueParser = valueParser;
    }

    @Transactional
    public StoreResult store(PriceSeries series, List<KamisPriceItemDto> items) {
        validateSeries(series);

        int pricesInserted = 0;
        int duplicatesSkipped = 0;
        int invalidRowsSkipped = 0;

        for (KamisPriceItemDto item : items) {
            try {
                LocalDate priceDate = valueParser.parseDate(item.year(), item.regDay());
                BigDecimal originalPrice = valueParser.parsePrice(item.price());

                if (ingredientPriceRepository.existsBySeries_IdAndPriceDate(
                        series.getId(), priceDate)) {
                    duplicatesSkipped++;
                    continue;
                }

                ingredientPriceRepository.save(new IngredientPrice(
                        series, priceDate, originalPrice, PRICE_UNIT_QUANTITY));
                pricesInserted++;
            } catch (IllegalArgumentException exception) {
                invalidRowsSkipped++;
                log.warn("유효하지 않은 KAMIS 가격 행을 건너뜁니다. 시계열 ID={}, 사유={}",
                        series.getId(), exception.getMessage());
            }
        }

        return new StoreResult(0, pricesInserted, duplicatesSkipped, invalidRowsSkipped);
    }

    private void validateSeries(PriceSeries series) {
        if (series == null || series.getId() == null) {
            throw new IllegalArgumentException("저장된 가격 시계열이 필요합니다.");
        }
        if (!"KAMIS".equals(series.getSourceName())) {
            throw new IllegalArgumentException("가격 시계열의 출처명은 KAMIS여야 합니다.");
        }

        Ingredient ingredient = series.getIngredient();
        if (ingredient == null || !ingredient.isActive()) {
            throw new IllegalStateException("가격 시계열의 식재료가 없거나 비활성 상태입니다.");
        }
        if (!"g".equalsIgnoreCase(ingredient.getStandardUnit())) {
            throw new IllegalStateException("가격 시계열의 식재료 기준 단위가 g이 아닙니다.");
        }
        if (!"kg".equalsIgnoreCase(series.getOriginalUnit())
                || BigDecimal.ONE.compareTo(series.getUnitQuantity()) != 0) {
            throw new IllegalStateException("KAMIS 가격 시계열이 1kg을 g으로 환산하는 형식이 아닙니다.");
        }
    }

    public record StoreResult(
            int seriesCreated,
            int pricesInserted,
            int duplicatesSkipped,
            int invalidRowsSkipped) {
    }
}
