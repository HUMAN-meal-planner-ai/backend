package com.human.backend.dashboard;

import com.human.backend.cost.repository.MemoryCostRepository;
import com.human.backend.cost.service.*;
import com.human.backend.dashboard.controller.DashboardController;
import com.human.backend.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MemoryCostRepository costRepository = new MemoryCostRepository();
        costRepository.init();

        MenuCostService menuCostService = new MenuCostService(costRepository);
        MenuRiskService menuRiskService = new MenuRiskService(menuCostService, costRepository);
        MealPlanCostService mealPlanCostService = new MealPlanCostService(costRepository);
        BudgetAnalysisService budgetAnalysisService = new BudgetAnalysisService(costRepository);

        CostService costService = new CostService(menuCostService, menuRiskService, mealPlanCostService, budgetAnalysisService);
        DashboardService dashboardService = new DashboardService(costRepository, costService);
        DashboardController dashboardController = new DashboardController(dashboardService);

        this.mockMvc = MockMvcBuilders.standaloneSetup(dashboardController).build();
    }

    @Test
    @DisplayName("[DASH-005] GET /api/dashboard/risk-summary 호출 시 200 OK와 올바른 JSON 구조를 반환한다")
    void testGetMenuRiskSummaryEndpoint() throws Exception {
        mockMvc.perform(get("/api/dashboard/risk-summary")
                        .param("facilityId", "1")
                        .param("baseDate", "2026-09-17")
                        .param("period", "WEEKLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityId").value(1))
                .andExpect(jsonPath("$.periodType").value("WEEKLY"))
                .andExpect(jsonPath("$.totalMenuCount").isNumber())
                .andExpect(jsonPath("$.riskMenuRatio").isNumber())
                .andExpect(jsonPath("$.riskCostRatio").isNumber())
                .andExpect(jsonPath("$.overallRiskLevel").isString())
                .andExpect(jsonPath("$.summaryHeadline").isString())
                .andExpect(jsonPath("$.summaryMessage").isString())
                .andExpect(jsonPath("$.riskMenus").isArray());
    }

    @Test
    @DisplayName("[DASH-005] Alias 경로 GET /api/dashboard/menu-risk-ratio 호출도 정상 작동한다")
    void testMenuRiskRatioAliasEndpoint() throws Exception {
        mockMvc.perform(get("/api/dashboard/menu-risk-ratio")
                        .param("facilityId", "1")
                        .param("baseDate", "2026-09-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facilityId").value(1))
                .andExpect(jsonPath("$.overallRiskLevel").isString());
    }
}
