package com.human.backend.cost.repository;

import com.human.backend.cost.entity.FacilityBudgetVo;
import com.human.backend.cost.entity.MealPlanCostVo;
import com.human.backend.cost.entity.MenuIngredientCostVo;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * 원가 및 예산 분석에 필요한 데이터를 조회하는 Repository 인터페이스
 * [결합도 완화]: 인터페이스를 통해 역할(규격)을 정의함으로써, 
 * 향후 InMemory 더미 데이터에서 JPA/MyBatis/PostgreSQL 등으로 데이터 소스를 교체하더라도
 * Service 계층의 코드 변경이 발생하지 않도록 의존성을 역전(DIP)시킵니다.
 */
public interface CostRepository {

    /**
     * 등록된 모든 메뉴 ID 목록 조회 (메뉴별 일괄 계산용)
     */
    List<Long> findAllMenuIds();

    /**
     * 메뉴 ID로 메뉴명 조회
     * (아이디로 메뉴 찾기: Optional을 사용하여 값이 없을 경우에 대한 처리를 호출자에게 위임)
     */
    Optional<String> findMenuNameById(Long menuId);

    /**
     * 메뉴 ID에 해당하는 최신 식재료 구성 및 단가 정보 목록 조회
     * (데이터가 없으면 빈 리스트 반환)
     */
    List<MenuIngredientCostVo> findLatestIngredientsByMenuId(Long menuId);

    /**
     * 메뉴 ID 및 특정 미래 일자에 해당하는 예측 식재료 구성 및 단가 정보 목록 조회 (COST-002)
     * (예측 데이터가 없을 경우 최신 시세를 대체 적용하여 반환)
     */
    List<MenuIngredientCostVo> findPredictedIngredientsByMenuId(Long menuId, LocalDate targetDate);

    /**
     * 시설의 특정 연월 예산 정보 조회
     */
    Optional<FacilityBudgetVo> findFacilityBudget(Long facilityId, YearMonth month);

    /**
     * 시설의 특정 기간 내 식단 계획 및 예상 단가 목록 조회
     */
    List<MealPlanCostVo> findMealPlansByFacilityAndDateRange(Long facilityId, LocalDate startDate, LocalDate endDate);
}

