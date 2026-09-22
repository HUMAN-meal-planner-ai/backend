package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.MonthlyMealPlanCostResponse;
import com.human.backend.cost.dto.response.WeeklyMealPlanCostResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.human.backend.cost.util.CostCalculationUtils.*;
import static com.human.backend.cost.util.CostConstants.*;

/**
 * 주간 및 월간 식단 식재료비 집계 전담 서비스
 * 담당 요구사항:
 * - COST-012: 선택 주차 7일 식단의 최신·예측 단가 기준 총 예상 식재료비를 계산한다.
 * - COST-013: 주별 예상 비용을 합산하여 월간 총 예상 식재료비를 계산한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MealPlanCostService {

    private final CostRepository costRepository;

    /**
     * 선택 주차 7일 식단의 최신·예측 단가 기준 총 예상 식재료비 계산 (COST-012)
     */
    public WeeklyMealPlanCostResponse calculateWeeklyMealPlanCost(Long facilityId, LocalDate startDate) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        LocalDate weekStart = (startDate != null) ? startDate : DEFAULT_BASE_DATE.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);

        String facilityName = costRepository.findFacilityBudget(targetFacilityId, YearMonth.from(weekStart))
                .map(FacilityBudgetVo::getFacilityName)
                .orElse("시설 " + targetFacilityId);

        List<MealPlanCostVo> weeklyPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId, weekStart, weekEnd);

        Map<LocalDate, List<MealPlanCostVo>> plansByDate = weeklyPlans.stream()
                .collect(Collectors.groupingBy(MealPlanCostVo::getPlanDate));

        List<WeeklyMealPlanCostResponse.DailyCostDetail> dailyCosts = new ArrayList<>();
        BigDecimal totalExpectedCost = BigDecimal.ZERO;
        int totalMealCount = 0;

        for (int i = 0; i < 7; i++) {
            LocalDate currentDate = weekStart.plusDays(i);
            List<MealPlanCostVo> currentDayPlans = plansByDate.getOrDefault(currentDate, Collections.emptyList());

            BigDecimal dailyTotalCost = BigDecimal.ZERO;
            int dailyMealCount = 0;
            List<WeeklyMealPlanCostResponse.MealPlanDetail> mealDetails = new ArrayList<>();

            for (MealPlanCostVo plan : currentDayPlans) {
                BigDecimal mealTotalCost = plan.calculateTotalCost();
                dailyTotalCost = dailyTotalCost.add(mealTotalCost);
                dailyMealCount += plan.getMealCount();

                mealDetails.add(WeeklyMealPlanCostResponse.MealPlanDetail.builder()
                        .planId(plan.getPlanId())
                        .mealType(plan.getMealType())
                        .mealCount(plan.getMealCount())
                        .costPerPerson(plan.getExpectedCostPerPerson())
                        .totalMealCost(mealTotalCost)
                        .build());
            }

            dailyCosts.add(WeeklyMealPlanCostResponse.DailyCostDetail.builder()
                    .date(currentDate)
                    .dayOfWeek(formatKoreanDayOfWeek(currentDate.getDayOfWeek()))
                    .dailyTotalCost(dailyTotalCost)
                    .dailyMealCount(dailyMealCount)
                    .meals(mealDetails)
                    .build());

            totalExpectedCost = totalExpectedCost.add(dailyTotalCost);
            totalMealCount += dailyMealCount;
        }

        BigDecimal averageCostPerPerson = calculateAverageCost(totalExpectedCost, totalMealCount);

        log.info("==================================================");
        log.info(">> [COST-012 주간 식단 식재료비 계산] 시설: {} (ID: {})", facilityName, targetFacilityId);
        log.info(">> 기간: {} ~ {} (7일)", weekStart, weekEnd);
        log.info(">> 총 식수: {}명 | 7일 총 예상 식재료비: {}원 | 1인 평균 단가: {}원",
                totalMealCount, totalExpectedCost, averageCostPerPerson);
        log.info("--------------------------------------------------");

        return WeeklyMealPlanCostResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(facilityName)
                .startDate(weekStart)
                .endDate(weekEnd)
                .totalExpectedCost(totalExpectedCost)
                .totalMealCount(totalMealCount)
                .averageCostPerPerson(averageCostPerPerson)
                .dailyCosts(dailyCosts)
                .build();
    }

    /**
     * 주별 예상 비용을 합산하여 월간 총 예상 식재료비 계산 (COST-013)
     */
    public MonthlyMealPlanCostResponse calculateMonthlyMealPlanCost(Long facilityId, String yearMonthStr) {
        Long targetFacilityId = (facilityId != null) ? facilityId : DEFAULT_FACILITY_ID;
        YearMonth targetYearMonth = parseYearMonth(yearMonthStr);
        LocalDate monthStart = targetYearMonth.atDay(1);
        LocalDate monthEnd = targetYearMonth.atEndOfMonth();

        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, targetYearMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, targetYearMonth,
                        DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        List<MonthlyMealPlanCostResponse.WeeklyCostSummary> weeklyCosts = new ArrayList<>();
        BigDecimal totalMonthlyExpectedCost = BigDecimal.ZERO;
        int totalMonthlyMealCount = 0;

        LocalDate curStart = monthStart;
        int weekIndex = 1;

        while (!curStart.isAfter(monthEnd)) {
            LocalDate curSunday = curStart.with(DayOfWeek.SUNDAY);
            LocalDate curEnd = curSunday.isAfter(monthEnd) ? monthEnd : curSunday;

            List<MealPlanCostVo> weekPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId, curStart, curEnd);

            BigDecimal weeklyTotalCost = calculateTotalMealPlansCost(weekPlans);
            int weeklyMealCount = weekPlans.stream().mapToInt(MealPlanCostVo::getMealCount).sum();
            BigDecimal weeklyAvgCostPerPerson = calculateAverageCost(weeklyTotalCost, weeklyMealCount);

            String weekLabel = String.format("%d월 %d주차", targetYearMonth.getMonthValue(), weekIndex);

            weeklyCosts.add(MonthlyMealPlanCostResponse.WeeklyCostSummary.builder()
                    .weekOfMonth(weekIndex)
                    .weekLabel(weekLabel)
                    .startDate(curStart)
                    .endDate(curEnd)
                    .weeklyTotalCost(weeklyTotalCost)
                    .weeklyMealCount(weeklyMealCount)
                    .averageCostPerPerson(weeklyAvgCostPerPerson)
                    .build());

            totalMonthlyExpectedCost = totalMonthlyExpectedCost.add(weeklyTotalCost);
            totalMonthlyMealCount += weeklyMealCount;

            curStart = curEnd.plusDays(1);
            weekIndex++;
        }

        BigDecimal averageCostPerPerson = calculateAverageCost(totalMonthlyExpectedCost, totalMonthlyMealCount);
        BigDecimal projectedRemainingBudget = monthlyBudget.subtract(totalMonthlyExpectedCost);
        BigDecimal budgetUsageRate = calculatePercentage(totalMonthlyExpectedCost, monthlyBudget);

        log.info("==================================================");
        log.info(">> [COST-013 월간 식단 식재료비 계산] 시설: {} (ID: {}) | 대상월: {}", budgetVo.getFacilityName(), targetFacilityId, targetYearMonth);
        log.info(">> 주차 수: {}개 주차 | 월간 총 식수: {}명 | 월간 총 예상 식재료비: {}원", weeklyCosts.size(), totalMonthlyMealCount, totalMonthlyExpectedCost);
        log.info(">> 월 배정 예산: {}원 | 예상 잔여액: {}원 (예산소진율: {}%) | 1인 평균 단가: {}원", monthlyBudget, projectedRemainingBudget, budgetUsageRate, averageCostPerPerson);
        log.info("--------------------------------------------------");

        return MonthlyMealPlanCostResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(budgetVo.getFacilityName())
                .yearMonth(targetYearMonth.toString())
                .monthlyBudget(monthlyBudget)
                .totalMonthlyExpectedCost(totalMonthlyExpectedCost)
                .totalMonthlyMealCount(totalMonthlyMealCount)
                .averageCostPerPerson(averageCostPerPerson)
                .projectedRemainingBudget(projectedRemainingBudget)
                .budgetUsageRate(budgetUsageRate)
                .weeklyCosts(weeklyCosts)
                .build();
    }
}

