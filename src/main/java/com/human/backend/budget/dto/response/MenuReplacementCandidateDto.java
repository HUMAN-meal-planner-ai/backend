package com.human.backend.budget.dto.response;

import com.human.backend.menu.domain.MenuSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * [BUDG-005] 대체 메뉴 후보별 단가 차이, 절감액 및 위험도 상세 정보 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class MenuReplacementCandidateDto {

    private final Long replacementMenuId;
    private final String replacementMenuName;
    private final MenuSlot slot;
    private final String slotName;

    // 1인분 기준
    private final BigDecimal replacementCostPerPerson;
    private final BigDecimal costDiffPerPerson; // original - replacement (양수: 절감, 음수: 원가 증가)
    private final BigDecimal savingsPerPerson;  // 1인분 절감액
    private final BigDecimal diffRate;          // 변동률 (%)

    // 식수 기준 총액
    private final Integer mealCount;
    private final BigDecimal replacementMealTotalCost; // 대체 메뉴의 식수 총액
    private final BigDecimal totalSavings;            // 식수 기준 총 절감액 (양수: 절감, 음수: 추가 소요)
    private final BigDecimal savingsRate;             // 끼니 전체 비용 대비 절감율 (%)
    private final BigDecimal afterMealTotalCost;      // 대체 적용 후 해당 끼니 전체 총비용

    // 상태: SAVINGS (절감), INCREASED (원가 상승), UNCHANGED (변동 없음)
    private final String savingsStatus;

    // 대체 메뉴의 식재료 가격 위험도 정보 (MenuRiskService 연동)
    private final String riskLevel;                   // SAFE, CAUTION, WARNING
    private final boolean isRisk;
    private final BigDecimal increaseRate;

    // 추천 및 변경 검토 가이드 문구
    private final String recommendationNote;
}
