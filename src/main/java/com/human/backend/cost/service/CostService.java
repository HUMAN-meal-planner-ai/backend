package com.human.backend.cost.service;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.global.exception.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CostService {

    // [결합도 완화]: 인터페이스인 CostRepository에 의존 (DIP 원칙 준수)
    private final CostRepository costRepository;

    // 시설 기본 1인 1식 목표 식재료비 기본값 (예: 2,500원)
    private static final BigDecimal DEFAULT_TARGET_COST = new BigDecimal("2500");

    /**
     * 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다.(단건, COST-001)
     */
    // 아까 생성한 response DTO를 반환하는 계산 메서드
    public MenuCostResponse calculateCurrentMenuCost(Long menuId, Integer mealCount, BigDecimal targetCost) {

        // 식수 인원 기본값 방어 (0 이하가 들어오면 기본 1명)
        int validMealCount = resolveMealCount(mealCount);
        // 목표 원가 기본값 방어 (null 또는 0 이하일 경우 시설 기본 목표 원가 적용)
        BigDecimal validTargetCost = resolveTargetCost(targetCost);

        // 메뉴를 가져오는데 ID에 해당하는 거 없으면 오류 반환
        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND", "존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        // 메뉴에 해당하는 식재료 정보 리스트를 가져오는데 없으면 오류 반환
        List<MenuIngredientCostVo> items = costRepository.findLatestIngredientsByMenuId(menuId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_INGREDIENT_NOT_FOUND", "해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        // [테스트 1/로그]: 어떤 메뉴를 계산하기 시작했는지 출력 (표준 Slf4j 로깅 적용)
        log.info("==================================================");
        log.info(">> [원가 계산 시작] 메뉴 ID: {} / 메뉴명: {}", menuId, menuName);
        log.info(">> 식재료 품목 수: {}개", items.size());
        log.info("--------------------------------------------------");

        BigDecimal costPerPerson = BigDecimal.ZERO; // 메뉴 1인분 원가를 담을 변수
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>(); // 식재료 정보 저장하는 리스트

        // 미리 가져온 식재료 정보리스트 순회
        for (MenuIngredientCostVo item : items) {
            // [응집도 향상]: 개별 재료의 원가 계산 및 DTO 변환을 DTO/VO에 위임
            MenuCostResponse.IngredientDetail detail = MenuCostResponse.IngredientDetail.from(item);
            BigDecimal lineCost = detail.getLineCost();

            costPerPerson = costPerPerson.add(lineCost);
            detailList.add(detail);

            // [테스트 2/로그]: 개별 식재료별 중량, 단가, 곱셈 결과 및 누적 합계 출력
            log.info("   - [{}] 사용량: {}g | 단가: {}원/g | 재료원가: {}원 (누적: {}원)",
                    item.getIngredientName(), item.getQuantity(), item.getStandardUnitPrice(), lineCost, costPerPerson);
        }

        // 2. [COST-004] 총 식수 원가 계산 = 1인분 원가 * 식수 인원
        BigDecimal totalMealCost = costPerPerson.multiply(BigDecimal.valueOf(validMealCount));

        // 테스트/확인용 로그 출력
        log.info(">> [총 원가 산출] {} | 1인분: {}원 * 식수: {}명 = 총 {}원",
                menuName, costPerPerson, validMealCount, totalMealCost);

        // 3. [COST-009] 목표 원가 초과 탐지 로직
        boolean isExceeded = costPerPerson.compareTo(validTargetCost) > 0;
        BigDecimal exceededAmount = isExceeded 
                ? costPerPerson.subtract(validTargetCost) 
                : BigDecimal.ZERO;

        log.info(">> [원가 검증] {} | 1인분: {}원 | 목표단가: {}원 | 초과여부: {} (초과액: {}원)",
                menuName, costPerPerson, validTargetCost, isExceeded, exceededAmount);

        // [응집도 향상]: 정적 팩토리 메서드를 통해 응답 DTO 생성
        return MenuCostResponse.of(menuId, menuName, costPerPerson, validMealCount, totalMealCost,
                validTargetCost, isExceeded, exceededAmount, detailList);
    }

    // 등록된 모든 메뉴별 1인분 기준 원가 일괄 산출(COST-003)
    public List<MenuCostResponse> calculateAllMenuCosts(Integer mealCount, BigDecimal targetCost) {
        List<Long> menuIds = costRepository.findAllMenuIds();
        List<MenuCostResponse> result = new ArrayList<>();

        for (Long menuId : menuIds) {
            result.add(calculateCurrentMenuCost(menuId, mealCount, targetCost));
        }

        return result;
    }

    /**
     * 식수 인원 유효성 검증 및 기본값 처리 (보조 메서드 분리로 단일 책임 강화)
     */
    private int resolveMealCount(Integer mealCount) {
        return (mealCount == null || mealCount <= 0) ? 1 : mealCount;
    }

    /**
     * 목표 단가 유효성 검증 및 기본값 처리 (보조 메서드 분리로 단일 책임 강화)
     */
    private BigDecimal resolveTargetCost(BigDecimal targetCost) {
        return (targetCost == null || targetCost.compareTo(BigDecimal.ZERO) <= 0) 
                ? DEFAULT_TARGET_COST : targetCost;
    }
}
