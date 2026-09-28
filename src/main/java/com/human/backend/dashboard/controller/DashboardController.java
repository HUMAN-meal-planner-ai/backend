package com.human.backend.dashboard.controller;

import com.human.backend.dashboard.dto.response.DashboardMenuRiskRatioResponse;
import com.human.backend.dashboard.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * [대시보드 원가 및 가격 위험 현황 요약 REST Controller]
 *
 * ■ 담당 요구사항:
 *   - DASH-005: 현재 식단 내 가격 위험 메뉴 비중을 요약한다.
 *
 * ■ 전체 아키텍처 흐름 (Architecture Flow):
 *   [Client Request] ──> [DashboardController] ──> [DashboardService]
 *                                                         │
 *                                                         ├──> [CostRepository] (DB + CSV Mock)
 *                                                         │     - findMealPlansByFacilityAndDateRange()
 *                                                         │     - findMenuIdsByPlanId()
 *                                                         │     - findFacilityBudget()
 *                                                         │
 *                                                         └──> [CostService (Facade)]
 *                                                               ├── [MenuCostService] : 식재료 예측단가 기반 1인분 원가 산출
 *                                                               └── [MenuRiskService] : 메뉴 위험도(WARNING/CAUTION/SAFE) 진단
 *                                                                                       원가 상승 핵심 식재료(Cost Driver) 식별
 *
 * ■ 제공 API 엔드포인트 분류 및 요구사항 매핑:
 * 1. 식단 가격 위험 메뉴 비중 요약 (DASH-005)
 *    - GET /api/dashboard/risk-summary    : 현재 식단 내 가격 위험 메뉴 건수/비용 비중 및 상세 요약 조회
 *    - GET /api/dashboard/menu-risk-ratio : /api/dashboard/risk-summary 의 Alias 엔드포인트
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * [DASH-005] 현재 식단 내 가격 위험 메뉴 비중 요약 조회 API
     *
     * 예시 호출:
     *   - GET /api/dashboard/risk-summary?facilityId=1&baseDate=2026-09-17&period=WEEKLY
     *   - GET /api/dashboard/risk-summary?facilityId=1&baseDate=2026-09-17&period=DAILY
     *
     * @param facilityId 시설 ID (기본값: 1)
     * @param baseDate   분석 기준일자 (기본값: 서버 기준일자 2026-09-17)
     * @param period     분석 기간 단위 ("WEEKLY" 또는 "DAILY", 기본값: "WEEKLY")
     * @return 대시보드 가격 위험 메뉴 비중 요약 응답 DTO
     */
    @GetMapping(value = {"/risk-summary", "/menu-risk-ratio"})
    public ResponseEntity<DashboardMenuRiskRatioResponse> getMenuRiskSummary(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId,
            @RequestParam(name = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(name = "period", defaultValue = "WEEKLY") String period) {

        DashboardMenuRiskRatioResponse response = dashboardService.summarizeMenuRiskRatio(facilityId, baseDate, period);
        return ResponseEntity.ok(response);
    }
}
