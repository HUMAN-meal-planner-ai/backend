package com.human.backend.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * [DASH-005] 대시보드 표시용 가격 위험 메뉴 상세 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardRiskMenuDto {

    /**
     * 메뉴 ID
     */
    private Long menuId;

    /**
     * 메뉴명
     */
    private String menuName;

    /**
     * 위험 등급 (WARNING: 경고, CAUTION: 주의, SAFE: 안정)
     */
    private String riskLevel;

    /**
     * 위험도 종합 점수 (0 ~ 100)
     */
    private Integer riskScore;

    /**
     * 1인분 예상 단가 (원)
     */
    private BigDecimal costPerPerson;

    /**
     * 해당 식단 기간 동안의 총 제공 횟수
     */
    private Integer appearanceCount;

    /**
     * 해당 식단 기간 동안의 누적 식수 인원 (명)
     */
    private Integer totalMealCount;

    /**
     * 총 예상 식재료비 (원) = 1인분 단가 * 누적 식수
     */
    private BigDecimal totalCost;

    /**
     * 전체 식단 비용 대비 해당 메뉴의 비용 비중 (%)
     */
    private BigDecimal costRatio;

    /**
     * 원가 변동률 (상승률, %)
     */
    private BigDecimal increaseRate;

    /**
     * 원가 상승을 견인한 핵심 식재료 (Cost Driver 요약)
     * 예: "돼지고기 (상승기여율 45.2%)"
     */
    private String topCostDriver;

    /**
     * 위험 사유 요약
     */
    private String riskReason;
}
