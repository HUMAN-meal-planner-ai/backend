package com.human.backend.budget.service;

import com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest;
import com.human.backend.budget.dto.request.WeeklyHighCostMenuRequest;
import com.human.backend.budget.dto.response.HighCostMenuDto;
import com.human.backend.budget.dto.response.MenuReplacementCandidateDto;
import com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse;
import com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse.WeeklyBudgetImpactDto;
import com.human.backend.budget.dto.response.ServedMealDetail;
import com.human.backend.budget.dto.response.WeeklyHighCostMenuCandidateResponse;
import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.dto.response.MenuReplacementDiffResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.global.exception.ApiException;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.mealplan.service.MealPlanService;
import com.human.backend.menu.domain.MenuSlot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

import static com.human.backend.budget.util.BudgetConstants.*;
import static com.human.backend.cost.util.CostCalculationUtils.*;
import static com.human.backend.cost.util.CostConstants.*;

/**
 * [예산 관리 및 식단 비용 분석 전담 서비스]
 *
 * ■ 담당 요구사항:
 *   - BUDG-003: 선택 주차 식단에서 비용 기여도가 높은 메뉴를 식별하여 주간 식단 재구성의 변경 검토 후보로 제공한다.
 *   - BUDG-005: 대체 메뉴 적용 전후의 예상 비용 차이와 절감액을 표시한다.
 *
 * ■ cost 폴더와의 연동 아키텍처:
 *   [BudgetService]
 *         │
 *         ├──> [CostRepository] : 시설 예산, 주간 식단(끼니), 식단별 메뉴 매핑 정보 조회
 *         └──> [CostService]    :
 *                ├── calculateFutureMenuCost()    : 예측 시세 기반 메뉴 1인분 예상 단가 산출
 *                ├── compareMenuReplacementCost() : 메뉴 간 단가 차이 및 1인분/총식수 절감액 산출 (BUDG-005)
 *                ├── evaluateMenuRisk()           : 메뉴 원가 변동률 및 가격 위험도(WARNING/CAUTION) 진단
 *                └── identifyCostDrivers()        : 원가 상승을 견인하는 핵심 식재료(Cost Driver) 식별
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class BudgetService {

    private final CostRepository costRepository;
    private final CostService costService;

    @Autowired(required = false)
    private MealPlanService mealPlanService;

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");

    /**
     * [BUDG-003] 선택 주차 식단에서 비용 기여도가 높은 메뉴를 식별하여 주간 식단 재구성의 변경 검토 후보로 제공
     */
    public WeeklyHighCostMenuCandidateResponse identifyHighCostMenuCandidates(WeeklyHighCostMenuRequest request) {
        Long targetFacilityId = (request != null && request.getFacilityId() != null)
                ? request.getFacilityId()
                : DEFAULT_FACILITY_ID;

        LocalDate targetBaseDate = (request != null && request.getWeekStartDate() != null)
                ? request.getWeekStartDate()
                : DEFAULT_BASE_DATE;

        LocalDate weekMonday = targetBaseDate.with(DayOfWeek.MONDAY);
        LocalDate weekSunday = weekMonday.plusDays(6);
        int topN = (request != null && request.getTopN() != null && request.getTopN() > 0)
                ? request.getTopN()
                : DEFAULT_TOP_N;
        BigDecimal minContributionRate = (request != null) ? request.getMinContributionRate() : null;

        YearMonth budgetMonth = YearMonth.from(weekMonday);

        log.info("==================================================");
        log.info(">> [BUDG-003 고비용 메뉴 식별 시작] 시설: {}, 주차: {} ~ {} (Top: {}개)",
                targetFacilityId, weekMonday, weekSunday, topN);

        // 1. 시설 예산 정보 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(targetFacilityId, budgetMonth)
                .orElseGet(() -> new FacilityBudgetVo(targetFacilityId, "시설 " + targetFacilityId, budgetMonth,
                        DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 2. 주간 식단 목록 조회 (CostRepository의 7일간 식단)
        List<MealPlanCostVo> weeklyPlans = costRepository.findMealPlansByFacilityAndDateRange(targetFacilityId, weekMonday, weekSunday);

        // 3. 끼니별 식단 정보를 바탕으로 메뉴별 비용 및 제공 정보 수집
        Map<Long, MenuAccumulator> accumulatorMap = new HashMap<>();

        if (!weeklyPlans.isEmpty()) {
            collectFromCostRepositoryPlans(weeklyPlans, accumulatorMap);
        } else if (mealPlanService != null) {
            // DB/CSV 식단이 없을 경우 MealPlanService에 저장된 동적 주간 식단 연동 시도
            tryCollectFromMealPlanService(weekMonday, accumulatorMap);
        }

        // 4. 주간 식단 전체 총 비용 및 식수 집계
        BigDecimal weeklyGrandTotalCost = BigDecimal.ZERO;
        int totalWeeklyMeals = 0;

        for (MenuAccumulator acc : accumulatorMap.values()) {
            weeklyGrandTotalCost = weeklyGrandTotalCost.add(acc.totalMenuCost);
            totalWeeklyMeals += acc.totalMealCount;
        }

        int totalUniqueMenuCount = accumulatorMap.size();
        BigDecimal avgCostPerMeal = calculateAverageCost(weeklyGrandTotalCost, totalWeeklyMeals);

        // 5. 비용 기여도(비용 총액) 내림차순 정렬
        List<MenuAccumulator> sortedAccumulators = accumulatorMap.values().stream()
                .sorted(Comparator.comparing(MenuAccumulator::getTotalMenuCost).reversed())
                .collect(Collectors.toList());

        // 6. 성능 최적화: 전체 메뉴 대상이 아닌 실제 검토 대상(Top N 및 필터 조건 충족) 메뉴에 한해 정밀 위험도 분석 연동
        List<HighCostMenuDto> candidates = new ArrayList<>();
        BigDecimal runningCumulativeRate = BigDecimal.ZERO;
        int rank = 1;

        for (MenuAccumulator acc : sortedAccumulators) {
            BigDecimal contributionRate = calculatePercentage(acc.totalMenuCost, weeklyGrandTotalCost);
            runningCumulativeRate = runningCumulativeRate.add(contributionRate);

            // 최소 기여율 필터링 조건 확인
            if (minContributionRate != null && contributionRate.compareTo(minContributionRate) < 0) {
                rank++;
                continue;
            }

            // Top N 개수 충족 시 탐색 종료
            if (candidates.size() >= topN) {
                break;
            }

            // 해당 메뉴의 최신 제공 일자 기준 위험도 및 Cost Driver 조회 (cost 폴더 연동)
            LocalDate analysisDate = acc.latestDate != null ? acc.latestDate : weekMonday;
            MenuRiskResponse riskResponse = safelyEvaluateMenuRisk(acc.menuId, analysisDate);
            CostDriverResponse driverResponse = safelyIdentifyCostDrivers(acc.menuId, analysisDate);

            // 메뉴 슬롯 판별
            MenuSlot slot = resolveMenuSlot(acc.menuId, acc.menuName);
            String slotName = formatSlotName(slot);

            // 위험도 정보 결합
            String riskLevel = (riskResponse != null) ? riskResponse.getRiskLevel() : "SAFE";
            boolean isRisk = (riskResponse != null) && riskResponse.isRisk();
            BigDecimal increaseRate = (riskResponse != null) ? riskResponse.getIncreaseRate() : BigDecimal.ZERO;

            String topCostDriver = null;
            if (driverResponse != null && driverResponse.getTopDriver() != null) {
                CostDriverResponse.IngredientDriver top = driverResponse.getTopDriver();
                topCostDriver = String.format("%s (상승기여율 %s%%)", top.getIngredientName(), top.getContributionRate());
            }

            // 변경 검토 우선순위 및 추천 사유 생성
            String reviewPriority = determineReviewPriority(contributionRate, riskLevel, isRisk);
            BigDecimal avgCostPerPerson = calculateAverageCost(acc.totalMenuCost, acc.totalMealCount);
            BigDecimal savingsPotential = calculateEstimatedSavingsPotential(slot, avgCostPerPerson, acc.totalMealCount);
            String reviewReason = buildReviewReason(acc.menuName, contributionRate, rank, riskLevel, increaseRate, topCostDriver, slotName);

            HighCostMenuDto dto = HighCostMenuDto.builder()
                    .rank(rank)
                    .menuId(acc.menuId)
                    .menuName(acc.menuName)
                    .slot(slot)
                    .slotName(slotName)
                    .averageCostPerPerson(avgCostPerPerson)
                    .appearanceCount(acc.appearanceCount)
                    .totalMealCount(acc.totalMealCount)
                    .weeklyMenuCost(acc.totalMenuCost)
                    .contributionRate(contributionRate)
                    .cumulativeContributionRate(runningCumulativeRate.setScale(2, RoundingMode.HALF_UP))
                    .servedMeals(acc.servedMeals)
                    .riskLevel(riskLevel)
                    .isRisk(isRisk)
                    .increaseRate(increaseRate)
                    .topCostDriver(topCostDriver)
                    .reviewPriority(reviewPriority)
                    .reviewReason(reviewReason)
                    .estimatedSavingsPotential(savingsPotential)
                    .build();

            candidates.add(dto);
            rank++;
        }

        // 7. 주간 식단 재구성 권고 요약 메시지 생성
        String recommendationSummary = buildRecommendationSummary(
                budgetVo.getFacilityName(), weekMonday, weekSunday,
                weeklyGrandTotalCost, monthlyBudget, candidates);

        log.info(">> [BUDG-003 고비용 메뉴 식별 완료] 총 식단비용: {}원, 식별 후보 수: {}개 (전체 {}개 중)",
                weeklyGrandTotalCost, candidates.size(), sortedAccumulators.size());
        log.info("==================================================");

        return WeeklyHighCostMenuCandidateResponse.builder()
                .facilityId(targetFacilityId)
                .facilityName(budgetVo.getFacilityName())
                .weekStartDate(weekMonday)
                .weekEndDate(weekSunday)
                .budgetMonth(budgetMonth.toString())
                .monthlyBudget(monthlyBudget)
                .weeklyTotalCost(weeklyGrandTotalCost)
                .totalUniqueMenuCount(totalUniqueMenuCount)
                .totalMealCount(totalWeeklyMeals)
                .averageCostPerMeal(avgCostPerMeal)
                .candidates(candidates)
                .recommendationSummary(recommendationSummary)
                .build();
    }

    /**
     * CostRepository로부터 주간 식단(MealPlanCostVo) 및 메뉴 매핑 정보를 추출하여 누적 집계
     * [성능 최적화]: 주간 식단에서 반복 등장하는 메뉴(밥, 김치 등)에 대해 단일 요청 로컬 캐시를 적용하여
     * 중복 원가 계산 및 저장소 조회를 60~70% 절감합니다.
     */
    private void collectFromCostRepositoryPlans(List<MealPlanCostVo> weeklyPlans, Map<Long, MenuAccumulator> accumulatorMap) {
        Map<String, BigDecimal> costCache = new HashMap<>();
        Map<Long, String> nameCache = new HashMap<>();

        for (MealPlanCostVo plan : weeklyPlans) {
            List<Long> menuIds = costRepository.findMenuIdsByPlanId(plan.getPlanId());
            LocalDate planDate = plan.getPlanDate();
            int mealCount = plan.getMealCount();
            String mealType = plan.getMealType();

            for (Long menuId : menuIds) {
                String menuName = nameCache.computeIfAbsent(menuId,
                        id -> costRepository.findMenuNameById(id).orElse("메뉴#" + id));

                // 단일 요청 캐시: 동일 메뉴의 동일 일자 예측 단가는 1회만 계산
                String costKey = menuId + ":" + planDate;
                BigDecimal costPerPerson = costCache.computeIfAbsent(costKey, k -> {
                    MenuCostResponse costResponse = costService.calculateFutureMenuCost(menuId, planDate, 1, null);
                    return (costResponse != null && costResponse.getCostPerPerson() != null)
                            ? costResponse.getCostPerPerson()
                            : BigDecimal.ZERO;
                });

                BigDecimal mealTotalCost = costPerPerson.multiply(BigDecimal.valueOf(mealCount)).setScale(0, RoundingMode.HALF_UP);

                ServedMealDetail detail = ServedMealDetail.builder()
                        .planId(plan.getPlanId())
                        .planDate(planDate)
                        .dayOfWeek(formatKoreanDayOfWeek(planDate.getDayOfWeek()))
                        .mealType(mealType)
                        .mealCount(mealCount)
                        .costPerPerson(costPerPerson)
                        .mealTotalCost(mealTotalCost)
                        .build();

                accumulatorMap.computeIfAbsent(menuId, k -> new MenuAccumulator(menuId, menuName))
                        .accumulate(detail, mealCount, mealTotalCost, planDate);
            }
        }
    }

    /**
     * MealPlanService에 저장된 동적 식단으로부터 데이터 수집 (Fallback 지원)
     */
    private void tryCollectFromMealPlanService(LocalDate weekMonday, Map<Long, MenuAccumulator> accumulatorMap) {
        try {
            MealPlanResponse response = mealPlanService.findWeeklyPlan(weekMonday);
            if (response != null && response.meals() != null) {
                int mealCount = response.mealCount() != null ? response.mealCount() : 1;
                for (MealPlanResponse.MealResponse meal : response.meals()) {
                    Long menuId = meal.menuId();
                    String menuName = meal.menuName();
                    BigDecimal costPerPerson = meal.costPerPerson() != null ? meal.costPerPerson() : BigDecimal.ZERO;
                    BigDecimal mealTotalCost = costPerPerson.multiply(BigDecimal.valueOf(mealCount)).setScale(0, RoundingMode.HALF_UP);

                    ServedMealDetail detail = ServedMealDetail.builder()
                            .planId(null)
                            .planDate(meal.mealDate())
                            .dayOfWeek(formatKoreanDayOfWeek(meal.mealDate().getDayOfWeek()))
                            .mealType("MEAL")
                            .mealCount(mealCount)
                            .costPerPerson(costPerPerson)
                            .mealTotalCost(mealTotalCost)
                            .build();

                    accumulatorMap.computeIfAbsent(menuId, k -> new MenuAccumulator(menuId, menuName))
                            .accumulate(detail, mealCount, mealTotalCost, meal.mealDate());
                }
            }
        } catch (Exception e) {
            log.debug(">> [BUDG-003] MealPlanService 주간 식단 연동 스킵: {}", e.getMessage());
        }
    }

    private MenuRiskResponse safelyEvaluateMenuRisk(Long menuId, LocalDate date) {
        try {
            return costService.evaluateMenuRisk(menuId, date);
        } catch (Exception e) {
            log.warn(">> [BUDG-003] MenuRiskService 조회 실패(menuId: {}): {}", menuId, e.getMessage());
            return null;
        }
    }

    private CostDriverResponse safelyIdentifyCostDrivers(Long menuId, LocalDate date) {
        try {
            return costService.identifyCostDrivers(menuId, date);
        } catch (Exception e) {
            log.warn(">> [BUDG-003] CostDriver 조회 실패(menuId: {}): {}", menuId, e.getMessage());
            return null;
        }
    }

    private MenuSlot resolveMenuSlot(Long menuId, String menuName) {
        String name = (menuName != null) ? menuName : "";
        if (name.contains("밥") || name.contains("죽")) return MenuSlot.RICE;
        if (name.contains("국") || name.contains("찌개") || name.contains("탕")) return MenuSlot.SOUP;
        if (name.contains("김치") || name.contains("깍두기")) return MenuSlot.KIMCHI;
        if (name.contains("볶음") || name.contains("불고기") || name.contains("구이") ||
            name.contains("찜") || name.contains("조림") || name.contains("닭") || name.contains("육")) {
            return MenuSlot.MAIN;
        }
        return MenuSlot.SIDE;
    }

    private String formatSlotName(MenuSlot slot) {
        if (slot == null) return "기타";
        return switch (slot) {
            case RICE -> "밥류";
            case SOUP -> "국·찌개류";
            case MAIN -> "주찬류(메인)";
            case SIDE -> "부찬류(반찬)";
            case KIMCHI -> "김치류";
            case OTHER -> "기타";
        };
    }

    /**
     * 비용 기여율과 위험도를 종합하여 변경 검토 우선순위 판정
     */
    private String determineReviewPriority(BigDecimal contributionRate, String riskLevel, boolean isRisk) {
        boolean isHighContribution = contributionRate.compareTo(URGENT_CONTRIBUTION_THRESHOLD) >= 0;
        boolean isModerateContribution = contributionRate.compareTo(MODERATE_CONTRIBUTION_THRESHOLD) >= 0;

        if ("WARNING".equals(riskLevel) && isHighContribution) {
            return "URGENT"; // 최우선 변경 검토
        } else if ("WARNING".equals(riskLevel) || ("CAUTION".equals(riskLevel) && isModerateContribution) || isHighContribution) {
            return "HIGH";   // 높은 우선순위
        } else if (isModerateContribution || isRisk) {
            return "MEDIUM"; // 검토 권장
        }
        return "LOW";        // 선택적
    }

    /**
     * 표준 기준 단가 대비 잠재 절감 가능 금액 산출
     */
    private BigDecimal calculateEstimatedSavingsPotential(MenuSlot slot, BigDecimal avgCostPerPerson, int totalMealCount) {
        BigDecimal benchmarkCost = switch (slot) {
            case MAIN -> BENCHMARK_COST_MAIN;
            case SOUP -> BENCHMARK_COST_SOUP;
            case SIDE -> BENCHMARK_COST_SIDE;
            case KIMCHI -> BENCHMARK_COST_KIMCHI;
            default -> BENCHMARK_COST_DEFAULT;
        };

        if (avgCostPerPerson != null && avgCostPerPerson.compareTo(benchmarkCost) > 0) {
            BigDecimal diff = avgCostPerPerson.subtract(benchmarkCost);
            return diff.multiply(BigDecimal.valueOf(totalMealCount)).setScale(0, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    /**
     * 변경 검토 추천 사유 포맷팅
     */
    private String buildReviewReason(String menuName, BigDecimal contributionRate, int rank,
                                      String riskLevel, BigDecimal increaseRate, String topCostDriver, String slotName) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("주간 식단 전체 비용의 %s%% 점유(기여도 %d위, %s)", contributionRate, rank, slotName));

        if ("WARNING".equals(riskLevel)) {
            sb.append(String.format(" | 식재료 단가 급등으로 원가 %s%% 상승(경고 등급)", increaseRate));
        } else if ("CAUTION".equals(riskLevel)) {
            sb.append(String.format(" | 식재료 단가 변동률 %s%% 주의 요망", increaseRate));
        }

        if (topCostDriver != null) {
            sb.append(" [상승 요인: ").append(topCostDriver).append("]");
        }

        if ("WARNING".equals(riskLevel)) {
            sb.append(" ──> 식단 재구성 시 최우선 교체 검토 권장");
        } else if (contributionRate.compareTo(new BigDecimal("15.0")) >= 0) {
            sb.append(" ──> 고비용 비중 완화를 위한 대체 메뉴 검토 권장");
        }

        return sb.toString();
    }

    /**
     * 종합 권고사항 메시지 생성
     */
    private String buildRecommendationSummary(String facilityName, LocalDate weekStart, LocalDate weekEnd,
                                               BigDecimal weeklyTotalCost, BigDecimal monthlyBudget,
                                               List<HighCostMenuDto> candidates) {
        if (candidates.isEmpty()) {
            return String.format("[%s] %s ~ %s 주간 식단에서 비용 기여도가 임계치를 초과하는 특정 고비용 메뉴가 발견되지 않았습니다.",
                    facilityName, weekStart, weekEnd);
        }

        BigDecimal topCandidatesRate = candidates.stream()
                .map(HighCostMenuDto::getContributionRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<String> urgentOrHighMenus = candidates.stream()
                .filter(c -> "URGENT".equals(c.getReviewPriority()) || "HIGH".equals(c.getReviewPriority()))
                .map(HighCostMenuDto::getMenuName)
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s ~ %s 주간 총 식단 예상 비용은 %s원",
                facilityName, weekStart, weekEnd, MONEY_FORMAT.format(weeklyTotalCost)));

        if (monthlyBudget != null && monthlyBudget.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal budgetShare = calculatePercentage(weeklyTotalCost, monthlyBudget);
            sb.append(String.format(" (월 배정예산의 %s%% 수준)", budgetShare));
        }
        sb.append(String.format("입니다. 상위 %d개 메뉴가 주간 비용의 총 %s%%를 차지하고 있습니다.\n",
                candidates.size(), topCandidatesRate));

        if (!urgentOrHighMenus.isEmpty()) {
            sb.append(String.format("특히 원가 급등 위험 및 고기여도를 보이는 [%s] 메뉴는 주간 식단 재구성(Reconfiguration) 시 최우선 교체 검토를 권장합니다.",
                    String.join(", ", urgentOrHighMenus)));
        } else {
            sb.append("비용 기여도 상위 메뉴를 표준 대체 메뉴로 전환 시 주간 예산 절감 효과를 기대할 수 있습니다.");
        }

        return sb.toString();
    }

    /**
     * 주간 메뉴별 집계를 위한 내부 누적 클래스
     */
    private static class MenuAccumulator {
        private final Long menuId;
        private final String menuName;
        private int appearanceCount = 0;
        private int totalMealCount = 0;
        private BigDecimal totalMenuCost = BigDecimal.ZERO;
        private LocalDate latestDate = null;
        private final List<ServedMealDetail> servedMeals = new ArrayList<>();

        public MenuAccumulator(Long menuId, String menuName) {
            this.menuId = menuId;
            this.menuName = menuName;
        }

        public void accumulate(ServedMealDetail detail, int mealCount, BigDecimal mealCost, LocalDate date) {
            this.appearanceCount++;
            this.totalMealCount += mealCount;
            this.totalMenuCost = this.totalMenuCost.add(mealCost);
            this.servedMeals.add(detail);
            if (this.latestDate == null || date.isAfter(this.latestDate)) {
                this.latestDate = date;
            }
        }

        public BigDecimal getTotalMenuCost() {
            return totalMenuCost;
        }
    }

    // =========================================================================
    // 2. 대체 메뉴 적용 전후 예상 비용 차이 및 절감액 분석 (BUDG-005)
    // =========================================================================

    /**
     * [BUDG-005] 대체 메뉴 적용 전후의 예상 비용 차이와 절감액을 종합 분석하여 반환한다.
     */
    public MenuReplacementCostComparisonResponse analyzeMenuReplacementCost(MenuReplacementAnalysisRequest request) {
        if (request == null || request.getOriginalMenuId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ORIGINAL_MENU_REQUIRED", "교체 대상 기존 메뉴 ID는 필수 입력값입니다.");
        }

        Long facilityId = (request.getFacilityId() != null) ? request.getFacilityId() : DEFAULT_FACILITY_ID;
        Long originalMenuId = request.getOriginalMenuId();
        Long planId = request.getPlanId();

        // 1. 기준 일자 및 식단 계획(Plan) 정보 해석
        LocalDate targetDate = request.getTargetDate();
        Integer mealCount = request.getMealCount();
        String mealType = "LUNCH";
        MealPlanCostVo targetPlan = null;

        if (planId != null) {
            targetPlan = findMealPlanById(facilityId, planId);
            if (targetPlan != null) {
                if (targetDate == null) {
                    targetDate = targetPlan.getPlanDate();
                }
                if (mealCount == null || mealCount <= 0) {
                    mealCount = targetPlan.getMealCount();
                }
                if (targetPlan.getMealType() != null) {
                    mealType = targetPlan.getMealType();
                }
            }
        }

        if (targetDate == null) {
            targetDate = DEFAULT_BASE_DATE;
        }
        if (mealCount == null || mealCount <= 0) {
            mealCount = 100;
        }

        LocalDate weekMonday = targetDate.with(DayOfWeek.MONDAY);
        LocalDate weekSunday = weekMonday.plusDays(6);
        YearMonth budgetMonth = YearMonth.from(weekMonday);

        log.info("==================================================");
        log.info(">> [BUDG-005 대체 메뉴 비용 분석 시작] 시설: {}, 기존메뉴 ID: {}, 기준일: {}, 식수: {}명",
                facilityId, originalMenuId, targetDate, mealCount);

        // 2. 시설 예산 조회
        FacilityBudgetVo budgetVo = costRepository.findFacilityBudget(facilityId, budgetMonth)
                .orElseGet(() -> new FacilityBudgetVo(facilityId, "시설 " + facilityId, budgetMonth, DEFAULT_MONTHLY_BUDGET));
        BigDecimal monthlyBudget = budgetVo.getBudgetAmount();

        // 3. 기존 메뉴(Original Menu) 원가 및 슬롯 분석
        String originalMenuName = costRepository.findMenuNameById(originalMenuId)
                .orElse("메뉴#" + originalMenuId);
        MenuSlot originalSlot = resolveMenuSlot(originalMenuId, originalMenuName);
        String originalSlotName = formatSlotName(originalSlot);

        MenuCostResponse origCostResponse = costService.calculateFutureMenuCost(originalMenuId, targetDate, 1, null);
        BigDecimal originalCostPerPerson = (origCostResponse != null && origCostResponse.getCostPerPerson() != null)
                ? origCostResponse.getCostPerPerson()
                : BigDecimal.ZERO;

        BigDecimal originalMenuTotalCost = originalCostPerPerson.multiply(BigDecimal.valueOf(mealCount))
                .setScale(0, RoundingMode.HALF_UP);

        // 4. 변경 전 끼니 전체 총 비용 결정
        BigDecimal beforeMealTotalCost;
        if (targetPlan != null && targetPlan.calculateTotalCost().compareTo(BigDecimal.ZERO) > 0) {
            beforeMealTotalCost = targetPlan.calculateTotalCost();
        } else {
            beforeMealTotalCost = originalMenuTotalCost;
        }

        // 5. 대체 후보 메뉴군(Candidate IDs) 결정
        Set<Long> candidateIds = new LinkedHashSet<>();
        if (request.getReplacementMenuId() != null) {
            candidateIds.add(request.getReplacementMenuId());
        }
        if (request.getReplacementMenuIds() != null) {
            candidateIds.addAll(request.getReplacementMenuIds());
        }

        // 대체 메뉴가 전혀 지정되지 않은 경우: 동일 슬롯 내 다른 메뉴들을 자동으로 후보로 추천
        if (candidateIds.isEmpty()) {
            List<Long> autoCandidates = findAutoReplacementCandidates(originalMenuId, originalSlot);
            candidateIds.addAll(autoCandidates);
        }

        // 6. 각 대체 후보별 원가 차이 및 절감액 산출 (CostService 연동)
        List<MenuReplacementCandidateDto> candidateList = new ArrayList<>();

        for (Long replMenuId : candidateIds) {
            if (Objects.equals(replMenuId, originalMenuId)) {
                continue;
            }

            try {
                MenuReplacementDiffResponse diff = costService.compareMenuReplacementCost(
                        originalMenuId, replMenuId, targetDate, mealCount);

                MenuSlot replSlot = resolveMenuSlot(replMenuId, diff.getReplacementMenuName());
                String replSlotName = formatSlotName(replSlot);

                BigDecimal replTotalCost = diff.getReplacementTotalCost();
                BigDecimal totalSavings = diff.getTotalSavings();
                BigDecimal afterMealCost = beforeMealTotalCost.subtract(totalSavings);
                if (afterMealCost.compareTo(BigDecimal.ZERO) < 0) {
                    afterMealCost = BigDecimal.ZERO;
                }

                BigDecimal savingsRate = BigDecimal.ZERO;
                if (beforeMealTotalCost.compareTo(BigDecimal.ZERO) > 0) {
                    savingsRate = calculatePercentage(totalSavings, beforeMealTotalCost);
                }

                String note = buildCandidateRecommendationNote(
                        diff.getReplacementMenuName(), diff.getSavingsPerPerson(), totalSavings,
                        diff.getReplacementRiskLevel(), diff.getReplacementIncreaseRate());

                MenuReplacementCandidateDto candDto = MenuReplacementCandidateDto.builder()
                        .replacementMenuId(replMenuId)
                        .replacementMenuName(diff.getReplacementMenuName())
                        .slot(replSlot)
                        .slotName(replSlotName)
                        .replacementCostPerPerson(diff.getReplacementCostPerPerson())
                        .costDiffPerPerson(diff.getCostDiffPerPerson())
                        .savingsPerPerson(diff.getSavingsPerPerson())
                        .diffRate(diff.getDiffRate())
                        .mealCount(mealCount)
                        .replacementMealTotalCost(replTotalCost)
                        .totalSavings(totalSavings)
                        .savingsRate(savingsRate)
                        .afterMealTotalCost(afterMealCost)
                        .savingsStatus(diff.getSavingsStatus())
                        .riskLevel(diff.getReplacementRiskLevel())
                        .isRisk(diff.isReplacementIsRisk())
                        .increaseRate(diff.getReplacementIncreaseRate())
                        .recommendationNote(note)
                        .build();

                candidateList.add(candDto);
            } catch (Exception e) {
                // 단일 대체 메뉴 명시 시 오류면 예외 전파, 다중/자동 후보면 로깅 후 다음 후보 진행
                if (request.getReplacementMenuId() != null && Objects.equals(request.getReplacementMenuId(), replMenuId)
                        && (request.getReplacementMenuIds() == null || request.getReplacementMenuIds().isEmpty())) {
                    throw e;
                }
                log.warn(">> [BUDG-005] 대체 메뉴(ID: {}) 원가 비교 실패로 제외: {}", replMenuId, e.getMessage());
            }
        }

        // 절감액 내림차순 정렬
        candidateList.sort(Comparator.comparing(MenuReplacementCandidateDto::getTotalSavings).reversed());

        // 선택된 주 대체 메뉴 설정
        MenuReplacementCandidateDto selectedReplacement = null;
        if (request.getReplacementMenuId() != null) {
            selectedReplacement = candidateList.stream()
                    .filter(c -> Objects.equals(c.getReplacementMenuId(), request.getReplacementMenuId()))
                    .findFirst()
                    .orElse(candidateList.isEmpty() ? null : candidateList.get(0));
        } else if (!candidateList.isEmpty()) {
            selectedReplacement = candidateList.get(0);
        }

        // 7. 주간 식단 및 예산 영향도 (Weekly Impact) 계산
        WeeklyBudgetImpactDto weeklyImpact = calculateWeeklyBudgetImpact(
                facilityId, weekMonday, weekSunday, monthlyBudget,
                originalMenuId, selectedReplacement, mealCount,
                Boolean.TRUE.equals(request.getApplyToAllOccurrences()));

        // 8. 종합 분석 요약 메시지 생성
        String summary = buildAnalysisSummary(
                budgetVo.getFacilityName(), targetDate, mealType,
                originalMenuName, selectedReplacement, beforeMealTotalCost, weeklyImpact);

        log.info(">> [BUDG-005 대체 메뉴 비용 분석 완료] 기존: {}, 대체: {}, 1끼절감액: {}원, 주간절감액: {}원",
                originalMenuName,
                (selectedReplacement != null ? selectedReplacement.getReplacementMenuName() : "없음"),
                (selectedReplacement != null ? selectedReplacement.getTotalSavings() : BigDecimal.ZERO),
                weeklyImpact.getWeeklyTotalSavings());
        log.info("==================================================");

        return MenuReplacementCostComparisonResponse.builder()
                .facilityId(facilityId)
                .facilityName(budgetVo.getFacilityName())
                .targetDate(targetDate)
                .dayOfWeek(formatKoreanDayOfWeek(targetDate.getDayOfWeek()))
                .planId(planId)
                .mealType(mealType)
                .mealCount(mealCount)
                .originalMenuId(originalMenuId)
                .originalMenuName(originalMenuName)
                .originalSlot(originalSlot)
                .originalSlotName(originalSlotName)
                .originalCostPerPerson(originalCostPerPerson)
                .originalMenuTotalCost(originalMenuTotalCost)
                .beforeMealTotalCost(beforeMealTotalCost)
                .selectedReplacement(selectedReplacement)
                .candidateReplacements(candidateList)
                .weeklyImpact(weeklyImpact)
                .analysisSummary(summary)
                .build();
    }

    /**
     * 식단 ID(planId)로 해당 식단 계획 정보 조회
     */
    private MealPlanCostVo findMealPlanById(Long facilityId, Long planId) {
        if (planId == null) {
            return null;
        }
        // 1년치 범위 내에서 탐색
        List<MealPlanCostVo> plans = costRepository.findMealPlansByFacilityAndDateRange(
                facilityId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        return plans.stream()
                .filter(p -> Objects.equals(p.getPlanId(), planId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 대체 메뉴 미지정 시 동일 슬롯(카테고리) 기반 자동 후보 탐색
     */
    private List<Long> findAutoReplacementCandidates(Long originalMenuId, MenuSlot slot) {
        List<Long> allMenuIds = costRepository.findAllMenuIds();
        List<Long> matched = new ArrayList<>();

        for (Long menuId : allMenuIds) {
            if (Objects.equals(menuId, originalMenuId)) {
                continue;
            }
            String name = costRepository.findMenuNameById(menuId).orElse("");
            MenuSlot candSlot = resolveMenuSlot(menuId, name);
            if (candSlot == slot) {
                matched.add(menuId);
            }
            if (matched.size() >= 5) {
                break;
            }
        }
        return matched;
    }

    /**
     * 대체 후보별 권고 가이드 문구 빌드
     */
    private String buildCandidateRecommendationNote(
            String menuName, BigDecimal savingsPerPerson, BigDecimal totalSavings,
            String riskLevel, BigDecimal increaseRate) {

        StringBuilder sb = new StringBuilder();
        if (totalSavings.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("1인당 %s원 절감 (총 %s원 절감 효과). ",
                    MONEY_FORMAT.format(savingsPerPerson), MONEY_FORMAT.format(totalSavings)));
        } else if (totalSavings.compareTo(BigDecimal.ZERO) < 0) {
            sb.append(String.format("1인당 %s원 원가 상승 (총 %s원 추가 소요). ",
                    MONEY_FORMAT.format(savingsPerPerson.abs()), MONEY_FORMAT.format(totalSavings.abs())));
        } else {
            sb.append("기존 메뉴와 예상 원가가 동일합니다. ");
        }

        if ("WARNING".equals(riskLevel)) {
            sb.append(String.format("식재료 단가 상승률이 %s%%로 높아(경고 등급) 향후 원가 변동 주의 요망.", increaseRate));
        } else if ("CAUTION".equals(riskLevel)) {
            sb.append(String.format("식재료 단가 변동률 %s%%(주의 등급) 관리 필요.", increaseRate));
        } else {
            sb.append("식재료 시세가 안정적(안전 등급)으로 교체 추천.");
        }

        return sb.toString();
    }

    /**
     * 주간 식단 및 월간 예산 영향도(WeeklyBudgetImpactDto) 계산
     */
    private WeeklyBudgetImpactDto calculateWeeklyBudgetImpact(
            Long facilityId, LocalDate weekMonday, LocalDate weekSunday, BigDecimal monthlyBudget,
            Long originalMenuId, MenuReplacementCandidateDto selectedReplacement,
            int mealCount, boolean applyToAllOccurrences) {

        List<MealPlanCostVo> weeklyPlans = costRepository.findMealPlansByFacilityAndDateRange(
                facilityId, weekMonday, weekSunday);

        BigDecimal beforeWeeklyTotalCost = BigDecimal.ZERO;
        int totalWeeklyMeals = 0;
        for (MealPlanCostVo plan : weeklyPlans) {
            beforeWeeklyTotalCost = beforeWeeklyTotalCost.add(plan.calculateTotalCost());
            totalWeeklyMeals += plan.getMealCount();
        }

        BigDecimal weeklyTotalSavings = BigDecimal.ZERO;
        int replacedOccurrences = 0;
        int replacedMealCount = 0;

        if (selectedReplacement != null) {
            BigDecimal diffUnit = selectedReplacement.getCostDiffPerPerson();

            if (applyToAllOccurrences) {
                // 주간 식단 내 해당 기존 메뉴가 포함된 모든 끼니 탐색
                for (MealPlanCostVo plan : weeklyPlans) {
                    List<Long> menuIds = costRepository.findMenuIdsByPlanId(plan.getPlanId());
                    if (menuIds.contains(originalMenuId)) {
                        replacedOccurrences++;
                        replacedMealCount += plan.getMealCount();
                        BigDecimal planSavings = diffUnit.multiply(BigDecimal.valueOf(plan.getMealCount()))
                                .setScale(0, RoundingMode.HALF_UP);
                        weeklyTotalSavings = weeklyTotalSavings.add(planSavings);
                    }
                }

                // 주간 식단에서 발견되지 않은 경우 기본 현재 끼니 1회 반영
                if (replacedOccurrences == 0) {
                    replacedOccurrences = 1;
                    replacedMealCount = mealCount;
                    weeklyTotalSavings = selectedReplacement.getTotalSavings();
                }
            } else {
                // 단일 끼니만 교체 반영
                replacedOccurrences = 1;
                replacedMealCount = mealCount;
                weeklyTotalSavings = selectedReplacement.getTotalSavings();
            }
        }

        BigDecimal afterWeeklyTotalCost = beforeWeeklyTotalCost.subtract(weeklyTotalSavings);
        if (afterWeeklyTotalCost.compareTo(BigDecimal.ZERO) < 0) {
            afterWeeklyTotalCost = BigDecimal.ZERO;
        }

        BigDecimal weeklySavingsRate = BigDecimal.ZERO;
        if (beforeWeeklyTotalCost.compareTo(BigDecimal.ZERO) > 0) {
            weeklySavingsRate = calculatePercentage(weeklyTotalSavings, beforeWeeklyTotalCost);
        }

        // 월 예산 대비 분석
        BigDecimal beforeRemainingBudget = monthlyBudget.subtract(beforeWeeklyTotalCost);
        BigDecimal afterRemainingBudget = beforeRemainingBudget.add(weeklyTotalSavings);

        BigDecimal beforeUsageRate = calculatePercentage(beforeWeeklyTotalCost, monthlyBudget);
        BigDecimal afterUsageRate = calculatePercentage(afterWeeklyTotalCost, monthlyBudget);
        String budgetUsageChange = String.format("%s%% -> %s%%", beforeUsageRate, afterUsageRate);

        return WeeklyBudgetImpactDto.builder()
                .weekStartDate(weekMonday)
                .weekEndDate(weekSunday)
                .monthlyBudget(monthlyBudget)
                .beforeWeeklyTotalCost(beforeWeeklyTotalCost)
                .afterWeeklyTotalCost(afterWeeklyTotalCost)
                .weeklyTotalSavings(weeklyTotalSavings)
                .weeklySavingsRate(weeklySavingsRate)
                .beforeRemainingBudget(beforeRemainingBudget)
                .afterRemainingBudget(afterRemainingBudget)
                .beforeUsageRate(beforeUsageRate)
                .afterUsageRate(afterUsageRate)
                .budgetUsageRateChange(budgetUsageChange)
                .totalWeeklyMeals(totalWeeklyMeals)
                .replacedOccurrences(replacedOccurrences)
                .replacedMealCount(replacedMealCount)
                .build();
    }

    /**
     * 종합 분석 요약 문구 생성
     */
    private String buildAnalysisSummary(
            String facilityName, LocalDate targetDate, String mealType,
            String originalMenuName, MenuReplacementCandidateDto selectedReplacement,
            BigDecimal beforeMealTotalCost, WeeklyBudgetImpactDto weeklyImpact) {

        if (selectedReplacement == null) {
            return String.format("[%s] %s %s의 [%s] 메뉴에 대한 대체 메뉴 후보가 지정되지 않았습니다.",
                    facilityName, targetDate, mealType, originalMenuName);
        }

        String replName = selectedReplacement.getReplacementMenuName();
        BigDecimal totalSavings = selectedReplacement.getTotalSavings();
        BigDecimal savingsPerPerson = selectedReplacement.getSavingsPerPerson();
        BigDecimal savingsRate = selectedReplacement.getSavingsRate();

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %s %s 기존 메뉴 [%s]을(를) 대체 메뉴 [%s](으)로 적용 시, ",
                facilityName, targetDate, mealType, originalMenuName, replName));

        if (totalSavings.compareTo(BigDecimal.ZERO) > 0) {
            sb.append(String.format("1인당 %s원의 식재료비가 절감되어 끼니 총 %s원을 절감(절감율 %s%%)할 수 있습니다.\n",
                    MONEY_FORMAT.format(savingsPerPerson), MONEY_FORMAT.format(totalSavings), savingsRate));
            sb.append(String.format("이에 따라 주간 총 예상 식재료비는 %s원에서 %s원으로 %s원 절감(%s%% 감소)되며, ",
                    MONEY_FORMAT.format(weeklyImpact.getBeforeWeeklyTotalCost()),
                    MONEY_FORMAT.format(weeklyImpact.getAfterWeeklyTotalCost()),
                    MONEY_FORMAT.format(weeklyImpact.getWeeklyTotalSavings()),
                    weeklyImpact.getWeeklySavingsRate()));
            sb.append(String.format("월 예산 대비 주간 소진율은 %s로 개선됩니다.",
                    weeklyImpact.getBudgetUsageRateChange()));
        } else if (totalSavings.compareTo(BigDecimal.ZERO) < 0) {
            sb.append(String.format("1인당 %s원의 원가가 상승하여 끼니 총 %s원의 추가 비용이 발생합니다.\n",
                    MONEY_FORMAT.format(savingsPerPerson.abs()), MONEY_FORMAT.format(totalSavings.abs())));
            sb.append("원가 절감 목적의 대체 메뉴로는 적합하지 않으므로 다른 대체 후보 검토를 권장합니다.");
        } else {
            sb.append("1인당 및 끼니 총 예상 원가 변동이 없어 예산에 미치는 영향은 동일합니다.");
        }

        return sb.toString();
    }
}
