package com.human.backend.cost.util;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.human.backend.cost.util.CostConstants.*;

/**
 * 원가 및 예산 분석을 위한 연산/변환 공통 유틸리티 클래스
 * [안정성 및 재사용성 향상]: 0 나누기(Divide-by-Zero) 예외 방어 및 소수점 처리 정책 통일
 */
@Slf4j
public final class CostCalculationUtils {

    private CostCalculationUtils() {
        // 인스턴스화 방지
    }

    /**
     * 백분율(%) 계산: (분자 / 분모) * 100
     * 분모가 null이거나 0 이하일 경우 안전하게 BigDecimal.ZERO 반환
     */
    public static BigDecimal calculatePercentage(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return numerator
                .divide(denominator, PERCENT_CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 변동률/상승률(%) 계산: ((미래값 - 현재값) / 현재값) * 100
     * 현재값이 null이거나 0 이하일 경우 안전하게 BigDecimal.ZERO 반환
     */
    public static BigDecimal calculateIncreaseRate(BigDecimal currentValue, BigDecimal futureValue) {
        if (currentValue == null || futureValue == null || currentValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal difference = futureValue.subtract(currentValue);
        return difference
                .divide(currentValue, PERCENT_CALC_SCALE, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(PERCENT_DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 1인당 평균 단가 계산: 총 비용 / 총 식수 인원
     * 식수 인원이 0 이하일 경우 안전하게 BigDecimal.ZERO 반환
     */
    public static BigDecimal calculateAverageCost(BigDecimal totalCost, Integer mealCount) {
        if (totalCost == null || mealCount == null || mealCount <= 0) {
            return BigDecimal.ZERO;
        }
        return totalCost.divide(BigDecimal.valueOf(mealCount), UNIT_PRICE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 연월 문자열(YYYY-MM) 안전 파싱 (null 또는 파싱 실패 시 기본 기준일의 연월 반환)
     */
    public static YearMonth parseYearMonth(String yearMonthStr) {
        if (yearMonthStr == null || yearMonthStr.isBlank()) {
            return YearMonth.from(DEFAULT_BASE_DATE);
        }
        try {
            return YearMonth.parse(yearMonthStr.trim());
        } catch (Exception e) {
            log.warn(">> 잘못된 연월 형식('{}')으로 기본 연월({})을 적용합니다.", yearMonthStr, DEFAULT_BASE_DATE);
            return YearMonth.from(DEFAULT_BASE_DATE);
        }
    }

    /**
     * DayOfWeek -> 한국어 요일 문자열 변환 ("월요일" ~ "일요일")
     */
    public static String formatKoreanDayOfWeek(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) return "";
        return switch (dayOfWeek) {
            case MONDAY -> "월요일";
            case TUESDAY -> "화요일";
            case WEDNESDAY -> "수요일";
            case THURSDAY -> "목요일";
            case FRIDAY -> "금요일";
            case SATURDAY -> "토요일";
            case SUNDAY -> "일요일";
        };
    }

    /**
     * 식수 인원 방어 처리 (null 또는 0 이하일 경우 1로 기본 보정)
     */
    public static int resolveMealCount(Integer mealCount) {
        return (mealCount == null || mealCount <= 0) ? 1 : mealCount;
    }

    /**
     * 목표 단가 방어 처리 (null 또는 0 이하일 경우 기본 목표단가 보정)
     */
    public static BigDecimal resolveTargetCost(BigDecimal targetCost) {
        return (targetCost == null || targetCost.compareTo(BigDecimal.ZERO) <= 0)
                ? DEFAULT_TARGET_COST
                : targetCost;
    }

    /**
     * 예측 기준일자 방어 처리 (null일 경우 기본 예측기준일 보정)
     */
    public static LocalDate resolveTargetDate(LocalDate targetDate) {
        return (targetDate != null) ? targetDate : DEFAULT_PREDICTION_DATE;
    }
}
