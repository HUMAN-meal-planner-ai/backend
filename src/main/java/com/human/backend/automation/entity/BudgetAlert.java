package com.human.backend.automation.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * [AUTO-002] 예산 초과 위험 경고 및 재평가 알림 엔티티/VO
 * 
 * 이번 주·다음 주 예상 비용과 월 잔여 예산을 기준으로
 * 예산 초과 위험이 감지되었을 때 생성되는 경고 알림 정보를 보관합니다.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetAlert {

    private Long alertId;                   // 알림 고유 ID
    private Long facilityId;                // 대상 시설 ID
    private String facilityName;            // 대상 시설명
    private String alertType;               // 알림 유형 (예: "BUDGET_OVERRUN_RISK")
    private String riskLevel;               // 위험 등급 (WARNING: 경고, CAUTION: 주의, SAFE: 안전)
    private boolean isRisk;                 // 초과 위험 발생 여부

    private LocalDate baseDate;             // 분석 기준 일자
    private String budgetMonth;             // 대상 월 (예: "2026-09")

    private BigDecimal monthlyBudget;               // 월 배정 예산
    private BigDecimal currentSpentCost;            // 현재 누적 집행액
    private BigDecimal monthlyRemainingBudget;      // 월 잔여 예산 (배정예산 - 집행액)

    private BigDecimal thisWeekExpectedCost;        // 이번 주 예상 비용
    private BigDecimal nextWeekExpectedCost;        // 다음 주 예상 비용
    private BigDecimal twoWeeksTotalExpectedCost;   // 향후 2주간 총 예상 소요액 (이번주 + 다음주)

    private BigDecimal projectedRemainingBudget;    // 2주 소요 후 예상 잔여 예산
    private BigDecimal exceededAmount;              // 예산 초과 예상 금액

    private String warningMessage;                  // 사용자/영양사 대상 경고 메시지
    private boolean isRead;                         // 알림 확인/읽음 여부
    private LocalDateTime createdAt;                // 알림 생성 일시
    private LocalDateTime readAt;                   // 알림 확인 일시

    /**
     * 알림 읽음 처리
     */
    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
    }

    /**
     * [응집도 향상] 예산 위험 분석 결과(BudgetRiskResponse)로부터 BudgetAlert 엔티티를 생성하는 팩토리 메서드
     */
    public static BudgetAlert fromRisk(com.human.backend.cost.dto.response.BudgetRiskResponse riskResponse) {
        if (riskResponse == null) {
            throw new IllegalArgumentException("예산 위험 분석 결과는 null일 수 없습니다.");
        }
        return BudgetAlert.builder()
                .facilityId(riskResponse.getFacilityId())
                .facilityName(riskResponse.getFacilityName())
                .alertType("BUDGET_OVERRUN_RISK")
                .riskLevel(riskResponse.getRiskLevel())
                .isRisk(riskResponse.isRisk())
                .baseDate(riskResponse.getBaseDate())
                .budgetMonth(riskResponse.getBudgetMonth())
                .monthlyBudget(riskResponse.getMonthlyBudget())
                .currentSpentCost(riskResponse.getCurrentSpentCost())
                .monthlyRemainingBudget(riskResponse.getMonthlyRemainingBudget())
                .thisWeekExpectedCost(riskResponse.getThisWeekExpectedCost())
                .nextWeekExpectedCost(riskResponse.getNextWeekExpectedCost())
                .twoWeeksTotalExpectedCost(riskResponse.getTwoWeeksTotalExpectedCost())
                .projectedRemainingBudget(riskResponse.getProjectedRemainingBudget())
                .exceededAmount(riskResponse.getExceededAmount())
                .warningMessage(riskResponse.getWarningMessage())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
