package com.human.backend.automation.controller;

import com.human.backend.automation.dto.request.BudgetReevaluationRequest;
import com.human.backend.automation.dto.request.MenuReplacementCandidateRequest;
import com.human.backend.automation.dto.request.WeeklyPlanReverificationRequest;
import com.human.backend.automation.dto.response.BudgetAlertResponse;
import com.human.backend.automation.dto.response.BudgetReevaluationResultResponse;
import com.human.backend.automation.dto.response.MenuReplacementCandidateResponse;
import com.human.backend.automation.dto.response.WeeklyPlanReverificationResponse;
import com.human.backend.automation.service.BudgetAutomationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * [AUTO-002, AUTO-004, AUTO-006] 예산 자동화·알림 REST Controller
 * 
 * 이번 주·다음 주 예상 비용과 월 잔여 예산을 기준으로 예산 초과 위험을 재평가(AUTO-002)하고,
 * 재평가된 주간 식단의 예산 위험을 재확인(AUTO-004)하며,
 * 원가 급등·가격 위험·목표단가 초과 메뉴를 변경 검토 후보로 탐지(AUTO-006)하는 REST API를 제공합니다.
 * 
 * ■ API 엔드포인트 목록:
 *  1. POST  /api/automation/budget/reevaluate              : 예산 초과 위험 재평가 실행 및 경고 알림 자동 발행 (AUTO-002)
 *  2. POST  /api/automation/budget/verify-weekly-plan      : 재평가된 주간 식단 예산 위험 재확인 및 비교 (AUTO-004)
 *  3. GET   /api/automation/budget/verify-weekly-plan      : 재평가된 주간 식단 예산 위험 재확인 간편 조회 (AUTO-004)
 *  4. POST  /api/automation/budget/replacement-candidates  : 변경 검토 메뉴 후보 탐지 API (AUTO-006)
 *  5. GET   /api/automation/budget/replacement-candidates  : 변경 검토 메뉴 후보 탐지 간편 조회 (AUTO-006)
 *  6. GET   /api/automation/budget/alerts                  : 경고 알림 목록 조회 (시설별, 미확인 전용 필터링 지원)
 *  7. GET   /api/automation/budget/alerts/{alertId}        : 특정 경고 알림 단건 상세 조회
 *  8. GET   /api/automation/budget/alerts/unread-count     : 미확인 경고 알림 건수 조회
 *  9. PATCH /api/automation/budget/alerts/{alertId}/read   : 특정 알림 읽음 처리
 *  10. PATCH /api/automation/budget/alerts/read-all         : 시설 전체 미확인 알림 일괄 읽음 처리
 *  11. DELETE /api/automation/budget/alerts/{alertId}      : 알림 삭제
 */
@Slf4j
@RestController
@RequestMapping("/api/automation/budget")
@RequiredArgsConstructor
public class BudgetAlertController {

    private final BudgetAutomationService budgetAutomationService;

