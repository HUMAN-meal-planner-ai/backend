package com.human.backend.automation.service;

import com.human.backend.automation.dto.request.BudgetReevaluationRequest;
import com.human.backend.automation.dto.request.MenuReplacementCandidateRequest;
import com.human.backend.automation.dto.request.WeeklyPlanReverificationRequest;
import com.human.backend.automation.dto.response.BudgetAlertResponse;
import com.human.backend.automation.dto.response.BudgetReevaluationResultResponse;
import com.human.backend.automation.dto.response.MenuReplacementCandidateResponse;
import com.human.backend.automation.dto.response.WeeklyPlanReverificationResponse;
import com.human.backend.cost.dto.response.BudgetRiskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [AUTO-002, AUTO-004, AUTO-006] 예산 자동화·알림 통합 오케스트레이터 서비스 (Automation Facade)
 * 
 * [책임 분리 및 오케스트레이션]:
 *  1. [BudgetReevaluationService]         : 예산 초과 위험 재평가 및 수치 진단 (AUTO-002)
 *  2. [WeeklyPlanBudgetVerificationService]: 재평가 주간 식단 예산 위험 재확인 및 비교 (AUTO-004)
 *  3. [MenuCandidateDetectionService]     : 원가급등/가격위험/목표단가초과 변경 검토 메뉴 후보 탐지 (AUTO-006)
 *  4. [BudgetAlertManagementService]       : 경고 알림 생성, 조회, 읽음 상태 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAutomationService {

    private final BudgetReevaluationService reevaluationService;
    private final BudgetAlertManagementService alertManagementService;
    private final WeeklyPlanBudgetVerificationService weeklyPlanBudgetVerificationService;
    private final MenuCandidateDetectionService menuCandidateDetectionService;

    /**
     * [AUTO-006 핵심 로직] 원가 급등·가격 위험·목표단가 초과 메뉴를 변경 검토 후보로 탐지
     * 
     * @param request 후보 탐지 요청 DTO
     * @return 변경 검토 후보 메뉴 목록 응답 DTO
     */
    public MenuReplacementCandidateResponse detectReplacementCandidates(MenuReplacementCandidateRequest request) {
        return menuCandidateDetectionService.detectCandidates(request);
    }

    /**
     * [AUTO-004 핵심 로직] 재평가된 주간 식단의 예상 비용과 월 잔여 예산을 비교해 예산 위험 재확인
     * 
     * @param request 재평가 주간 식단 재확인 요청 DTO
     * @return 재평가 전/후 비교 및 예산 위험 재확인 결과 DTO
     */
    public WeeklyPlanReverificationResponse verifyWeeklyPlanBudget(WeeklyPlanReverificationRequest request) {
        WeeklyPlanReverificationResponse response = weeklyPlanBudgetVerificationService.verifyWeeklyPlanBudget(request);

        // 위험이 해소(isRiskResolved = true)되고 autoUpdateAlert가 true인 경우 시설의 미확인 알림 자동 확인 처리
        if (response.isRiskResolved() && Boolean.TRUE.equals(request.getAutoUpdateAlert())) {
            int updatedCount = alertManagementService.markAllAsReadByFacility(response.getFacilityId());
            if (updatedCount > 0) {
                log.info(">> [AUTO-004 예산 위험 해소 연동] 주간 식단 개선으로 시설 ID: {}의 기존 경고 알림 {}건을 자동 확인 처리했습니다.",
                        response.getFacilityId(), updatedCount);
            }
        }

        return response;
    }

    /**
     * [AUTO-002 핵심 로직] 이번 주·다음 주 예상 비용과 월 잔여 예산 기준 예산 초과 위험 재평가 및 경고 알림 자동 발행
     * 
     * @param request 재평가 요청 DTO
     * @return 재평가 실행 결과 및 생성된 알림 정보 DTO
     */
    public BudgetReevaluationResultResponse reevaluateAndAlert(BudgetReevaluationRequest request) {
        // 1. 재평가 전담 서비스를 통한 위험도 및 예상 비용 진단
        BudgetRiskResponse riskResult = reevaluationService.evaluateRisk(request);

        // 2. 경고 알림 생성 조건 확인 (autoSaveAlert 옵션 및 위험 발생 여부 / 경고 등급)
        boolean shouldCreateAlert = Boolean.TRUE.equals(request.getAutoSaveAlert()) 
                && (riskResult.isRisk() || !"SAFE".equalsIgnoreCase(riskResult.getRiskLevel()));

        Long createdAlertId = null;
        if (shouldCreateAlert) {
            BudgetAlertResponse savedAlert = alertManagementService.createAlert(riskResult);
            createdAlertId = savedAlert.getAlertId();
            log.info(">> [AUTO-002 경고 트리거] 예산 초과 위험 감지로 경고 알림이 자동 생성되었습니다. (알림 ID: {})", createdAlertId);
        } else {
            log.info(">> [AUTO-002 재평가 통과] 예산 초과 위험이 없거나 알림 자동 생성이 비활성화되었습니다.");
        }

        // 3. 종합 재평가 결과 DTO 조립
        return BudgetReevaluationResultResponse.builder()
                .facilityId(riskResult.getFacilityId())
                .facilityName(riskResult.getFacilityName())
                .evaluatedDate(riskResult.getBaseDate())
                .budgetMonth(riskResult.getBudgetMonth())
                .monthlyBudget(riskResult.getMonthlyBudget())
                .monthlyRemainingBudget(riskResult.getMonthlyRemainingBudget())
                .thisWeekExpectedCost(riskResult.getThisWeekExpectedCost())
                .nextWeekExpectedCost(riskResult.getNextWeekExpectedCost())
                .twoWeeksTotalExpectedCost(riskResult.getTwoWeeksTotalExpectedCost())
                .exceededAmount(riskResult.getExceededAmount())
                .riskLevel(riskResult.getRiskLevel())
                .isRisk(riskResult.isRisk())
                .alertCreated(shouldCreateAlert)
                .alertId(createdAlertId)
                .warningMessage(riskResult.getWarningMessage())
                .evaluatedAt(LocalDateTime.now())
                .riskDetails(riskResult)
                .build();
    }

    /**
     * 전체 경고 알림 목록 조회
     */
    public List<BudgetAlertResponse> getAllAlerts() {
        return alertManagementService.getAllAlerts();
    }

    /**
     * 시설별 경고 알림 목록 조회
     */
    public List<BudgetAlertResponse> getAlertsByFacility(Long facilityId) {
        return alertManagementService.getAlertsByFacility(facilityId);
    }

    /**
     * 시설별 미확인 알림 목록 조회
     */
    public List<BudgetAlertResponse> getUnreadAlertsByFacility(Long facilityId) {
        return alertManagementService.getUnreadAlertsByFacility(facilityId);
    }

    /**
     * 미확인 알림 건수 조회
     */
    public long countUnreadAlerts(Long facilityId) {
        return alertManagementService.countUnreadAlerts(facilityId);
    }

    /**
     * 알림 단건 조회
     */
    public BudgetAlertResponse getAlertById(Long alertId) {
        return alertManagementService.getAlertById(alertId);
    }

    /**
     * 알림 읽음 처리
     */
    public boolean markAsRead(Long alertId) {
        return alertManagementService.markAsRead(alertId);
    }

    /**
     * 시설의 모든 미확인 알림 일괄 읽음 처리
     */
    public int markAllAsReadByFacility(Long facilityId) {
        return alertManagementService.markAllAsReadByFacility(facilityId);
    }

    /**
     * 알림 삭제
     */
    public boolean deleteAlert(Long alertId) {
        return alertManagementService.deleteAlert(alertId);
    }
}
