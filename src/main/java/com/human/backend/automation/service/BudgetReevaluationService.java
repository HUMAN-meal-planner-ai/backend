package com.human.backend.automation.service;

import com.human.backend.automation.dto.request.BudgetReevaluationRequest;
import com.human.backend.cost.dto.response.BudgetRiskResponse;
import com.human.backend.cost.service.BudgetAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * [AUTO-002] 예산 초과 위험 재평가 전담 서비스
 * 
 * [단일 책임 원칙(SRP)]:
 * 본 서비스는 알림 저장/발송과 분리되어, 오직 "이번 주·다음 주 예상 비용과 월 잔여 예산 기준 초과 위험 평가"
 * 계산 및 진단 결과 도출에만 집중합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetReevaluationService {

    private final BudgetAnalysisService budgetAnalysisService;

    /**
     * 특정 시설 및 기준일자에 대한 예산 초과 위험 재평가 수행
     * 
     * @param request 재평가 요청 DTO (시설 ID, 기준 일자 등)
     * @return 2주 예상비용 및 월 잔여 예산 기반 예산 위험 분석 결과
     */
    public BudgetRiskResponse evaluateRisk(BudgetReevaluationRequest request) {
        Long facilityId = (request != null) ? request.getFacilityId() : null;
        LocalDate baseDate = (request != null) ? request.getBaseDate() : null;

        log.info(">> [AUTO-002 예산 재평가 시작] 시설 ID: {}, 기준일자: {}", facilityId, baseDate);

        // BudgetAnalysisService를 활용하여 2주 예상 식단 비용과 월 잔여 예산 위험도 산출
        BudgetRiskResponse riskResponse = budgetAnalysisService.evaluateBudgetRisk(facilityId, baseDate);

        log.info(">> [AUTO-002 예산 재평가 완료] 시설 ID: {}, 위험여부: {}, 위험등급: {}, 초과예상액: {}원",
                riskResponse.getFacilityId(), riskResponse.isRisk(), riskResponse.getRiskLevel(),
                riskResponse.getExceededAmount());

        return riskResponse;
    }
}
