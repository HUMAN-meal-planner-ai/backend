package com.human.backend.price.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.human.backend.price.dto.response.DailyPricePoint;
import com.human.backend.price.dto.response.DailyPriceResponse;
import com.human.backend.price.dto.response.PriceDataStatus;
import com.human.backend.price.dto.response.WeeklyPriceResponse;
import com.human.backend.price.repository.PriceSeriesRepository;
import com.human.backend.price.repository.PriceSeriesRepository.WeeklyRepresentativePrice;

@Service
public class PriceQueryService {

    private static final int PRICE_SCALE = 6;
    private static final int DAYS_IN_WEEK = 7;

    private final PriceSeriesRepository priceSeriesRepository;

    public PriceQueryService(PriceSeriesRepository priceSeriesRepository) {
        this.priceSeriesRepository = priceSeriesRepository;
    }

    public DailyPriceResponse getDailyPrices(Long seriesId, LocalDate startDate, LocalDate endDate) {
        validateSeriesId(seriesId);
        validateDateRange(startDate, endDate);

        List<WeeklyRepresentativePrice> observations = priceSeriesRepository
                .findDailyRepresentativePrices(seriesId, startDate, endDate);
        List<DailyPricePoint> prices = observations.stream()
                .map(value -> new DailyPricePoint(value.getPriceDate(), value.getRepresentativePrice()))
                .toList();

        return new DailyPriceResponse(
                seriesId,
                ingredientCode(observations),
                standardUnit(observations),
                startDate,
                endDate,
                prices.size(),
                prices);
    }

    public WeeklyPriceResponse getWeeklyPrice(Long seriesId, LocalDate weekStartDate) {
        validateSeriesId(seriesId);
        if (weekStartDate == null) {
            throw new IllegalArgumentException("weekStartDate is required");
        }
        if (weekStartDate.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("weekStartDate must be a Monday");
        }

        LocalDate previousWeekStart = weekStartDate.minusWeeks(1);
        LocalDate weekEndDate = weekStartDate.plusDays(DAYS_IN_WEEK - 1L);
        List<WeeklyRepresentativePrice> observations = priceSeriesRepository
                .findDailyRepresentativePrices(seriesId, previousWeekStart, weekEndDate);
        List<WeeklyRepresentativePrice> currentWeek = within(
                observations, weekStartDate, weekEndDate);
        List<WeeklyRepresentativePrice> previousWeek = within(
                observations, previousWeekStart, weekStartDate.minusDays(1));

        BigDecimal currentAverage = average(currentWeek);
        BigDecimal previousAverage = average(previousWeek);
        boolean comparable = currentAverage != null
                && previousAverage != null
                && previousAverage.compareTo(BigDecimal.ZERO) != 0;
        BigDecimal changeRate = comparable
                ? currentAverage.subtract(previousAverage)
                        .divide(previousAverage, PRICE_SCALE + 2, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(PRICE_SCALE, RoundingMode.HALF_UP)
                : null;

        return new WeeklyPriceResponse(
                seriesId,
                ingredientCode(observations),
                standardUnit(observations),
                weekStartDate,
                weekEndDate,
                currentWeek.size(),
                currentAverage,
                status(currentWeek.size()),
                previousAverage,
                comparable,
                changeRate);
    }

    private List<WeeklyRepresentativePrice> within(
            List<WeeklyRepresentativePrice> observations, LocalDate startDate, LocalDate endDate) {
        return observations.stream()
                .filter(value -> !value.getPriceDate().isBefore(startDate))
                .filter(value -> !value.getPriceDate().isAfter(endDate))
                .toList();
    }

    private BigDecimal average(List<WeeklyRepresentativePrice> observations) {
        if (observations.isEmpty()) {
            return null;
        }
        BigDecimal total = observations.stream()
                .map(WeeklyRepresentativePrice::getRepresentativePrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(observations.size()), PRICE_SCALE, RoundingMode.HALF_UP);
    }

    private PriceDataStatus status(int observationDays) {
        if (observationDays == 0) {
            return PriceDataStatus.NO_DATA;
        }
        return observationDays == DAYS_IN_WEEK
                ? PriceDataStatus.COMPLETE
                : PriceDataStatus.PARTIAL;
    }

    private String ingredientCode(List<WeeklyRepresentativePrice> observations) {
        return observations.isEmpty() ? null : observations.get(0).getIngredientCode();
    }

    private String standardUnit(List<WeeklyRepresentativePrice> observations) {
        return observations.isEmpty() ? null : observations.get(0).getStandardUnit();
    }

    private void validateSeriesId(Long seriesId) {
        if (seriesId == null || seriesId <= 0) {
            throw new IllegalArgumentException("seriesId must be positive");
        }
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be on or before endDate");
        }
    }
}
