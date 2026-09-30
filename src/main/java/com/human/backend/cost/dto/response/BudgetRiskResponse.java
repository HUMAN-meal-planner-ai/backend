package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 1주간(이번 주) 및 2주간(이번 주+다음 주) 시뮬레이션 기반 예산 초과 위험 분석 응답 DTO
 * [응집도 향상]: 1주 / 2주 기간별 시뮬레이션 지표를 명확히 분리 및 제공
 */
@Getter
@Builder
@AllArgsConstructor
public class BudgetRiskResponse {

    // 1. 기본 시설 및 기준 정보
    private final Long facilityId;
    private final String facilityName;
    private final LocalDate baseDate;           // 분석 기준일자 (기본: 오늘)
    private final String budgetMonth;           // 대상 월 (예: "2026-09")

    // 2. 월 예산 및 누적 지출
    private final BigDecimal monthlyBudget;     // 월 총 배정 예산
    private final BigDecimal currentSpentCost;  // 현재까지의 누적 지출/집행 비용
    private final BigDecimal monthlyRemainingBudget; // 월 잔여 예산 (총예산 - 누적지출)

    // ==========================================
    // 3. [1주간 시뮬레이션] 이번 주 기준 지표
    // ==========================================
    private final BigDecimal thisWeekExpectedCost;         // 이번 주(1주) 총 예상 식단 비용 (하위 호환)
    private final BigDecimal oneWeekExpectedCost;          // 1주간 총 예상 식단 비용 (= thisWeekExpectedCost)
    private final BigDecimal oneWeekProjectedRemainingBudget; // 1주 비용 소요 후 잔여 예산
    private final BigDecimal oneWeekExceededAmount;        // 1주 예산 초과 예상 금액 (초과 없을 시 0)
    private final String oneWeekRiskLevel;                 // 1주 위험 단계: SAFE, CAUTION, WARNING
    private final boolean oneWeekIsRisk;                   // 1주 초과 위험 여부 (true/false)
    private final String oneWeekWarningMessage;            // 1주 시뮬레이션 안내 메시지

    // ==========================================
    // 4. [2주간 시뮬레이션] 2주(이번주+다음주) 누적 지표
    // ==========================================
    private final BigDecimal nextWeekExpectedCost;         // 다음 주 총 예상 비용
    private final BigDecimal twoWeeksTotalExpectedCost;    // 향후 2주간 총 예상 비용 (이번주 + 다음주)
    private final BigDecimal twoWeeksProjectedRemainingBudget; // 2주 비용 소요 후 잔여 예산
    private final BigDecimal twoWeeksExceededAmount;       // 2주 예산 초과 예상 금액
    private final String twoWeeksRiskLevel;                // 2주 위험 단계: SAFE, CAUTION, WARNING
    private final boolean twoWeeksIsRisk;                  // 2주 초과 위험 여부
    private final String twoWeeksWarningMessage;           // 2주 시뮬레이션 안내 메시지

    // ==========================================
    // 5. 하위 호환 종합 진단 필드 (2주 기준 기본 매핑)
    // ==========================================
    private final BigDecimal projectedRemainingBudget;     // 2주 소요 후 잔여 예산 (= twoWeeksProjectedRemainingBudget)
    private final BigDecimal exceededAmount;               // 예산 초과 예상 금액 (= twoWeeksExceededAmount)
    private final String riskLevel;                        // 종합 위험 단계 (= twoWeeksRiskLevel)
    private final boolean isRisk;                          // 종합 초과 위험 여부 (= twoWeeksIsRisk)
    private final String warningMessage;                   // 종합 안내 경고 메시지 (= twoWeeksWarningMessage)

    // 6. 세부 주차별/일자별 식단 비용 내역
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
        private final String menuNames;                // 해당 끼니에 편성된 메뉴명 (예: "누룽지(멥쌀), 콩나물국밥")
    }
}

