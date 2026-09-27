package com.human.backend.automation.service;

import com.human.backend.automation.dto.request.MenuReplacementCandidateRequest;
import com.human.backend.automation.dto.response.MenuReplacementCandidateResponse;
import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostComparisonResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.MenuRiskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static com.human.backend.cost.util.CostConstants.*;

/**
 * [AUTO-006] 주간 재평가 메뉴 변경 검토 후보 탐지 전담 서비스
 * 
 * [단일 책임 원칙(SRP)]:
 * 주간 재평가 결과를 바탕으로 원가 급등, 가격 위험, 목표단가/예산 초과에 영향을 크게 주는 메뉴를
 * 다차원으로 분석하여 '변경 검토 후보(Replacement Candidates)'로 식별 및 랭킹하는 책임을 전담합니다.
 * 실제 식단 내 유지/교체 확정 결정은 주간 식단 재구성 모듈에서 수행됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MenuCandidateDetectionService {

    private final MenuRiskService menuRiskService;
    private final CostRepository costRepository;

    /**
     * [AUTO-006] 원가 급등·가격 위험·목표단가 초과 메뉴를 변경 검토 후보로 탐지
     */
    public MenuReplacementCandidateResponse detectCandidates(MenuReplacementCandidateRequest request) {
        Long facilityId = (request != null && request.getFacilityId() != null)
                ? request.getFacilityId() : DEFAULT_FACILITY_ID;

        LocalDate targetDate = (request != null && request.getTargetDate() != null)
                ? request.getTargetDate() : DEFAULT_PREDICTION_DATE;

        LocalDate weekMonday = (request != null && request.getWeekStartDate() != null)
                ? request.getWeekStartDate().with(DayOfWeek.MONDAY)
                : targetDate.with(DayOfWeek.MONDAY);
        LocalDate weekSunday = weekMonday.plusDays(6);

        int mealCount = (request != null && request.getMealCount() != null && request.getMealCount() > 0)
                ? request.getMealCount() : 1;

        BigDecimal targetCost = (request != null && request.getTargetCost() != null)
                ? request.getTargetCost() : DEFAULT_TARGET_COST;

        BigDecimal surgeThresholdRate = (request != null && request.getSurgeThresholdRate() != null)
                ? request.getSurgeThresholdRate() : new BigDecimal("10.0");

        log.info(">> [AUTO-006 후보 탐지 시작] 시설 ID: {}, 주차: {} ~ {}, 기준일: {}, 목표단가: {}원, 급등기준: {}%",
                facilityId, weekMonday, weekSunday, targetDate, targetCost, surgeThresholdRate);

        // 1. 해당 주차에 편성된 식단 목록 조회 (일자 및 끼니 매핑용)
        List<MealPlanCostVo> weeklyPlans = costRepository.findMealPlansByFacilityAndDateRange(facilityId, weekMonday, weekSunday);
        Map<Long, MealPlanCostVo> planMenuMap = new HashMap<>();
        for (MealPlanCostVo plan : weeklyPlans) {
            if (plan.getPlanId() != null) {
                planMenuMap.put(plan.getPlanId(), plan);
            }
        }

        // 2. 전체 평가 대상 메뉴 ID 목록 도출 (주간 편성 메뉴 우선 + 전체 등록 메뉴)
        List<Long> allMenuIds = costRepository.findAllMenuIds();
        Set<Long> targetMenuIdSet = new LinkedHashSet<>();
        targetMenuIdSet.addAll(planMenuMap.keySet());
        targetMenuIdSet.addAll(allMenuIds);

        List<MenuReplacementCandidateResponse.MenuReplacementCandidate> candidateList = new ArrayList<>();
        int totalEvaluated = 0;

        // 3. 각 메뉴별 다차원 지표 진단 및 후보 필터링 (DTO 팩토리를 활용한 높은 응집도)
        for (Long menuId : targetMenuIdSet) {
            totalEvaluated++;

            // (1) 원가 변동 비교 및 위험도/식재료 진단
            MenuCostComparisonResponse comparison = menuRiskService.compareMenuCost(menuId, targetDate, mealCount);
            MenuRiskResponse riskResponse = menuRiskService.evaluateMenuRisk(menuId, targetDate);
            CostDriverResponse driverResponse = menuRiskService.identifyCostDrivers(menuId, targetDate);

            // (2) 변경 검토 후보 해당 여부 판정
            if (MenuReplacementCandidateResponse.MenuReplacementCandidate.isCandidate(
                    comparison.getIncreaseRate(), riskResponse.getRiskLevel(),
                    comparison.getCurrentCostPerPerson(), comparison.getFutureCostPerPerson(),
                    targetCost, surgeThresholdRate)) {

                MealPlanCostVo planInfo = planMenuMap.get(menuId);
                candidateList.add(MenuReplacementCandidateResponse.MenuReplacementCandidate.from(
                        comparison, riskResponse, driverResponse, planInfo, targetCost, surgeThresholdRate));
            }
        }

        // 4. 영향도 점수 기준 내림차순 정렬 (영향이 큰 메뉴가 최상단)
        candidateList.sort(Comparator.comparing(MenuReplacementCandidateResponse.MenuReplacementCandidate::getImpactScore,
                Comparator.nullsLast(Comparator.reverseOrder())));

        // 5. 전체 종합 요약 메시지
        String summary = String.format("총 %d개 평가 대상 메뉴 중 원가 급등·가격 위험·목표단가 초과 영향을 미치는 %d개의 변경 검토 후보 메뉴가 탐지되었습니다.",
                totalEvaluated, candidateList.size());

        log.info(">> [AUTO-006 후보 탐지 완료] 총 평가: {}건, 탐지된 후보: {}건", totalEvaluated, candidateList.size());

        return MenuReplacementCandidateResponse.builder()
                .facilityId(facilityId)
                .weekStartDate(weekMonday)
                .targetDate(targetDate)
                .totalMenusEvaluated(totalEvaluated)
                .candidateCount(candidateList.size())
                .evaluationSummary(summary)
                .evaluatedAt(LocalDateTime.now())
                .candidates(candidateList)
                .build();
    }
}
