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

        // KAMIS에는 같은 식재료의 품종·등급·지역별 시계열이 여러 개 존재합니다.
        // 랜딩 카드가 한 식재료로만 채워지지 않도록 절감률 순으로 정렬한 뒤
        // 식재료별 가장 저렴한 대표 시계열 한 건만 화면에 전달합니다.
        Map<String, YearOverYearBargainItem> bestItemByIngredient = new LinkedHashMap<>();
        currentBySeries.values().stream()
                .filter(current -> isDisplayableKamisPrice(current, previousBySeries.get(seriesId(current))))
                .map(current -> toItem(current, previousBySeries.get(seriesId(current))))
                .sorted((left, right) -> right.savingRatePercent().compareTo(left.savingRatePercent()))
                .forEach(item -> bestItemByIngredient.putIfAbsent(item.ingredientName(), item));

        List<YearOverYearBargainItem> items = bestItemByIngredient.values().stream()
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
                series.getId(), ingredient.getId(), representativeIngredientName(ingredient.getName()),
                ingredient.getCategory(),
                series.getVariety(), series.getGrade(), series.getOriginalUnit(),
                current.getOriginalPrice(), previous.getOriginalPrice(), savingRate);
    }

    /**
     * 식품 영양 DB의 이름은 "상추, 잎상추, 축면상추, 적색, 생것"처럼
     * 세부 분류가 쉼표로 이어집니다. 랜딩 화면에서는 첫 분류만 사용해
     * 사용자가 한눈에 알아볼 수 있는 대표 품목명(예: "상추")으로 표시합니다.
     */
    private String representativeIngredientName(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return "식재료";
        }

        return ingredientName.split(",", 2)[0].trim();
    }

    private Long seriesId(IngredientPrice price) {
        return price.getSeries().getId();
    }

    private YearOverYearBargainResponse empty(LocalDate asOfDate, LocalDate comparisonDate) {
        return new YearOverYearBargainResponse(asOfDate, comparisonDate, 0, List.of());
    }
}