    /**
     * 1. 예산 초과 위험 재평가 실행 및 경고 알림 자동 생성 API (AUTO-002)
     * 예시 호출: POST /api/automation/budget/reevaluate
     * Body: { "facilityId": 1, "baseDate": "2026-09-17", "autoSaveAlert": true }
     */
    @PostMapping("/reevaluate")
    public ResponseEntity<BudgetReevaluationResultResponse> reevaluateBudget(
            @RequestBody(required = false) BudgetReevaluationRequest request) {
        BudgetReevaluationRequest targetRequest = (request != null) ? request : new BudgetReevaluationRequest();
        BudgetReevaluationResultResponse response = budgetAutomationService.reevaluateAndAlert(targetRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * 1-1. GET 방식의 예산 초과 위험 재평가 간편 호출 API (AUTO-002)
     * 예시 호출: GET /api/automation/budget/reevaluate?facilityId=1&baseDate=2026-09-17&autoSaveAlert=true
     */
    @GetMapping("/reevaluate")
    public ResponseEntity<BudgetReevaluationResultResponse> reevaluateBudgetByGet(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(name = "autoSaveAlert", defaultValue = "true") Boolean autoSaveAlert) {
        BudgetReevaluationRequest request = BudgetReevaluationRequest.builder()
                .facilityId(facilityId)
                .baseDate(baseDate)
                .autoSaveAlert(autoSaveAlert)
                .build();
        BudgetReevaluationResultResponse response = budgetAutomationService.reevaluateAndAlert(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 1-2. 재평가된 주간 식단의 예상 비용과 월 잔여 예산 비교 및 예산 위험 재확인 API (AUTO-004)
     * 예시 호출: POST /api/automation/budget/verify-weekly-plan
     * Body: { "facilityId": 1, "weekStartDate": "2026-09-14", "mealCount": 50, "autoUpdateAlert": true }
     */
    @PostMapping("/verify-weekly-plan")
    public ResponseEntity<WeeklyPlanReverificationResponse> verifyWeeklyPlanBudget(
            @RequestBody(required = false) WeeklyPlanReverificationRequest request) {
        WeeklyPlanReverificationRequest targetRequest = (request != null) ? request : new WeeklyPlanReverificationRequest();
        WeeklyPlanReverificationResponse response = budgetAutomationService.verifyWeeklyPlanBudget(targetRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * 1-3. 재평가된 주간 식단의 예산 위험 재확인 GET 간편 호출 API (AUTO-004)
     * 예시 호출: GET /api/automation/budget/verify-weekly-plan?facilityId=1&weekStartDate=2026-09-14&mealCount=50
     */
    @GetMapping("/verify-weekly-plan")
    public ResponseEntity<WeeklyPlanReverificationResponse> verifyWeeklyPlanBudgetByGet(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "weekStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(name = "baseDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "autoUpdateAlert", defaultValue = "true") Boolean autoUpdateAlert) {
        WeeklyPlanReverificationRequest request = WeeklyPlanReverificationRequest.builder()
                .facilityId(facilityId)
                .weekStartDate(weekStartDate)
                .baseDate(baseDate)
                .mealCount(mealCount)
                .autoUpdateAlert(autoUpdateAlert)
                .build();
        WeeklyPlanReverificationResponse response = budgetAutomationService.verifyWeeklyPlanBudget(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 1-4. 주간 재평가 원가 급등·가격 위험·목표단가 초과 변경 검토 메뉴 후보 탐지 API (AUTO-006)
     * 예시 호출: POST /api/automation/budget/replacement-candidates
     * Body: { "facilityId": 1, "weekStartDate": "2026-09-14", "targetCost": 2500, "surgeThresholdRate": 10.0 }
     */
    @PostMapping("/replacement-candidates")
    public ResponseEntity<MenuReplacementCandidateResponse> getReplacementCandidates(
            @RequestBody(required = false) MenuReplacementCandidateRequest request) {
        MenuReplacementCandidateRequest targetRequest = (request != null) ? request : new MenuReplacementCandidateRequest();
        MenuReplacementCandidateResponse response = budgetAutomationService.detectReplacementCandidates(targetRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * 1-5. 변경 검토 메뉴 후보 탐지 GET 간편 호출 API (AUTO-006)
     * 예시 호출: GET /api/automation/budget/replacement-candidates?facilityId=1&weekStartDate=2026-09-14&targetCost=2500
     */
    @GetMapping("/replacement-candidates")
    public ResponseEntity<MenuReplacementCandidateResponse> getReplacementCandidatesByGet(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "weekStartDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate,
            @RequestParam(name = "targetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost,
            @RequestParam(name = "surgeThresholdRate", defaultValue = "10.0") BigDecimal surgeThresholdRate) {
        MenuReplacementCandidateRequest request = MenuReplacementCandidateRequest.builder()
                .facilityId(facilityId)
                .weekStartDate(weekStartDate)
                .targetDate(targetDate)
                .mealCount(mealCount)
                .targetCost(targetCost)
                .surgeThresholdRate(surgeThresholdRate)
                .build();
        MenuReplacementCandidateResponse response = budgetAutomationService.detectReplacementCandidates(request);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. 경고 알림 목록 조회 API
     * 예시 호출: GET /api/automation/budget/alerts?facilityId=1&unreadOnly=true
     */
    @GetMapping("/alerts")
    public ResponseEntity<List<BudgetAlertResponse>> getAlerts(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly) {
        List<BudgetAlertResponse> responses;
        if (facilityId != null) {
            responses = unreadOnly 
                    ? budgetAutomationService.getUnreadAlertsByFacility(facilityId) 
                    : budgetAutomationService.getAlertsByFacility(facilityId);
        } else {
            responses = budgetAutomationService.getAllAlerts();
        }
        return ResponseEntity.ok(responses);
    }

    /**
     * 3. 특정 경고 알림 단건 조회 API
     * 예시 호출: GET /api/automation/budget/alerts/1
     */
    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<BudgetAlertResponse> getAlertById(@PathVariable("alertId") Long alertId) {
        BudgetAlertResponse response = budgetAutomationService.getAlertById(alertId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 4. 미확인 알림 건수 조회 API
     * 예시 호출: GET /api/automation/budget/alerts/unread-count?facilityId=1
     */
    @GetMapping("/alerts/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId) {
        long count = budgetAutomationService.countUnreadAlerts(facilityId);
        return ResponseEntity.ok(Map.of(
                "facilityId", facilityId,
                "unreadCount", count
        ));
    }

    /**
     * 5. 특정 알림 읽음(확인) 처리 API
     * 예시 호출: PATCH /api/automation/budget/alerts/1/read
     */
    @PatchMapping("/alerts/{alertId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable("alertId") Long alertId) {
        boolean updated = budgetAutomationService.markAsRead(alertId);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "alertId", alertId,
                "isRead", true,
                "message", "알림이 성공적으로 확인 처리되었습니다."
        ));
    }

    /**
     * 6. 시설 전체 미확인 알림 일괄 읽음 처리 API
     * 예시 호출: PATCH /api/automation/budget/alerts/read-all?facilityId=1
     */
    @PatchMapping("/alerts/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead(
            @RequestParam(name = "facilityId", defaultValue = "1") Long facilityId) {
        int processedCount = budgetAutomationService.markAllAsReadByFacility(facilityId);
        return ResponseEntity.ok(Map.of(
                "facilityId", facilityId,
                "processedCount", processedCount,
                "message", String.format("총 %d건의 알림이 일괄 확인 처리되었습니다.", processedCount)
        ));
    }

    /**
     * 7. 특정 알림 삭제 API
     * 예시 호출: DELETE /api/automation/budget/alerts/1
     */
    @DeleteMapping("/alerts/{alertId}")
    public ResponseEntity<Map<String, Object>> deleteAlert(@PathVariable("alertId") Long alertId) {
        boolean deleted = budgetAutomationService.deleteAlert(alertId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "alertId", alertId,
                "deleted", true,
                "message", "알림이 삭제되었습니다."
        ));
    }
}
