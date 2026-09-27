package com.human.backend.budget.controller;

import com.human.backend.budget.dto.request.WeeklyHighCostMenuRequest;
import com.human.backend.budget.dto.response.WeeklyHighCostMenuCandidateResponse;
import com.human.backend.budget.service.BudgetService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * [예산 관리 및 식단 비용 분석 REST Controller]
 *
 * ■ 전체 아키텍처 흐름 (Architecture Flow):
 *   [Client Request] ──> [BudgetController] ──> [BudgetService]
 *                                                      │
 *                                                      ├──> [CostRepository] (DB + CSV Mock)
 *                                                      │     - findMealPlansByFacilityAndDateRange()
 *                                                      │     - findMenuIdsByPlanId()
 *                                                      │     - findFacilityBudget()
 *                                                      │
 *                                                      └──> [CostService (Facade)]
 *                                                            ├── [MenuCostService] : 미래 예측 메뉴 원가 계산
 *                                                            └── [MenuRiskService] : 가격 변동 위험도 & Cost Driver
 *
 * ■ 제공 API 엔드포인트 분류 및 요구사항 매핑:
 * 1. 예산 및 식단 재구성 분석 (BUDG-003)
 *    - GET  /api/budget/high-cost-menus : 주간 식단 고비용 기여 메뉴 식별 및 변경 검토 후보 조회 (Query Param)
 *    - POST /api/budget/high-cost-menus : 주간 식단 고비용 기여 메뉴 식별 및 변경 검토 후보 조회 (JSON Body)
 *
 * 2. 대체 메뉴 적용 전후 비용 차이 및 절감액 분석 (BUDG-005)
 *    - GET  /api/budget/replacement-analysis : 대체 메뉴 적용 전후 비용 및 절감액 조회 (Query Param)
 *    - POST /api/budget/replacement-analysis : 대체 메뉴 적용 전후 비용 및 절감액 조회 (JSON Body)
 */
@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    // 1. 선택 주차 식단 비용 기여도 상위 메뉴 식별 및 식단 재구성 변경 검토 후보 조회 (BUDG-003)
    // 예시 호출: GET /api/budget/high-cost-menus?facilityId=1&startDate=2026-09-14&topN=5
    @GetMapping("/high-cost-menus")
    public ResponseEntity<WeeklyHighCostMenuCandidateResponse> getHighCostMenuCandidates(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "weekStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(name = "topN", defaultValue = "5") Integer topN,
            @RequestParam(name = "minContributionRate", required = false) BigDecimal minContributionRate) {

        LocalDate effectiveStartDate = (startDate != null) ? startDate : weekStartDate;

        WeeklyHighCostMenuRequest request = WeeklyHighCostMenuRequest.builder()
                .facilityId(facilityId)
                .weekStartDate(effectiveStartDate)
                .topN(topN)
                .minContributionRate(minContributionRate)
                .build();

        WeeklyHighCostMenuCandidateResponse response = budgetService.identifyHighCostMenuCandidates(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [BUDG-003] 주간 식단 비용 기여도 상위 메뉴 식별 및 식단 재구성 변경 검토 후보 조회 (POST)
     * 예시 호출:
     *   POST /api/budget/high-cost-menus
     *   Body: { "facilityId": 1, "weekStartDate": "2026-09-14", "topN": 5 }
     */
    @PostMapping("/high-cost-menus")
    public ResponseEntity<WeeklyHighCostMenuCandidateResponse> queryHighCostMenuCandidates(
            @RequestBody WeeklyHighCostMenuRequest request) {

        WeeklyHighCostMenuCandidateResponse response = budgetService.identifyHighCostMenuCandidates(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [BUDG-005] 대체 메뉴 적용 전후의 예상 비용 차이와 절감액 조회 (GET)
     * 예시 호출:
     *   GET /api/budget/replacement-analysis?originalMenuId=9&replacementMenuId=15&mealCount=100&targetDate=2026-09-17
     */
    @GetMapping("/replacement-analysis")
    public ResponseEntity<com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse> getReplacementAnalysis(
            @RequestParam(name = "originalMenuId") Long originalMenuId,
            @RequestParam(name = "replacementMenuId", required = false) Long replacementMenuId,
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "planId", required = false) Long planId,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", required = false) Integer mealCount,
            @RequestParam(name = "applyToAllOccurrences", defaultValue = "false") Boolean applyToAllOccurrences) {

        com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request =
                com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest.builder()
                        .facilityId(facilityId)
                        .originalMenuId(originalMenuId)
                        .replacementMenuId(replacementMenuId)
                        .planId(planId)
                        .targetDate(targetDate)
                        .mealCount(mealCount)
                        .applyToAllOccurrences(applyToAllOccurrences)
                        .build();

        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);
        return ResponseEntity.ok(response);
    }

    /**
     * [BUDG-005] 대체 메뉴 적용 전후의 예상 비용 차이와 절감액 종합 분석 (POST)
     * 예시 호출:
     *   POST /api/budget/replacement-analysis
     *   Body: { "facilityId": 1, "originalMenuId": 9, "replacementMenuId": 15, "mealCount": 100, "targetDate": "2026-09-17" }
     */
    @PostMapping("/replacement-analysis")
    public ResponseEntity<com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse> analyzeReplacement(
            @RequestBody com.human.backend.budget.dto.request.MenuReplacementAnalysisRequest request) {

        com.human.backend.budget.dto.response.MenuReplacementCostComparisonResponse response =
                budgetService.analyzeMenuReplacementCost(request);
        return ResponseEntity.ok(response);
    }
}
