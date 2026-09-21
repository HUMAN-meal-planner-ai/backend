package com.human.backend.cost.dto.response;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// 가격을 보내는 응답상자 
@Getter
@Builder
@AllArgsConstructor
public class MenuCostResponse {

    private final Long menuId;
    private final String menuName;
    private final BigDecimal costPerPerson; // 1인분 기준 현재 원가 (기존 totalCost에서 명칭 명확화)
    private final Integer mealCount;        // COST-004: 식수 인원
    private final BigDecimal totalMealCost; // COST-004: 1인분 원가 * 식수 인원 (총 예상 원가)
    
    // [COST-009] 목표 원가 초과 탐지 필드
    private final BigDecimal targetCost;    // 비교 기준 목표 단가
    private final boolean isExceeded;       // 초과 여부 (true/false)
    private final BigDecimal exceededAmount;// 초과된 금액 (0 이하면 0원)

    // [COST-002/COST-009] 기준 일자 (현재 또는 미래 예측 기준일자)
    private final LocalDate targetDate;     // 원가 산출 기준일

    private final List<IngredientDetail> details;

    /**
     * MenuCostResponse 생성을 위한 정적 팩토리 메서드 (기준일자 포함)
     * [응집도 향상]: 응답 객체 생성 책임을 DTO 내부로 캡슐화하여 서비스 코드의 가독성을 높입니다.
     */
    public static MenuCostResponse of(Long menuId, String menuName, LocalDate targetDate,
                                      BigDecimal costPerPerson, Integer mealCount, BigDecimal totalMealCost,
                                      BigDecimal targetCost, boolean isExceeded, BigDecimal exceededAmount,
                                      List<IngredientDetail> details) {
        return MenuCostResponse.builder()
                .menuId(menuId)
                .menuName(menuName)
                .targetDate(targetDate)
                .costPerPerson(costPerPerson)
                .mealCount(mealCount)
                .totalMealCost(totalMealCost)
                .targetCost(targetCost)
                .isExceeded(isExceeded)
                .exceededAmount(exceededAmount)
                .details(details)
                .build();
    }

    /**
     * 기존 호출 호환용 정적 팩토리 메서드 (기준일자 null 처리)
     */
    public static MenuCostResponse of(Long menuId, String menuName, BigDecimal costPerPerson,
                                      Integer mealCount, BigDecimal totalMealCost,
                                      BigDecimal targetCost, boolean isExceeded, BigDecimal exceededAmount,
                                      List<IngredientDetail> details) {
        return of(menuId, menuName, null, costPerPerson, mealCount, totalMealCost, targetCost, isExceeded, exceededAmount, details);
    }

    // static 정적 중첩 클래스(원활한 관리를 위해 식재료 정보를 DTO 내부에 작성)
    // 독립된 클래스로서 안전하게 json 변환이 가능
    @Getter
    @Builder
    @AllArgsConstructor
    public static class IngredientDetail {
        private final Long ingredientId;
        private final String ingredientName;
        private final BigDecimal quantity;          // 사용량 (g)
        private final BigDecimal standardUnitPrice; // 1g당 단가
        private final BigDecimal lineCost;          // quantity * standardUnitPrice
        private final LocalDate priceDate;          // 단가 기준일

        /**
         * MenuIngredientCostVo 엔티티로부터 DTO를 변환하는 정적 팩토리 메서드
         * [결합도 완화 및 응집도 향상]: VO에서 DTO로의 매핑과 개별 재료 원가 계산을 DTO 내부로 캡슐화합니다.
         */
        public static IngredientDetail from(MenuIngredientCostVo vo) {
            return IngredientDetail.builder()
                    .ingredientId(vo.getIngredientId())
                    .ingredientName(vo.getIngredientName())
                    .quantity(vo.getQuantity())
                    .standardUnitPrice(vo.getStandardUnitPrice())
                    .lineCost(vo.calculateLineCost())
                    .priceDate(vo.getPriceDate())
                    .build();
        }
    }
}
