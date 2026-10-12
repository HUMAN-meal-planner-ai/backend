package com.human.backend.automation.service;

import com.human.backend.automation.dto.response.BudgetAlertResponse;
import com.human.backend.automation.entity.BudgetAlert;
import com.human.backend.automation.repository.BudgetAlertRepository;
import com.human.backend.cost.dto.response.BudgetRiskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * [AUTO-002] 예산 경고 알림 관리 전담 서비스 (Alert Management Service)
 * 
 * [단일 책임 원칙(SRP)]:
 * 본 서비스는 알림(Notification/Alert)의 생성, 저장, 조회, 읽음 처리 및 삭제 등
 * 알림 라이프사이클 관리 책임을 전담합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetAlertManagementService {

    private final BudgetAlertRepository alertRepository;

    /**
     * 예산 위험 분석 결과를 기반으로 경고 알림 생성 및 저장
     *
     * 로그인 때마다 재평가가 실행되므로, 같은 시설·같은 대상 월·같은 위험 등급의 미확인 알림이
     * 이미 있으면 새로 만들지 않고 그 알림의 금액과 메시지만 최신 값으로 갱신합니다.
     *
     * @param riskResponse 예산 초과 위험 분석 결과
     * @return 저장되었거나 갱신된 경고 알림 응답 DTO
     */
    public BudgetAlertResponse createAlert(BudgetRiskResponse riskResponse) {
        BudgetAlert alert = BudgetAlert.fromRisk(riskResponse);

        BudgetAlert existing = alertRepository.findUnreadByFacilityId(alert.getFacilityId()).stream()
                .filter(unread -> Objects.equals(unread.getAlertType(), alert.getAlertType())
                        && Objects.equals(unread.getBudgetMonth(), alert.getBudgetMonth())
                        && Objects.equals(unread.getRiskLevel(), alert.getRiskLevel()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            existing.setRisk(alert.isRisk());
            existing.setBaseDate(alert.getBaseDate());
            existing.setMonthlyBudget(alert.getMonthlyBudget());
            existing.setCurrentSpentCost(alert.getCurrentSpentCost());
            existing.setMonthlyRemainingBudget(alert.getMonthlyRemainingBudget());
            existing.setThisWeekExpectedCost(alert.getThisWeekExpectedCost());
            existing.setNextWeekExpectedCost(alert.getNextWeekExpectedCost());
            existing.setTwoWeeksTotalExpectedCost(alert.getTwoWeeksTotalExpectedCost());
            existing.setProjectedRemainingBudget(alert.getProjectedRemainingBudget());
            existing.setExceededAmount(alert.getExceededAmount());
            existing.setWarningMessage(alert.getWarningMessage());
            BudgetAlert refreshed = alertRepository.save(existing);
            log.info(">> [AUTO-002 기존 알림 갱신] 같은 위험의 미확인 알림이 있어 새로 만들지 않았습니다. 알림 ID: {}, 시설 ID: {}, 위험등급: {}",
                    refreshed.getAlertId(), refreshed.getFacilityId(), refreshed.getRiskLevel());
            return BudgetAlertResponse.from(refreshed);
        }

        BudgetAlert savedAlert = alertRepository.save(alert);
        log.info(">> [AUTO-002 경고 알림 발송 완료] 알림 ID: {}, 시설 ID: {}, 위험등급: {}, 초과금액: {}원",
                savedAlert.getAlertId(), savedAlert.getFacilityId(), savedAlert.getRiskLevel(),
                savedAlert.getExceededAmount());

        return BudgetAlertResponse.from(savedAlert);
    }

    /**
     * 전체 알림 목록 조회
     */
    public List<BudgetAlertResponse> getAllAlerts() {
        return alertRepository.findAll().stream()
                .map(BudgetAlertResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 시설의 알림 목록 조회
     */
    public List<BudgetAlertResponse> getAlertsByFacility(Long facilityId) {
        return alertRepository.findByFacilityId(facilityId).stream()
                .map(BudgetAlertResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 시설의 미확인(읽지 않은) 알림 목록 조회
     */
    public List<BudgetAlertResponse> getUnreadAlertsByFacility(Long facilityId) {
        return alertRepository.findUnreadByFacilityId(facilityId).stream()
                .map(BudgetAlertResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 위험 등급의 알림 목록 조회
     */
    public List<BudgetAlertResponse> getAlertsByRiskLevel(String riskLevel) {
        return alertRepository.findByRiskLevel(riskLevel).stream()
                .map(BudgetAlertResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 시설의 미확인 알림 건수 조회
     */
    public long countUnreadAlerts(Long facilityId) {
        return alertRepository.countUnreadByFacilityId(facilityId);
    }

    /**
     * 알림 단건 조회
     */
    public BudgetAlertResponse getAlertById(Long alertId) {
        return alertRepository.findById(alertId)
                .map(BudgetAlertResponse::from)
                .orElse(null);
    }

    /**
     * 알림 읽음 처리
     */
    public boolean markAsRead(Long alertId) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    alert.markAsRead();
                    alertRepository.save(alert);
                    log.info(">> [알림 읽음 처리 완료] 알림 ID: {}", alertId);
                    return true;
                })
                .orElse(false);
    }

    /**
     * 특정 시설의 모든 미확인 알림 일괄 읽음 처리
     */
    public int markAllAsReadByFacility(Long facilityId) {
        List<BudgetAlert> unreadAlerts = alertRepository.findUnreadByFacilityId(facilityId);
        for (BudgetAlert alert : unreadAlerts) {
            alert.markAsRead();
            alertRepository.save(alert);
        }
        log.info(">> [시설 전체 알림 일괄 읽음 처리 완료] 시설 ID: {}, 처리 건수: {}건", facilityId, unreadAlerts.size());
        return unreadAlerts.size();
    }

    /**
     * 알림 삭제
     */
    public boolean deleteAlert(Long alertId) {
        return alertRepository.deleteById(alertId);
    }
}
