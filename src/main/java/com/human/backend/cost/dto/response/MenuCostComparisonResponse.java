package com.human.backend.cost.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 현재 메뉴 원가와 미래 예상 원가 비교 및 상승률 분석 응답 DTO (COST-002 비교 분석)
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuCostComparisonResponse {

    private final Long menuId;
    private final String menuName;
    private final LocalDate targetDate;             // 미래 예측 기준일

    // 1인분 기준 원가 비교
    private final BigDecimal currentCostPerPerson;  // 현재 1인분 원가
    private final BigDecimal futureCostPerPerson;   // 미래 1인분 예상 원가
    private final BigDecimal costDifference;        // 1인분 차액 (미래 - 현재)
    private final BigDecimal increaseRate;          // 1인분 가격 상승률 (%, 예: 12.50)
    private final boolean isIncreased;              // 가격 상승 여부 (true: 상승, false: 동일/하락)

    // 식수 인원 기준 총 원가 비교
    private final Integer mealCount;                // 적용 식수 인원
    private final BigDecimal currentTotalMealCost;  // 현재 총 식수 원가
    private final BigDecimal futureTotalMealCost;   // 미래 총 식수 원가
    private final BigDecimal totalCostDifference;   // 총 식수 원가 차액

    // 개별 식재료별 단가 및 원가 변동 세부 내역
    private final List<IngredientCostComparison> ingredientComparisons;

    /**
     * 식재료별 현재 단가 vs 미래 예측단가 변동 세부 DTO
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class IngredientCostComparison {
        private final Long ingredientId;
        private final String ingredientName;
        private final BigDecimal quantity;              // 1인 사용량 (g)
        private final BigDecimal currentUnitPrice;      // 현재 단가 (원/g)
        private final BigDecimal futureUnitPrice;       // 미래 예측 단가 (원/g)
        private final BigDecimal currentLineCost;       // 현재 재료 원가 (사용량 * 현재단가)
        private final BigDecimal futureLineCost;        // 미래 예상 재료 원가 (사용량 * 예측단가)
        private final BigDecimal lineCostDifference;    // 재료 원가 차액 (미래 - 현재)
        private final BigDecimal unitPriceIncreaseRate; // 단가 변동률 (%)
        private final LocalDate currentPriceDate;       // 현재 단가 기준일
        private final LocalDate futurePriceDate;        // 미래 예측 단가 기준일
    }
}
