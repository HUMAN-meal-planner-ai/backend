package com.human.backend.price.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.ingredient.entity.Ingredient;
import com.human.backend.price.dto.response.YearOverYearBargainItem;
import com.human.backend.price.dto.response.YearOverYearBargainResponse;
import com.human.backend.price.entity.IngredientPrice;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.repository.IngredientPriceRepository;

@Service
public class YearOverYearBargainService {

    private static final int MAX_LIMIT = 12;
    private static final int PREVIOUS_YEAR_LOOKBACK_DAYS = 14;
    private final IngredientPriceRepository ingredientPriceRepository;

    public YearOverYearBargainService(IngredientPriceRepository ingredientPriceRepository) {
        this.ingredientPriceRepository = ingredientPriceRepository;
    }

    @Transactional(readOnly = true)
    public YearOverYearBargainResponse getBargains(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }

        LocalDate asOfDate = ingredientPriceRepository.findLatestPriceDate();
        if (asOfDate == null) {
            return empty(null, null);
        }

        LocalDate previousYearEnd = asOfDate.minusYears(1);
        LocalDate comparisonDate = ingredientPriceRepository.findLatestPriceDateBetween(
                previousYearEnd.minusDays(PREVIOUS_YEAR_LOOKBACK_DAYS), previousYearEnd);
        if (comparisonDate == null) {
            return empty(asOfDate, null);
        }

        List<IngredientPrice> prices = ingredientPriceRepository
                .findAllByPriceDateInOrderByIdDesc(List.of(asOfDate, comparisonDate));
        Map<Long, IngredientPrice> currentBySeries = pricesForDate(prices, asOfDate);
        Map<Long, IngredientPrice> previousBySeries = pricesForDate(prices, comparisonDate);

        List<YearOverYearBargainItem> items = currentBySeries.values().stream()
                .filter(current -> isDisplayableKamisPrice(current, previousBySeries.get(seriesId(current))))
                .map(current -> toItem(current, previousBySeries.get(seriesId(current))))
                .sorted((left, right) -> right.savingRatePercent().compareTo(left.savingRatePercent()))
                .limit(limit)
                .toList();

        return new YearOverYearBargainResponse(
                asOfDate, comparisonDate, items.size(), List.copyOf(items));
    }

    private Map<Long, IngredientPrice> pricesForDate(
            Collection<IngredientPrice> prices, LocalDate priceDate) {
        Map<Long, IngredientPrice> result = new LinkedHashMap<>();
        prices.stream()
                .filter(price -> priceDate.equals(price.getPriceDate()))
                .forEach(price -> result.putIfAbsent(seriesId(price), price));
        return result;
    }

    private boolean isDisplayableKamisPrice(IngredientPrice current, IngredientPrice previous) {
        if (current == null || previous == null) {
            return false;
        }
        PriceSeries series = current.getSeries();
        Ingredient ingredient = series == null ? null : series.getIngredient();
        return ingredient != null
                && ingredient.isActive()
                && "KAMIS".equals(series.getSourceName())
                && current.getOriginalPrice() != null
                && previous.getOriginalPrice() != null
                && previous.getOriginalPrice().compareTo(BigDecimal.ZERO) > 0
                && current.getOriginalPrice().compareTo(previous.getOriginalPrice()) < 0;
    }

    private YearOverYearBargainItem toItem(IngredientPrice current, IngredientPrice previous) {
        PriceSeries series = current.getSeries();
        Ingredient ingredient = series.getIngredient();
        BigDecimal savingRate = previous.getOriginalPrice()
                .subtract(current.getOriginalPrice())
                .divide(previous.getOriginalPrice(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        return new YearOverYearBargainItem(
                series.getId(), ingredient.getName(), ingredient.getCategory(),
                series.getVariety(), series.getGrade(), series.getOriginalUnit(),
                current.getOriginalPrice(), previous.getOriginalPrice(), savingRate);
    }

    private Long seriesId(IngredientPrice price) {
        return price.getSeries().getId();
    }

    private YearOverYearBargainResponse empty(LocalDate asOfDate, LocalDate comparisonDate) {
        return new YearOverYearBargainResponse(asOfDate, comparisonDate, 0, List.of());
    }
}
