package com.human.backend.price.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Component;

@Component
public class KamisPriceValueParser {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public BigDecimal parsePrice(String rawPrice) {
        if (rawPrice == null) {
            throw new IllegalArgumentException("KAMIS 가격이 없습니다.");
        }

        String normalized = rawPrice.replace(",", "").replaceAll("\\s+", "");
        if (!normalized.matches("[+-]?\\d+(?:\\.\\d+)?")) {
            throw new IllegalArgumentException("유효하지 않은 KAMIS 가격입니다: " + rawPrice);
        }

        BigDecimal price = new BigDecimal(normalized);
        if (price.signum() < 0) {
            throw new IllegalArgumentException("KAMIS 가격은 음수일 수 없습니다: " + rawPrice);
        }
        return price;
    }

    public LocalDate parseDate(String year, String regDay) {
        if (regDay == null || regDay.isBlank()) {
            throw new IllegalArgumentException("KAMIS 가격 일자가 없습니다.");
        }

        try {
            if (regDay.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(regDay);
            }
            if (year != null && year.matches("\\d{4}") && regDay.matches("\\d{2}/\\d{2}")) {
                return LocalDate.parse(year + "/" + regDay, MONTH_DAY);
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("유효하지 않은 KAMIS 가격 일자입니다: " + regDay, exception);
        }

        throw new IllegalArgumentException("지원하지 않는 KAMIS 가격 일자 형식입니다: " + regDay);
    }
}
