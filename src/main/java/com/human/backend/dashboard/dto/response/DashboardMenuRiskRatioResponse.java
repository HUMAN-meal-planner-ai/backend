package com.human.backend.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * [DASH-005] 현재 식단 내 가격 위험 메뉴 비중 요약 응답 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardMenuRiskRatioResponse {

    // ==========================================
    // 1. 기본 분석 메타 정보
    // ==========================================
    /**
     * 시설 ID
     */
    private Long facilityId;

    /**
     * 시설명
     */
    private String facilityName;

    /**
     * 분석 기준 일자
     */
    private LocalDate baseDate;

    /**
     * 분석 기간 시작일
     */
    private LocalDate startDate;

    /**
     * 분석 기간 종료일
     */
    private LocalDate endDate;

    /**
     * 분석 기간 단위 (WEEKLY: 주간, DAILY: 일간)
     */
    private String periodType;

    // ==========================================
    // 2. 수량(건수) 기준 가격 위험 메뉴 비중 통계
    // ==========================================
    /**
     * 식단에 편성된 총 메뉴 슬롯 수 (중복 포함 총 제공 건수)
     */
    private Integer totalMenuCount;

    /**
     * 중복을 제외한 고유 메뉴 수
     */
    private Integer uniqueMenuCount;

    /**
     * 가격 위험(WARNING + CAUTION) 메뉴 총 건수
     */
    private Integer riskMenuCount;

    /**
     * 가격 위험 메뉴 건수 비중 (%) = (riskMenuCount / totalMenuCount) * 100
     */
    private BigDecimal riskMenuRatio;

    /**
     * 고유 가격 위험 메뉴 수
     */
    private Integer uniqueRiskMenuCount;

    /**
     * 고유 메뉴 기준 가격 위험 메뉴 비중 (%) = (uniqueRiskMenuCount / uniqueMenuCount) * 100
     */
    private BigDecimal uniqueRiskMenuRatio;

    /**
     * WARNING (경고/급등) 메뉴 건수
     */
    private Integer warningCount;

    /**
     * WARNING 메뉴 비중 (%)
     */
    private BigDecimal warningRatio;

    /**
     * CAUTION (주의/상승) 메뉴 건수
     */
    private Integer cautionCount;

    /**
     * CAUTION 메뉴 비중 (%)
     */
    private BigDecimal cautionRatio;

    /**
     * SAFE (안정) 메뉴 건수
     */
    private Integer safeCount;

    /**
     * SAFE 메뉴 비중 (%)
     */
    private BigDecimal safeRatio;

    // ==========================================
    // 3. 비용(식재료비) 기준 가격 위험 메뉴 비중 통계
    // ==========================================
    /**
     * 현재 식단 전체 총 예상 식재료비 (원)
     */
    private BigDecimal totalPlannedCost;

    /**
     * 가격 위험 메뉴(WARNING + CAUTION) 총 예상 식재료비 (원)
     */
    private BigDecimal riskMenuTotalCost;

    /**
     * 식재료비 기준 가격 위험 메뉴 비용 비중 (%) = (riskMenuTotalCost / totalPlannedCost) * 100
     */
    private BigDecimal riskCostRatio;

    /**
     * WARNING 메뉴 총 예상 비용 (원)
     */
    private BigDecimal warningTotalCost;

    /**
     * WARNING 메뉴 비용 비중 (%)
     */
    private BigDecimal warningCostRatio;

    /**
     * CAUTION 메뉴 총 예상 비용 (원)
     */
    private BigDecimal cautionTotalCost;

    /**
     * CAUTION 메뉴 비용 비중 (%)
     */
    private BigDecimal cautionCostRatio;

    /**
     * SAFE 메뉴 총 예상 비용 (원)
     */
    private BigDecimal safeTotalCost;

    /**
     * SAFE 메뉴 비용 비중 (%)
     */
    private BigDecimal safeCostRatio;

    // ==========================================
    // 4. 대시보드 종합 상태 및 브리핑 요약
    // ==========================================
    /**
     * 대시보드 종합 위험 상태 (CRITICAL: 심각, WARNING: 경고, CAUTION: 주의, SAFE: 안정)
     */
    private String overallRiskLevel;

    /**
     * 대시보드 헤드라인 타이틀 (한 줄 핵심 요약)
     */
    private String summaryHeadline;

    /**
     * 대시보드 종합 진단 및 권고 브리핑 메시지
     */
    private String summaryMessage;

    // ==========================================
    // 5. 가격 위험 메뉴 상세 목록 (Top 위험 메뉴들)
    // ==========================================
    /**
     * 가격 위험 메뉴 상세 리스트 (위험 등급 높은 순, 상승률 높은 순 정렬)
     */
    private List<DashboardRiskMenuDto> riskMenus;
}
