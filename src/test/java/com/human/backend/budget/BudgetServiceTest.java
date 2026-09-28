package com.human.backend.budget;

import com.human.backend.budget.dto.request.WeeklyHighCostMenuRequest;
import com.human.backend.budget.dto.response.HighCostMenuDto;
import com.human.backend.budget.dto.response.WeeklyHighCostMenuCandidateResponse;
import com.human.backend.budget.service.BudgetService;
import com.human.backend.cost.repository.MemoryCostRepository;
import com.human.backend.cost.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BudgetServiceTest {

    private BudgetService budgetService;
    private MemoryCostRepository costRepository;
    private CostService costService;

    @BeforeEach
    void setUp() {
        costRepository = new MemoryCostRepository();
        costRepository.init();

        MenuCostService menuCostService = new MenuCostService(costRepository);
        MenuRiskService menuRiskService = new MenuRiskService(menuCostService, costRepository);
        MealPlanCostService mealPlanCostService = new MealPlanCostService(costRepository);
        BudgetAnalysisService budgetAnalysisService = new BudgetAnalysisService(costRepository);

        costService = new CostService(menuCostService, menuRiskService, mealPlanCostService, budgetAnalysisService);
        budgetService = new BudgetService(costRepository, costService);
    }

    @Test
    @DisplayName("[BUDG-003] 선택 주차 식단에서 비용 기여도가 높은 메뉴를 식별하고 변경 검토 후보로 반환한다")
    void testIdentifyHighCostMenuCandidates() {
        // Given: 2026-09-17이 포함된 주차 (2026-09-14 ~ 2026-09-20)
        WeeklyHighCostMenuRequest request = WeeklyHighCostMenuRequest.builder()
                .facilityId(1L)
                .weekStartDate(LocalDate.of(2026, 9, 14))
                .topN(5)
                .build();

        // When
        WeeklyHighCostMenuCandidateResponse response = budgetService.identifyHighCostMenuCandidates(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFacilityId()).isEqualTo(1L);
        assertThat(response.getWeekStartDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.getWeekEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(response.getWeeklyTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getCandidates()).isNotEmpty();
        assertThat(response.getCandidates().size()).isLessThanOrEqualTo(5);

        // 순위 및 기여도 내림차순 정렬 검증
        List<HighCostMenuDto> candidates = response.getCandidates();
        for (int i = 0; i < candidates.size(); i++) {
            HighCostMenuDto candidate = candidates.get(i);
            assertThat(candidate.getRank()).isEqualTo(i + 1);
            assertThat(candidate.getMenuName()).isNotBlank();
            assertThat(candidate.getWeeklyMenuCost()).isGreaterThan(BigDecimal.ZERO);
            assertThat(candidate.getContributionRate()).isGreaterThan(BigDecimal.ZERO);
            assertThat(candidate.getServedMeals()).isNotEmpty();

            if (i > 0) {
                // 이전 순위 메뉴의 총 비용이 현재 메뉴보다 크거나 같아야 함
                assertThat(candidates.get(i - 1).getWeeklyMenuCost())
                        .isGreaterThanOrEqualTo(candidate.getWeeklyMenuCost());
            }
        }

        // 권고 요약 메시지 검증
        assertThat(response.getRecommendationSummary()).isNotBlank();
        System.out.println(">> [테스트 성공] 주간 총 비용: " + response.getWeeklyTotalCost() + "원");
        System.out.println(">> 상위 후보 1위: " + candidates.get(0).getMenuName() +
                ", 기여율: " + candidates.get(0).getContributionRate() + "%" +
                ", 사유: " + candidates.get(0).getReviewReason());
        System.out.println(">> 요약: " + response.getRecommendationSummary());
    }

    @Test
    @DisplayName("[BUDG-003] 최소 기여율(minContributionRate) 필터링이 정상 동작한다")
    void testIdentifyWithMinContributionRateFilter() {
        // Given: 15% 이상 기여하는 메뉴만 필터링
        WeeklyHighCostMenuRequest request = WeeklyHighCostMenuRequest.builder()
                .facilityId(1L)
                .weekStartDate(LocalDate.of(2026, 9, 14))
                .topN(10)
                .minContributionRate(new BigDecimal("15.0"))
                .build();

        // When
        WeeklyHighCostMenuCandidateResponse response = budgetService.identifyHighCostMenuCandidates(request);

        // Then
        assertThat(response).isNotNull();
        for (HighCostMenuDto candidate : response.getCandidates()) {
            assertThat(candidate.getContributionRate()).isGreaterThanOrEqualTo(new BigDecimal("15.0"));
        }
    }

    @Test
    @DisplayName("[BUDG-005] 대체 메뉴 적용 전후 예상 비용 차이와 절감액을 산출한다 (단일 대체 메뉴)")
    void testAnalyzeMenuReplacementCost_SingleReplacement() {
        // Given: 제육볶음(ID: 9)을 두부조림(ID: 15)으로 대체 교체 (식수 520명, 기준일: 2026-09-17)
        com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request =
                com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest.builder()
                        .facilityId(1L)
                        .originalMenuId(9L)
                        .replacementMenuId(15L)
                        .targetDate(LocalDate.of(2026, 9, 17))
                        .mealCount(520)
                        .build();

        // When
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getOriginalMenuId()).isEqualTo(9L);
        assertThat(response.getOriginalMenuName()).contains("제육볶음");
        assertThat(response.getOriginalCostPerPerson()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getBeforeMealTotalCost()).isGreaterThan(BigDecimal.ZERO);

        // 선택된 대체 메뉴 검증
        com.human.backend.budget.dto.response.MenuReplacementCandidateDto selected = response.getSelectedReplacement();
        assertThat(selected).isNotNull();
        assertThat(selected.getReplacementMenuId()).isEqualTo(15L);
        assertThat(selected.getReplacementMenuName()).contains("두부조림");
        assertThat(selected.getCostDiffPerPerson()).isNotNull();
        assertThat(selected.getTotalSavings()).isNotNull();
        assertThat(selected.getAfterMealTotalCost()).isNotNull();
        assertThat(selected.getSavingsStatus()).isNotBlank();
        assertThat(selected.getRiskLevel()).isNotBlank();
        assertThat(selected.getRecommendationNote()).isNotBlank();

        // 주간 식단 및 예산 영향도 검증
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse.WeeklyBudgetImpactDto weeklyImpact =
                response.getWeeklyImpact();
        assertThat(weeklyImpact).isNotNull();
        assertThat(weeklyImpact.getBeforeWeeklyTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(weeklyImpact.getAfterWeeklyTotalCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(weeklyImpact.getBudgetUsageRateChange()).isNotBlank();

        // 분석 요약 문구 검증
        assertThat(response.getAnalysisSummary()).isNotBlank();

        System.out.println(">> [BUDG-005 단일 교체 테스트 성공]");
        System.out.println("   - 기존 메뉴: " + response.getOriginalMenuName() + " (1인분: " + response.getOriginalCostPerPerson() + "원)");
        System.out.println("   - 대체 메뉴: " + selected.getReplacementMenuName() + " (1인분: " + selected.getReplacementCostPerPerson() + "원)");
        System.out.println("   - 1인분 차액: " + selected.getCostDiffPerPerson() + "원, 식수(520명) 총 절감액: " + selected.getTotalSavings() + "원");
        System.out.println("   - 교체 후 끼니 총비용: " + selected.getAfterMealTotalCost() + "원 (절감율: " + selected.getSavingsRate() + "%)");
        System.out.println("   - 요약문: " + response.getAnalysisSummary());
    }

    @Test
    @DisplayName("[BUDG-005] 다중 대체 메뉴 후보군을 전달하면 절감액 순으로 비교 정렬한다")
    void testAnalyzeMenuReplacementCost_MultipleCandidates() {
        // Given: 제육볶음(ID: 9)에 대해 후보 3개 (10: 돼지불고기, 15: 두부조림, 16: 계란말이)
        com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request =
                com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest.builder()
                        .facilityId(1L)
                        .originalMenuId(9L)
                        .replacementMenuIds(List.of(10L, 15L, 16L))
                        .targetDate(LocalDate.of(2026, 9, 17))
                        .mealCount(200)
                        .build();

        // When
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCandidateReplacements()).hasSize(3);

        List<com.human.backend.budget.dto.response.MenuReplacementCandidateDto> candidates = response.getCandidateReplacements();
        for (int i = 0; i < candidates.size() - 1; i++) {
            assertThat(candidates.get(i).getTotalSavings())
                    .isGreaterThanOrEqualTo(candidates.get(i + 1).getTotalSavings());
        }

        System.out.println(">> [BUDG-005 다중 후보 비교 테스트 성공]");
        for (com.human.backend.budget.dto.response.MenuReplacementCandidateDto cand : candidates) {
            System.out.println("   - 후보: " + cand.getReplacementMenuName() +
                    " | 1인 단가: " + cand.getReplacementCostPerPerson() + "원" +
                    " | 1인 절감액: " + cand.getSavingsPerPerson() + "원" +
                    " | 식수(200명) 총 절감액: " + cand.getTotalSavings() + "원" +
                    " | 위험도: " + cand.getRiskLevel());
        }
    }

    @Test
    @DisplayName("[BUDG-005] 대체 메뉴를 지정하지 않아도 동일 카테고리(슬롯) 후보를 자동 추천한다")
    void testAnalyzeMenuReplacementCost_AutoCandidates() {
        // Given: 김치찌개(ID: 6 - 국·찌개류) 대체 메뉴 미지정
        com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request =
                com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest.builder()
                        .facilityId(1L)
                        .originalMenuId(6L)
                        .targetDate(LocalDate.of(2026, 9, 17))
                        .mealCount(150)
                        .build();

        // When
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getCandidateReplacements()).isNotEmpty();
        assertThat(response.getSelectedReplacement()).isNotNull();

        System.out.println(">> [BUDG-005 자동 후보 추천 테스트 성공]");
        System.out.println("   - 기존 메뉴: " + response.getOriginalMenuName() + " (" + response.getOriginalSlotName() + ")");
        System.out.println("   - 자동 추천 1위: " + response.getSelectedReplacement().getReplacementMenuName() +
                " (" + response.getSelectedReplacement().getSlotName() + ")");
    }

    @Test
    @DisplayName("[BUDG-005] 주간 식단 내 일괄 교체(applyToAllOccurrences=true) 시 주간 총 절감액이 반영된다")
    void testAnalyzeMenuReplacementCost_ApplyToAllOccurrences() {
        // Given
        com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request =
                com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest.builder()
                        .facilityId(1L)
                        .originalMenuId(9L)
                        .replacementMenuId(15L)
                        .targetDate(LocalDate.of(2026, 9, 17))
                        .mealCount(520)
                        .applyToAllOccurrences(true)
                        .build();

        // When
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);

        // Then
        assertThat(response).isNotNull();
        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse.WeeklyBudgetImpactDto impact =
                response.getWeeklyImpact();
        assertThat(impact).isNotNull();
        assertThat(impact.getReplacedOccurrences()).isGreaterThan(0);
        assertThat(impact.getReplacedMealCount()).isGreaterThan(0);
        assertThat(impact.getWeeklyTotalSavings()).isNotNull();

        System.out.println(">> [BUDG-005 일괄 교체 테스트 성공] 주간 내 교체 끼니수: " + impact.getReplacedOccurrences() +
                ", 총 식수인원: " + impact.getReplacedMealCount() + "명, 주간 총 절감액: " + impact.getWeeklyTotalSavings() + "원");
    }
}

