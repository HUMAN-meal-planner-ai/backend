package com.human.backend.price.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.human.backend.integration.priceapi.KamisRegion;
import com.human.backend.integration.priceapi.dto.KamisPriceItemDto;
import com.human.backend.price.entity.PriceSeries;
import com.human.backend.price.util.KamisPriceValueParser;

@Component
public class KamisRegionalPriceSelector {

    private final KamisPriceValueParser valueParser;

    public KamisRegionalPriceSelector(KamisPriceValueParser valueParser) {
        this.valueParser = valueParser;
    }

    /**
     * 요청한 지역의 실제 시장가격만 남기고, 같은 날짜의 동일 가격 중복은 한 건으로 정규화한다.
     */
    public List<KamisPriceItemDto> select(
            PriceSeries series, List<KamisPriceItemDto> items) {
        String expectedRegion = KamisRegion.fromSeriesRegion(series.getRegion()).displayName();
        Map<String, List<KamisPriceItemDto>> itemsByDate = new LinkedHashMap<>();

        items.stream()
                .filter(item -> expectedRegion.equals(trim(item.countyName())))
                .forEach(item -> itemsByDate
                        .computeIfAbsent(dateKey(item), ignored -> new ArrayList<>())
                        .add(item));

        List<KamisPriceItemDto> selected = new ArrayList<>();
        for (Map.Entry<String, List<KamisPriceItemDto>> entry : itemsByDate.entrySet()) {
            List<KamisPriceItemDto> sameDateItems = entry.getValue();
            selected.add(selectOnePrice(series, entry.getKey(), sameDateItems));
        }
        return List.copyOf(selected);
    }

    private KamisPriceItemDto selectOnePrice(
            PriceSeries series, String dateKey, List<KamisPriceItemDto> items) {
        List<KamisPriceItemDto> validPriceItems = items.stream()
                .filter(this::hasValidPrice)
                .toList();
        if (validPriceItems.isEmpty()) {
            return items.get(0);
        }

        BigDecimal firstPrice = valueParser.parsePrice(validPriceItems.get(0).price());
        boolean allSame = validPriceItems.stream()
                .map(KamisPriceItemDto::price)
                .map(valueParser::parsePrice)
                .allMatch(price -> firstPrice.compareTo(price) == 0);
        if (!allSame) {
            throw new IllegalStateException(
                    "동일한 지역과 날짜에 서로 다른 KAMIS 가격이 있습니다. 시계열 ID="
                            + series.getId() + ", 날짜=" + dateKey);
        }
        return validPriceItems.get(0);
    }

    private boolean hasValidPrice(KamisPriceItemDto item) {
        try {
            valueParser.parsePrice(item.price());
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String dateKey(KamisPriceItemDto item) {
        return trim(item.year()) + "-" + trim(item.regDay());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
