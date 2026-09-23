package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 메뉴 원가 상승에 가장 크게 기여하는 식재료 분석 응답 DTO (Cost Driver Analysis)
 */
@Getter
@Builder
@AllArgsConstructor
public class CostDriverResponse {

    private final Long menuId;
    private final String menuName;
    private final LocalDate targetDate;              // 예측 기준일

    // 메뉴 원가 변동 요약
    private final BigDecimal currentCostPerPerson;   // 현재 1인분 원가
    private final BigDecimal futureCostPerPerson;    // 미래 1인분 예상 원가
    private final BigDecimal totalCostDifference;    // 메뉴 1인분 총 상승액 (차액)
    private final BigDecimal totalIncreaseRate;      // 메뉴 1인분 총 상승률 (%)

    // 원가 상승에 가장 크게 기여한 핵심 식재료 (Top 1 Driver)
    private final IngredientDriver topDriver;

    // 식재료별 원가 상승 기여도 랭킹 목록 (기여액 내림차순 정렬)
    private final List<IngredientDriver> rankedDrivers;

    /**
     * 식재료별 원가 상승 기여 상세 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class IngredientDriver {
        private final Integer rank;                  // 기여도 순위 (1위, 2위...)
        private final Long ingredientId;
        private final String ingredientName;
        private final BigDecimal quantity;           // 1인 사용량 (g)
        private final BigDecimal currentUnitPrice;   // 현재 단가 (원/g)
        private final BigDecimal futureUnitPrice;    // 미래 예측 단가 (원/g)
        private final BigDecimal unitPriceDifference;// 단가 변동액 (원/g)
        private final BigDecimal unitPriceIncreaseRate; // 단가 변동률 (%)
        private final BigDecimal currentLineCost;    // 현재 재료비 (원)
        private final BigDecimal futureLineCost;     // 미래 재료비 (원)
        private final BigDecimal lineCostDifference; // 1인분 재료비 증가액 (원)
        private final BigDecimal contributionRate;   // 메뉴 총 원가 상승에 대한 기여율 (%, 예: 65.4%)
        private final boolean isCostIncrease;        // 원가 상승 요인 여부 (증가액 > 0)
    }
}
