package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 이번 주·다음 주 예상 비용과 월 잔여 예산 기준 예산 초과 위험 분석 응답 DTO
 * [응집도 향상]: 응답 객체 생성 및 위험도 판정 결과를 캡슐화
 */
@Getter
@Builder
@AllArgsConstructor
public class BudgetRiskResponse {

    // 1. 기본 시설 및 기준 정보
    private final Long facilityId;
    private final String facilityName;
    private final LocalDate baseDate;           // 분석 기준일자
    private final String budgetMonth;           // 대상 월 (예: "2026-09")

    // 2. 월 예산 및 누적 지출
    private final BigDecimal monthlyBudget;     // 월 총 배정 예산
    private final BigDecimal currentSpentCost;  // 현재까지의 누적 지출/집행 비용
    private final BigDecimal monthlyRemainingBudget; // 월 잔여 예산 (총예산 - 누적지출)

    // 3. 이번 주 / 다음 주 예상 비용
    private final BigDecimal thisWeekExpectedCost; // 이번 주 총 예상 비용
    private final BigDecimal nextWeekExpectedCost; // 다음 주 총 예상 비용
    private final BigDecimal twoWeeksTotalExpectedCost; // 향후 2주간 총 예상 비용 (이번주 + 다음주)

    // 4. 예산 진단 및 예측 결과
    private final BigDecimal projectedRemainingBudget; // 2주 비용 소요 후 잔여 예산
    private final BigDecimal exceededAmount;           // 예산 초과 예상 금액 (초과 없을 시 0)
    private final String riskLevel;                    // 위험 단계: SAFE(안전), CAUTION(주의), WARNING(경고)
    private final boolean isRisk;                      // 초과 위험 여부 (true/false)
    private final String warningMessage;               // 사용자/영양사용 안내 경고 메시지

    // 5. 세부 주차별/일자별 식단 비용 내역
    private final List<DailyPlanCostDetail> thisWeekDetails;
    private final List<DailyPlanCostDetail> nextWeekDetails;

    /**
     * 일자별 식단 예상 비용 세부 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class DailyPlanCostDetail {
        private final Long planId;
        private final LocalDate planDate;
        private final String mealType;                 // LUNCH, DINNER 등
        private final Integer mealCount;               // 식수 인원
        private final BigDecimal costPerPerson;        // 1인당 예상 단가
        private final BigDecimal totalDailyCost;       // 일자/끼니별 총비용 (mealCount * costPerPerson)
    }
}
