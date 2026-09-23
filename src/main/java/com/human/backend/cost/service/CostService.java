package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.BudgetRiskResponse;
import com.human.backend.cost.dto.response.BudgetUsageRateResponse;
import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostComparisonResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.dto.response.MonthlyMealPlanCostResponse;
import com.human.backend.cost.dto.response.WeeklyMealPlanCostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 원가 및 예산 분석 통합 서비스 파사드 (Facade)
 * 도메인별로 분리된 하위 서비스(MenuCostService, MenuRiskService, MealPlanCostService, BudgetAnalysisService)에
 * 작업을 위임하여 단일 창구 역할을 제공합니다.
 */
@Service
@RequiredArgsConstructor
public class CostService {

    private final MenuCostService menuCostService;
    private final MenuRiskService menuRiskService;
    private final MealPlanCostService mealPlanCostService;
    private final BudgetAnalysisService budgetAnalysisService;

    // ==========================================
    // 1. 메뉴 원가 계산 (MenuCostService 위임)
    // ==========================================

    /**
     * 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가 계산 (MENU-008, COST-001, COST-004, COST-009)
     */
    public MenuCostResponse calculateCurrentMenuCost(Long menuId, Integer mealCount, BigDecimal targetCost) {
        return menuCostService.calculateCurrentMenuCost(menuId, mealCount, targetCost);
    }

    /**
     * 등록된 모든 메뉴별 1인분 기준 현재 원가 일괄 산출 (COST-003)
     */
    public List<MenuCostResponse> calculateAllMenuCosts(Integer mealCount, BigDecimal targetCost) {
        return menuCostService.calculateAllMenuCosts(mealCount, targetCost);
    }

    /**
     * 미래 식재료 예측가격과 사용 중량으로 메뉴 미래 원가 계산 (COST-002, COST-004, COST-009)
     */
    public MenuCostResponse calculateFutureMenuCost(Long menuId, LocalDate targetDate, Integer mealCount, BigDecimal targetCost) {
        return menuCostService.calculateFutureMenuCost(menuId, targetDate, mealCount, targetCost);
    }

    /**
     * 모든 메뉴 대상 특정 미래 일자 기준 원가 일괄 산출 (COST-002)
     */
    public List<MenuCostResponse> calculateAllFutureMenuCosts(LocalDate targetDate, Integer mealCount, BigDecimal targetCost) {
        return menuCostService.calculateAllFutureMenuCosts(targetDate, mealCount, targetCost);
    }

    // ==========================================
    // 2. 메뉴 원가 비교 및 위험도 분석 (MenuRiskService 위임)
    // ==========================================

    /**
     * 현재 메뉴 원가와 미래 예상 원가 비교 및 상승률 분석 (COST-005)
     */
    public MenuCostComparisonResponse compareMenuCost(Long menuId, LocalDate targetDate, Integer mealCount) {
        return menuRiskService.compareMenuCost(menuId, targetDate, mealCount);
    }

    /**
     * 모든 메뉴 대상 현재 원가 vs 미래 예상 원가 비교 일괄 목록 산출 (COST-005)
     */
    public List<MenuCostComparisonResponse> compareAllMenuCosts(LocalDate targetDate, Integer mealCount) {
        return menuRiskService.compareAllMenuCosts(targetDate, mealCount);
    }

    /**
     * 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 식별 (COST-006)
     */
    public CostDriverResponse identifyCostDrivers(Long menuId, LocalDate targetDate) {
        return menuRiskService.identifyCostDrivers(menuId, targetDate);
    }

    /**
     * 모든 메뉴 대상 원가 상승 기여 식재료 식별 일괄 목록 산출 (COST-006)
     */
    public List<CostDriverResponse> identifyAllCostDrivers(LocalDate targetDate) {
        return menuRiskService.identifyAllCostDrivers(targetDate);
    }

    /**
     * 메뉴 구성 주요 식재료의 가격 위험 종합 및 메뉴 위험도 산출 (MENU-009)
     */
    public MenuRiskResponse evaluateMenuRisk(Long menuId, LocalDate targetDate) {
        return menuRiskService.evaluateMenuRisk(menuId, targetDate);
    }

    /**
     * 모든 메뉴 대상 주요 식재료 가격 위험 종합 및 메뉴 위험도 일괄 산출 (MENU-009)
     */
    public List<MenuRiskResponse> evaluateAllMenuRisks(LocalDate targetDate) {
        return menuRiskService.evaluateAllMenuRisks(targetDate);
    }

    // ==========================================
    // 3. 식단 식재료비 계산 (MealPlanCostService 위임)
    // ==========================================

    /**
     * 선택 주차 7일 식단의 최신·예측 단가 기준 총 예상 식재료비 계산 (COST-012)
     */
    public WeeklyMealPlanCostResponse calculateWeeklyMealPlanCost(Long facilityId, LocalDate startDate) {
        return mealPlanCostService.calculateWeeklyMealPlanCost(facilityId, startDate);
    }

    /**
     * 주별 예상 비용을 합산하여 월간 총 예상 식재료비 계산 (COST-013)
     */
    public MonthlyMealPlanCostResponse calculateMonthlyMealPlanCost(Long facilityId, String yearMonthStr) {
        return mealPlanCostService.calculateMonthlyMealPlanCost(facilityId, yearMonthStr);
    }

    // ==========================================
    // 3. 예산 및 위험 분석 (BudgetAnalysisService 위임)
    // ==========================================

    /**
     * 이번 주·다음 주 예상 비용과 월 잔여 예산 기준 예산 초과 위험 분석 (BUDG-002)
     */
    public BudgetRiskResponse evaluateBudgetRisk(Long facilityId, LocalDate baseDate) {
        return budgetAnalysisService.evaluateBudgetRisk(facilityId, baseDate);
    }

    /**
     * 설정된 예산 대비 예상 사용액과 사용률 분석 (COST-014)
     */
    public BudgetUsageRateResponse evaluateBudgetUsage(Long facilityId, String yearMonthStr, LocalDate baseDate) {
        return budgetAnalysisService.evaluateBudgetUsage(facilityId, yearMonthStr, baseDate);
    }
}
