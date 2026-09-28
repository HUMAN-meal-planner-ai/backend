package com.human.backend.dashboard;

import com.human.backend.cost.repository.MemoryCostRepository;
import com.human.backend.cost.service.*;
import com.human.backend.dashboard.dto.response.DashboardMenuRiskRatioResponse;
import com.human.backend.dashboard.dto.response.DashboardRiskMenuDto;
import com.human.backend.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardServiceTest {

    private DashboardService dashboardService;
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
        dashboardService = new DashboardService(costRepository, costService);
    }

    @Test
    @DisplayName("[DASH-005] 현재 주간 식단 내 가격 위험 메뉴 비중 요약을 정상 산출한다")
    void testSummarizeMenuRiskRatio_Weekly() {
        // Given: 2026-09-17이 포함된 주간 (2026-09-14 ~ 2026-09-20)
        Long facilityId = 1L;
        LocalDate baseDate = LocalDate.of(2026, 9, 17);

        // When
        DashboardMenuRiskRatioResponse response = dashboardService.summarizeMenuRiskRatio(facilityId, baseDate, "WEEKLY");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFacilityId()).isEqualTo(facilityId);
        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2026, 9, 20));
        assertThat(response.getPeriodType()).isEqualTo("WEEKLY");

        // 수량 기준 지표 검증
        assertThat(response.getTotalMenuCount()).isGreaterThan(0);
        assertThat(response.getUniqueMenuCount()).isGreaterThan(0);
        assertThat(response.getRiskMenuRatio()).isNotNull();
        assertThat(response.getUniqueRiskMenuRatio()).isNotNull();

        // 합산 검증: warning + caution + safe == totalMenuCount
        int calculatedTotalCount = response.getWarningCount() + response.getCautionCount() + response.getSafeCount();
        assertThat(calculatedTotalCount).isEqualTo(response.getTotalMenuCount());

        // 비율 합산 근사치 검증 (반올림 오차 감안 99% ~ 101%)
        BigDecimal sumRatio = response.getWarningRatio().add(response.getCautionRatio()).add(response.getSafeRatio());
        assertThat(sumRatio.doubleValue()).isBetween(99.0, 101.0);

        // 비용 기준 지표 검증
        assertThat(response.getTotalPlannedCost()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getRiskCostRatio()).isNotNull();
        assertThat(response.getWarningTotalCost()).isNotNull();
        assertThat(response.getCautionTotalCost()).isNotNull();
        assertThat(response.getSafeTotalCost()).isNotNull();

        // 상태 및 브리핑 문구 검증
        assertThat(response.getOverallRiskLevel()).isIn("CRITICAL", "WARNING", "CAUTION", "SAFE");
        assertThat(response.getSummaryHeadline()).isNotBlank();
        assertThat(response.getSummaryMessage()).isNotBlank();

        // 위험 메뉴 리스트 검증
        List<DashboardRiskMenuDto> riskMenus = response.getRiskMenus();
        assertThat(riskMenus).isNotNull();
        for (DashboardRiskMenuDto riskMenu : riskMenus) {
            assertThat(riskMenu.getRiskLevel()).isIn("WARNING", "CAUTION");
            assertThat(riskMenu.getMenuName()).isNotBlank();
            assertThat(riskMenu.getTotalCost()).isGreaterThan(BigDecimal.ZERO);
            assertThat(riskMenu.getCostRatio()).isGreaterThan(BigDecimal.ZERO);
        }

        System.out.println(">> [주간 요약 테스트 성공] " + response.getSummaryHeadline());
        System.out.println(">> 메시지: " + response.getSummaryMessage());
        System.out.println(">> 위험 메뉴 건수 비중: " + response.getRiskMenuRatio() + "%, 비용 비중: " + response.getRiskCostRatio() + "%");
    }

    @Test
    @DisplayName("[DASH-005] 특정 일자(DAILY) 식단 내 가격 위험 메뉴 비중을 정상 산출한다")
    void testSummarizeMenuRiskRatio_Daily() {
        // Given: 2026-09-17 당일
        Long facilityId = 1L;
        LocalDate baseDate = LocalDate.of(2026, 9, 17);

        // When
        DashboardMenuRiskRatioResponse response = dashboardService.summarizeMenuRiskRatio(facilityId, baseDate, "DAILY");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStartDate()).isEqualTo(baseDate);
        assertThat(response.getEndDate()).isEqualTo(baseDate);
        assertThat(response.getPeriodType()).isEqualTo("DAILY");
        assertThat(response.getTotalMenuCount()).isGreaterThan(0);
        assertThat(response.getSummaryHeadline()).isNotBlank();

        System.out.println(">> [일간 요약 테스트 성공] " + response.getSummaryHeadline());
    }

    @Test
    @DisplayName("[DASH-005] 식단이 존재하지 않는 날짜 조회 시 안전하게 기본 빈 응답을 반환한다")
    void testSummarizeMenuRiskRatio_EmptyPlans() {
        // Given: 먼 미래 날짜
        Long facilityId = 9999L;
        LocalDate baseDate = LocalDate.of(2099, 1, 1);

        // When
        DashboardMenuRiskRatioResponse response = dashboardService.summarizeMenuRiskRatio(facilityId, baseDate, "WEEKLY");

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTotalMenuCount()).isEqualTo(0);
        assertThat(response.getRiskMenuRatio()).isEqualTo(BigDecimal.ZERO.setScale(1));
        assertThat(response.getTotalPlannedCost()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.getOverallRiskLevel()).isEqualTo("SAFE");
        assertThat(response.getRiskMenus()).isEmpty();
    }
}
