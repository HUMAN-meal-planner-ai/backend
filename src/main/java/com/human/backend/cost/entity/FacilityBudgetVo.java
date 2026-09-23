package com.human.backend.cost.entity;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * 시설별 월 예산 정보 VO
 */
@Getter
public class FacilityBudgetVo {

    private final Long facilityId;
    private final String facilityName;
    private final YearMonth budgetMonth;
    private final BigDecimal budgetAmount;

    public FacilityBudgetVo(Long facilityId, String facilityName, YearMonth budgetMonth, BigDecimal budgetAmount) {
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.budgetMonth = budgetMonth;
        this.budgetAmount = budgetAmount != null ? budgetAmount : BigDecimal.ZERO;
    }
}
