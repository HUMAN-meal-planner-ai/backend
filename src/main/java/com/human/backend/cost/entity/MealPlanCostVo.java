package com.human.backend.cost.entity;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 식단 계획별 예상 원가 및 식수 정보 VO
 * [응집도 향상]: 1식 총 예상 비용 계산 책임을 스스로 수행
 */
@Getter
public class MealPlanCostVo {

    private final Long planId;
    private final Long facilityId;
    private final LocalDate planDate;
    private final String mealType;                 // LUNCH, DINNER 등
    private final Integer mealCount;               // 식수 인원
    private final BigDecimal expectedCostPerPerson;// 1인당 예상 단가

    public MealPlanCostVo(Long planId, Long facilityId, LocalDate planDate, String mealType,
                          Integer mealCount, BigDecimal expectedCostPerPerson) {
        this.planId = planId;
        this.facilityId = facilityId;
        this.planDate = planDate;
        this.mealType = mealType;
        this.mealCount = mealCount != null ? mealCount : 0;
        this.expectedCostPerPerson = expectedCostPerPerson != null ? expectedCostPerPerson : BigDecimal.ZERO;
    }

    /**
     * 해당 끼니의 총 예상 비용 계산 (1인당 단가 * 식수 인원)
     *
     * @return 총 예상 비용 (원 단위 정수 반올림)
     */
    public BigDecimal calculateTotalCost() {
        return this.expectedCostPerPerson
                .multiply(BigDecimal.valueOf(this.mealCount))
                .setScale(0, RoundingMode.HALF_UP);
    }
}
