package com.human.backend.cost.controller;

import com.human.backend.cost.dto.response.BudgetRiskResponse;
import com.human.backend.cost.dto.response.BudgetUsageRateResponse;
import com.human.backend.cost.dto.response.CostDriverResponse;
import com.human.backend.cost.dto.response.MenuCostComparisonResponse;
import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.dto.response.MenuRiskResponse;
import com.human.backend.cost.dto.response.MonthlyMealPlanCostResponse;
import com.human.backend.cost.dto.response.WeeklyMealPlanCostResponse;
import com.human.backend.cost.service.CostService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [원가 및 예산 분석 REST Controller]
 *
 * ■ 전체 아키텍처 흐름 (Architecture Flow):
 *   [Client Request] ──> [CostController] ──> [CostService (Facade)]
 *                                                   ├──> [MenuCostService]      : 메뉴 원가 계산
 *                                                   ├──> [MenuRiskService]      : 원가 비교 & 위험도 분석
 *                                                   ├──> [MealPlanCostService]  : 주간/월간 식단 비용
 *                                                   └──> [BudgetAnalysisService]: 예산 위험 & 사용률
 *                                                                 │
 *                                                                 ▼
 *                                                        [CostRepository] (DB + CSV Mock)
 *
 * ■ 제공 API 엔드포인트 분류 및 요구사항 매핑:
 * 1. 메뉴 원가 산출 (MENU-008, COST-001~004, COST-009)
 *    - GET /api/cost/menus                 : 전체 메뉴 현재 원가 일괄 조회
 *    - GET /api/cost/menus/{menuId}        : 특정 메뉴 현재 원가 단건 조회
 *    - GET /api/cost/menus/future          : 전체 메뉴 미래 예측 원가 일괄 조회
 *    - GET /api/cost/menus/{menuId}/future : 특정 메뉴 미래 예측 원가 단건 조회
 *
 * 2. 원가 변동 비교 및 위험 분석 (MENU-009, COST-005, COST-006)
 *    - GET /api/cost/menus/{menuId}/comparison : 현재 vs 미래 원가 비교 및 상승률
 *    - GET /api/cost/menus/comparison          : 전체 메뉴 원가 변동 비교 일괄 조회
 *    - GET /api/cost/menus/{menuId}/drivers    : 원가 상승 주요 기여 식재료(Cost Driver) 식별
 *    - GET /api/cost/menus/drivers             : 전체 메뉴 Cost Driver 일괄 조회
 *    - GET /api/cost/menus/{menuId}/risk       : 메뉴 가격 위험도(WARNING/CAUTION/SAFE) 종합 진단
 *    - GET /api/cost/menus/risk                : 전체 메뉴 위험도 일괄 조회
 *
 * 3. 식단 식재료비 및 예산 관리 (BUDG-002, COST-012~014)
 *    - GET /api/cost/weekly-plan-cost          : 주간 7일 식단 총 예상 식재료비 (COST-012)
 *    - GET /api/cost/monthly-plan-cost         : 월간 주차별 식재료비 & 예산 잔여액 (COST-013)
 *    - GET /api/cost/budget-risk               : 향후 2주간 예산 초과 위험 경고 분석 (BUDG-002)
 *    - GET /api/cost/budget-usage              : 월 배정 예산 대비 예상 사용액 & 사용률 (COST-014)
 */
@RestController
@RequestMapping("/api/cost")
public class CostController {

    private final CostService costService;

    public CostController(CostService costService) {
        this.costService = costService;
    }

    // 1. 메뉴별 1인분 기준 원가 전체 목록 조회 (MENU-008, COST-003)
    // 예시 호출: GET /api/cost/menus?mealCount=1&targetCost=3000
    @GetMapping("/menus")
    public ResponseEntity<List<MenuCostResponse>> getAllMenuCosts(
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        List<MenuCostResponse> responses = costService.calculateAllMenuCosts(mealCount, targetCost);
        return ResponseEntity.ok(responses);
    }

    // 2. 메뉴 현재 원가 계산 결과 단건 조회 API
    // 예시 호출: GET /api/cost/menus/101?mealCount=50&targetCost=3000
    @GetMapping("/menus/{menuId}")
    public ResponseEntity<MenuCostResponse> getMenuCost(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        MenuCostResponse response = costService.calculateCurrentMenuCost(menuId, mealCount, targetCost);
        return ResponseEntity.ok(response);
    }

    // 3. 미래 식재료 예측가격과 사용 중량 기반 메뉴 미래 원가 계산 단건 조회 API (COST-002)
    // 예시 호출: GET
    // /api/cost/menus/101/future?targetDate=2026-09-20&mealCount=50&targetCost=3000
    @GetMapping("/menus/{menuId}/future")
    public ResponseEntity<MenuCostResponse> getFutureMenuCost(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        MenuCostResponse response = costService.calculateFutureMenuCost(menuId, targetDate, mealCount, targetCost);
        return ResponseEntity.ok(response);
    }

    // 4. 모든 메뉴 대상 특정 미래 일자 기준 원가 일괄 조회 API
    // 예시 호출: GET
    // /api/cost/menus/future?targetDate=2026-09-20&mealCount=50&targetCost=3000
    @GetMapping("/menus/future")
    public ResponseEntity<List<MenuCostResponse>> getAllFutureMenuCosts(
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        List<MenuCostResponse> responses = costService.calculateAllFutureMenuCosts(targetDate, mealCount, targetCost);
        return ResponseEntity.ok(responses);
    }

