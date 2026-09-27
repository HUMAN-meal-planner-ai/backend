package com.human.backend.automation.service;

import com.human.backend.automation.dto.request.WeeklyPlanReverificationRequest;
import com.human.backend.automation.dto.response.WeeklyPlanReverificationResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.mealplan.service.MealPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static com.human.backend.cost.util.CostCalculationUtils.calculatePercentage;
import static com.human.backend.cost.util.CostCalculationUtils.calculateTotalMealPlansCost;
import static com.human.backend.cost.util.CostConstants.DEFAULT_BASE_DATE;
import static com.human.backend.cost.util.CostConstants.DEFAULT_FACILITY_ID;
import static com.human.backend.cost.util.CostConstants.DEFAULT_MONTHLY_BUDGET;

/**
 * [AUTO-004] 재평가된 주간 식단 예산 위험 재확인 전담 서비스
 * 
 * [단일 책임 원칙(SRP)]:
 * 재평가/재구성된 주간 식단의 총 예상 비용을 산출하고, 이를 월 잔여 예산과 비교하여
 * 예산 초과 위험의 변화(해소 여부, 위험 등급 재판정, 비용 절감액)를 정밀 검증하는 책임을 전담합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeeklyPlanBudgetVerificationService {

    private final CostRepository costRepository;
    private final CostService costService;
    private final MealPlanService mealPlanService;

    /**
     * [AUTO-004] 재평가된 주간 식단의 예상 비용과 월 잔여 예산을 비교해 예산 위험 재확인
     */
    public WeeklyPlanReverificationResponse verifyWeeklyPlanBudget(WeeklyPlanReverificationRequest request) {
        Long targetFacilityId = (request != null && request.getFacilityId() != null)
                ? request.getFacilityId()
                : DEFAULT_FACILITY_ID;

        LocalDate targetBaseDate = (request != null && request.getBaseDate() != null)
                ? request.getBaseDate()
                : ((request != null && request.getWeekStartDate() != null) ? request.getWeekStartDate()
                        : DEFAULT_BASE_DATE);

        LocalDate weekMonday = (request != null && request.getWeekStartDate() != null)
                ? request.getWeekStartDate().with(DayOfWeek.MONDAY)
                : targetBaseDate.with(DayOfWeek.MONDAY);
        LocalDate weekSunday = weekMonday.plusDays(6);

        int mealCount = (request != null && request.getMealCount() != null && request.getMealCount() > 0)
                ? request.getMealCount()
                : 1;

        YearMonth budgetMonth = YearMonth.from(weekMonday);
        LocalDate monthStart = budgetMonth.atDay(1);

        log.info(">> [AUTO-004 예산 재확인 시작] 시설 ID: {}, 대상 주차: {} ~ {}, 식수인원: {}명",
                targetFacilityId, weekMonday, weekSunday, mealCount);

        // 1. 시설 월 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, budgetMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, budgetMonth,
                        DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 2. 월초부터 해당 주차 시작일(월요일) 직전까지의 누적 기 집행액 산출
        BigDecimal currentSpentCost = BigDecimal.ZERO;
        if (weekMonday.isAfter(monthStart)) {
            LocalDate pastEnd = weekMonday.minusDays(1);
            List<MealPlanCostVo> pastPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                    monthStart, pastEnd);
            currentSpentCost = calculateTotalMealPlansCost(pastPlans);
        }

        // 3. 월 잔여 예산 도출 (배정예산 - 기집행액)
        BigDecimal monthlyRemainingBudget = monthlyBudget.subtract(currentSpentCost);

        // 4. 기존(재평가 전) 주간 식단 비용 산출
        List<MealPlanCostVo> originalPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId,
                weekMonday, weekSunday);
        BigDecimal originalWeeklyCost = calculateTotalMealPlansCost(originalPlans);

        // 5. 재평가(재구성)된 주간 식단 비용 및 일자별 세부 내역 산출
        List<WeeklyPlanReverificationResponse.DailyReevaluatedCostDetail> dailyBreakdown = new ArrayList<>();
        BigDecimal reevaluatedWeeklyCost = BigDecimal.ZERO;

        if (request != null && request.getReconfiguredItems() != null && !request.getReconfiguredItems().isEmpty()) {
            // (A) 직접 전달된 재구성 식단 목록 기준
            for (WeeklyPlanReverificationRequest.ReconfiguredItemDto item : request.getReconfiguredItems()) {
                BigDecimal unitPrice = item.getCostPerPerson();
                if (unitPrice == null && item.getMenuId() != null) {
                    MenuCostResponse costResponse = costService.calculateCurrentMenuCost(item.getMenuId(), mealCount,
                            null);
                    unitPrice = costResponse.getCostPerPerson();
                }
                unitPrice = (unitPrice != null) ? unitPrice : BigDecimal.ZERO;
                BigDecimal dailyTotal = unitPrice.multiply(BigDecimal.valueOf(mealCount));
                reevaluatedWeeklyCost = reevaluatedWeeklyCost.add(dailyTotal);

                String menuName = item.getMenuName();
                if (menuName == null && item.getMenuId() != null) {
                    menuName = costRepository.findMenuNameById(item.getMenuId()).orElse("메뉴 " + item.getMenuId());
                }

                dailyBreakdown.add(WeeklyPlanReverificationResponse.DailyReevaluatedCostDetail.builder()
                        .mealDate(item.getMealDate())
                        .menuId(item.getMenuId())
                        .menuName(menuName)
                        .costPerPerson(unitPrice)
                        .totalCost(dailyTotal)
                        .build());
            }
        } else {
            // (B) 저장된 재구성 주간 식단(MealPlanService) 또는 기본 식단 기준 조회
            try {
                MealPlanResponse savedPlan = mealPlanService.findWeeklyPlan(weekMonday);
                for (MealPlanResponse.MealResponse meal : savedPlan.meals()) {
                    BigDecimal unitPrice = meal.costPerPerson() != null ? meal.costPerPerson() : BigDecimal.ZERO;
                    BigDecimal dailyTotal = unitPrice.multiply(BigDecimal.valueOf(mealCount));
                    reevaluatedWeeklyCost = reevaluatedWeeklyCost.add(dailyTotal);

                    dailyBreakdown.add(WeeklyPlanReverificationResponse.DailyReevaluatedCostDetail.builder()
                            .mealDate(meal.mealDate())
                            .menuId(meal.menuId())
                            .menuName(meal.menuName())
                            .costPerPerson(unitPrice)
                            .totalCost(dailyTotal)
                            .build());
                }
            } catch (Exception e) {
                // 저장된 식단이 없을 경우 기존 식단 기반 단가 적용
                reevaluatedWeeklyCost = originalWeeklyCost;
                for (MealPlanCostVo planVo : originalPlans) {
                    dailyBreakdown.add(WeeklyPlanReverificationResponse.DailyReevaluatedCostDetail.builder()
                            .mealDate(planVo.getPlanDate())
                            .menuId(planVo.getPlanId())
                            .menuName(planVo.getMealType())
                            .costPerPerson(planVo.getExpectedCostPerPerson())
                            .totalCost(planVo.calculateTotalCost())
                            .build());
                }
            }
        }

        // 6. 비용 차액 및 절감률 산출
        BigDecimal costDifference = originalWeeklyCost.subtract(reevaluatedWeeklyCost);
        BigDecimal savingsRate = BigDecimal.ZERO;
        if (originalWeeklyCost.compareTo(BigDecimal.ZERO) > 0) {
            savingsRate = calculatePercentage(costDifference, originalWeeklyCost);
        }

        // 7. 응답 DTO 팩토리 호출 (위험도 판정 및 피드백 메시지 생성 캡슐화)
        WeeklyPlanReverificationResponse response = WeeklyPlanReverificationResponse.of(
                targetFacilityId, budgetVo.getFacilityName(),
                weekMonday, weekSunday, budgetMonth.toString(), mealCount,
                monthlyBudget, currentSpentCost, monthlyRemainingBudget,
                originalWeeklyCost, reevaluatedWeeklyCost, costDifference, savingsRate,
                dailyBreakdown);

        log.info(">> [AUTO-004 예산 재확인 완료] 기존비용: {}원 ──> 재평가비용: {}원 (절감: {}원, {}%) | 잔여예산: {}원 | 위험등급: {} -> {} (해소: {})",
                originalWeeklyCost, reevaluatedWeeklyCost, costDifference, savingsRate, response.getProjectedRemainingBudget(),
                response.getPreviousRiskLevel(), response.getRecheckedRiskLevel(), response.isRiskResolved());

        return response;
    }
}
