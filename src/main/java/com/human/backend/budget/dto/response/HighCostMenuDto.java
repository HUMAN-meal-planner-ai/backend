package com.human.backend.budget.dto.response;

import com.human.backend.menu.domain.MenuSlot;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * [BUDG-003] 주간 식단 비용 기여도 높은 메뉴 및 변경 검토 후보 정보 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighCostMenuDto {

    // 1. 순위 및 기본 식별 정보
    private Integer rank;                          // 비용 기여도 순위 (1, 2, 3...)
    private Long menuId;                           // 메뉴 식별자
    private String menuName;                       // 메뉴명
    private MenuSlot slot;                         // 메뉴 슬롯 (MAIN, SOUP, SIDE 등)
    private String slotName;                       // 슬롯 한글명 (주찬, 국, 부찬 등)

    // 2. 주간 비용 기여도 분석 결과
    private BigDecimal averageCostPerPerson;       // 1인분 평균 예상 단가
    private Integer appearanceCount;               // 주간 식단 등장/제공 횟수
    private Integer totalMealCount;                // 주간 누적 식수 인원 합계
    private BigDecimal weeklyMenuCost;             // 주간 누적 총 발생 비용 (원)
    private BigDecimal contributionRate;           // 주간 총 식단비용 대비 기여율 (%, 예: 24.32%)
    private BigDecimal cumulativeContributionRate; // 상위 누적 기여율 (%)

    // 3. 제공 끼니별 상세 목록
    private List<ServedMealDetail> servedMeals;

    // 4. cost 폴더 연동 위험도 및 Cost Driver 분석
    private String riskLevel;                      // 메뉴 위험 등급 (WARNING, CAUTION, SAFE)
    private boolean isRisk;                        // 가격 급등 위험 노출 여부
    private BigDecimal increaseRate;               // 메뉴 원가 변동률 (%)
    private String topCostDriver;                  // 가격 상승 1위 핵심 식재료

    // 5. 주간 식단 재구성을 위한 변경 검토 가이드
    private String reviewPriority;                 // 변경 검토 우선순위 (URGENT, HIGH, MEDIUM, LOW)
    private String reviewReason;                   // 변경 검토 추천 사유
    private BigDecimal estimatedSavingsPotential;  // 대체 메뉴 교체 시 주간 예상 절감액 (원)
}
