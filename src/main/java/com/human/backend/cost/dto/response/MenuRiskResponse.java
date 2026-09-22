package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 메뉴 구성 주요 식재료의 가격 위험을 종합한 메뉴 위험도 응답 DTO
 * [MENU-009] 메뉴 구성 주요 식재료의 가격 위험을 종합해 메뉴 위험도를 표시한다.
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuRiskResponse {

    // 1. 기본 메뉴 및 기준 정보
    private final Long menuId;
    private final String menuName;
    private final LocalDate targetDate;              // 분석 기준 예측일자

    // 2. 1인분 원가 및 변동 요약
    private final BigDecimal currentCostPerPerson;   // 현재 1인분 원가
    private final BigDecimal futureCostPerPerson;    // 미래 예측 1인분 원가
    private final BigDecimal costDifference;        // 원가 변동액 (미래 - 현재)
    private final BigDecimal increaseRate;          // 메뉴 원가 변동률 (%)

    // 3. 종합 메뉴 위험도 판정 결과
    private final String riskLevel;                  // 종합 위험 등급: SAFE(안전), CAUTION(주의), WARNING(경고)
    private final boolean isRisk;                    // 가격 위험 노출 여부 (CAUTION 또는 WARNING 일 때 true)
    private final Integer riskScore;                 // 위험도 환산 점수 (0 ~ 100점)
    private final String riskSummary;                // 메뉴 종합 위험도 진단 요약 메시지

    // 4. 주요 식재료별 가격 위험 분석 세부 목록
    private final List<IngredientRiskDetail> riskIngredients;

    /**
     * 메뉴 구성 식재료별 개별 가격 위험 분석 세부 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class IngredientRiskDetail {
        private final Long ingredientId;
        private final String ingredientName;
        private final BigDecimal quantity;              // 1인 사용량 (g)
        private final BigDecimal currentUnitPrice;      // 현재 단가 (원/g)
        private final BigDecimal futureUnitPrice;       // 미래 예측 단가 (원/g)
        private final BigDecimal unitPriceIncreaseRate; // 식재료 단가 상승률 (%)
        private final BigDecimal lineCostDifference;    // 메뉴 내 해당 재료비 증가액 (원)
        private final BigDecimal contributionRate;      // 메뉴 총 원가 상승 기여율 (%)
        private final String ingredientRiskLevel;       // 식재료별 위험 단계: SAFE, CAUTION, WARNING
        private final String riskReason;                // 위험 요인 코멘트 (예: "단가 15.2% 급등 예상")
    }
}