    // 5. 메뉴 현재 원가 vs 미래 예상 원가 비교 및 상승률 분석 단건 조회 API
    // 예시 호출: GET /api/cost/menus/101/comparison?targetDate=2026-09-20&mealCount=50
    @GetMapping("/menus/{menuId}/comparison")
    public ResponseEntity<MenuCostComparisonResponse> getMenuCostComparison(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount) {
        MenuCostComparisonResponse response = costService.compareMenuCost(menuId, targetDate, mealCount);
        return ResponseEntity.ok(response);
    }

    // 6. 모든 메뉴 대상 현재 원가 vs 미래 예상 원가 비교 및 상승률 일괄 목록 조회 API
    // 예시 호출: GET /api/cost/menus/comparison?targetDate=2026-09-20&mealCount=50
    @GetMapping("/menus/comparison")
    public ResponseEntity<List<MenuCostComparisonResponse>> getAllMenuCostComparisons(
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount) {
        List<MenuCostComparisonResponse> responses = costService.compareAllMenuCosts(targetDate, mealCount);
        return ResponseEntity.ok(responses);
    }

    // 7. 메뉴 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 단건 조회 API
    // 예시 호출: GET /api/cost/menus/101/drivers?targetDate=2026-09-20
    @GetMapping("/menus/{menuId}/drivers")
    public ResponseEntity<CostDriverResponse> getMenuCostDrivers(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        CostDriverResponse response = costService.identifyCostDrivers(menuId, targetDate);
        return ResponseEntity.ok(response);
    }

    // 8. 전체 메뉴 대상 원가 상승에 가장 크게 기여하는 식재료(Cost Driver) 일괄 목록 조회 API
    // 예시 호출: GET /api/cost/menus/drivers?targetDate=2026-09-20
    @GetMapping("/menus/drivers")
    public ResponseEntity<List<CostDriverResponse>> getAllMenuCostDrivers(
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        List<CostDriverResponse> responses = costService.identifyAllCostDrivers(targetDate);
        return ResponseEntity.ok(responses);
    }

    // 9. 메뉴 구성 주요 식재료 가격 위험 종합 메뉴 위험도 단건 조회 API (MENU-009)
    // 예시 호출: GET /api/cost/menus/101/risk?targetDate=2026-09-20
    @GetMapping("/menus/{menuId}/risk")
    public ResponseEntity<MenuRiskResponse> getMenuRisk(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        MenuRiskResponse response = costService.evaluateMenuRisk(menuId, targetDate);
        return ResponseEntity.ok(response);
    }

    // 10. 전체 메뉴 대상 식재료 가격 위험 종합 메뉴 위험도 일괄 목록 조회 API (MENU-009)
    // 예시 호출: GET /api/cost/menus/risk?targetDate=2026-09-20
    @GetMapping("/menus/risk")
    public ResponseEntity<List<MenuRiskResponse>> getAllMenuRisks(
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        List<MenuRiskResponse> responses = costService.evaluateAllMenuRisks(targetDate);
        return ResponseEntity.ok(responses);
    }

    // 11. 이번 주·다음 주 예상 비용 및 월 잔여 예산 기준 초과 위험 분석 API
    // 예시 호출: GET /api/cost/budget-risk?facilityId=1&baseDate=2026-09-17
    @GetMapping("/budget-risk")
    public ResponseEntity<BudgetRiskResponse> getBudgetRisk(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        BudgetRiskResponse response = costService.evaluateBudgetRisk(facilityId, baseDate);
        return ResponseEntity.ok(response);
    }

    // 10. 선택 주차 7일 식단의 최신·예측 단가 기준 총 예상 식재료비 계산 API (COST-012)
    // 예시 호출: GET /api/cost/weekly-plan-cost?facilityId=1&startDate=2026-09-14
    @GetMapping("/weekly-plan-cost")
    public ResponseEntity<WeeklyMealPlanCostResponse> getWeeklyMealPlanCost(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        WeeklyMealPlanCostResponse response = costService.calculateWeeklyMealPlanCost(facilityId, startDate);
        return ResponseEntity.ok(response);
    }

    // 11. 주별 예상 비용을 합산한 월간 총 예상 식재료비 계산 API (COST-013)
    // 예시 호출: GET /api/cost/monthly-plan-cost?facilityId=1&yearMonth=2026-09
    @GetMapping("/monthly-plan-cost")
    public ResponseEntity<MonthlyMealPlanCostResponse> getMonthlyMealPlanCost(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "yearMonth", required = false) String yearMonth) {
        MonthlyMealPlanCostResponse response = costService.calculateMonthlyMealPlanCost(facilityId, yearMonth);
        return ResponseEntity.ok(response);
    }

    // 12. 설정된 예산 대비 예상 사용액과 사용률 분석 API (COST-014)
    // 예시 호출: GET /api/cost/budget-usage?facilityId=1&yearMonth=2026-09&baseDate=2026-09-17
    @GetMapping("/budget-usage")
    public ResponseEntity<BudgetUsageRateResponse> getBudgetUsage(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "yearMonth", required = false) String yearMonth,
            @RequestParam(name = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        BudgetUsageRateResponse response = costService.evaluateBudgetUsage(facilityId, yearMonth, baseDate);
        return ResponseEntity.ok(response);
    }
}

