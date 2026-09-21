package com.human.backend.price.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.ingredient.repository.IngredientRepository;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.config.KamisPriceTarget;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.util.KamisPriceValueParser;

@Service
public class PriceStorageService {

    private static final Logger log = LoggerFactory.getLogger(PriceStorageService.class);
    private static final String SOURCE_NAME = "KAMIS";
    private static final String PRICE_TYPE = "WHOLESALE";
    private static final String ORIGINAL_UNIT = "kg";
    private static final BigDecimal SERIES_UNIT_QUANTITY = BigDecimal.ONE;
    private static final BigDecimal PRICE_UNIT_QUANTITY = BigDecimal.valueOf(1000);

    private final IngredientRepository ingredientRepository;
    private final PriceSeriesRepository priceSeriesRepository;
    private final IngredientPriceRepository ingredientPriceRepository;
    private final KamisPriceValueParser valueParser;

    public PriceStorageService(
            IngredientRepository ingredientRepository,
            PriceSeriesRepository priceSeriesRepository,
            IngredientPriceRepository ingredientPriceRepository,
            KamisPriceValueParser valueParser) {
        this.ingredientRepository = ingredientRepository;
        this.priceSeriesRepository = priceSeriesRepository;
        this.ingredientPriceRepository = ingredientPriceRepository;
        this.valueParser = valueParser;
    }

    @Transactional
    public StoreResult store(KamisPriceTarget target, List<KamisPriceItemDto> items) {
        Ingredient ingredient = ingredientRepository.findByIngredientCode(target.ingredientCode())
                .orElseThrow(() -> new IllegalStateException(
                        "설정된 ingredient_code가 DB에 없습니다: " + target.ingredientCode()));

        if (!ingredient.isActive()) {
            throw new IllegalStateException("비활성 ingredient입니다: " + target.ingredientCode());
        }
        if (!"g".equalsIgnoreCase(ingredient.getStandardUnit())) {
            throw new IllegalStateException(
                    "kg→g 환산 대상이 아닌 ingredient입니다: " + target.ingredientCode());
        }

        int seriesCreated = 0;
        int pricesInserted = 0;
        int duplicatesSkipped = 0;
        int invalidRowsSkipped = 0;
        Map<SeriesKey, PriceSeries> seriesCache = new HashMap<>();

        for (KamisPriceItemDto item : items) {
            try {
                String market = required(item.marketName(), "marketname");
                String region = required(item.countyName(), "countyname");
                String variety = defaultIfBlank(item.kindName(), target.kindName());
                LocalDate priceDate = valueParser.parseDate(item.year(), item.regDay());
                BigDecimal originalPrice = valueParser.parsePrice(item.price());
                SeriesKey key = new SeriesKey(variety, market, region);

                PriceSeries series = seriesCache.get(key);
                if (series == null) {
                    series = priceSeriesRepository.findByNaturalKey(
                                    ingredient.getId(), SOURCE_NAME, target.itemCode(), variety,
                                    target.rankName(), PRICE_TYPE, market, region,
                                    ORIGINAL_UNIT, SERIES_UNIT_QUANTITY)
                            .orElse(null);
                    if (series == null) {
                        series = priceSeriesRepository.saveAndFlush(new PriceSeries(
                                ingredient, SOURCE_NAME, target.itemCode(), variety,
                                target.rankName(), PRICE_TYPE, market, region,
                                ORIGINAL_UNIT, SERIES_UNIT_QUANTITY));
                        seriesCreated++;
                    }
                    seriesCache.put(key, series);
                }

                if (ingredientPriceRepository.existsBySeries_IdAndPriceDate(series.getId(), priceDate)) {
                    duplicatesSkipped++;
                    continue;
                }

                ingredientPriceRepository.save(new IngredientPrice(
                        series, priceDate, originalPrice, PRICE_UNIT_QUANTITY));
                pricesInserted++;
            } catch (IllegalArgumentException exception) {
                invalidRowsSkipped++;
                log.warn("KAMIS 가격 행을 건너뜁니다. ingredientCode={}, reason={}",
                        target.ingredientCode(), exception.getMessage());
            }
        }

        return new StoreResult(seriesCreated, pricesInserted, duplicatesSkipped, invalidRowsSkipped);
    }

    private String required(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("KAMIS " + fieldName + " 값이 없습니다.");
        }
        return value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record SeriesKey(String variety, String market, String region) {
    }

    public record StoreResult(
            int seriesCreated,
            int pricesInserted,
            int duplicatesSkipped,
            int invalidRowsSkipped) {
    }
}
