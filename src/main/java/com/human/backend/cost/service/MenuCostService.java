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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.human.backend.cost.util.CostCalculationUtils.*;

/**
 * 메뉴 1인분 및 식수 기준 원가 계산 전담 서비스
 * 담당 요구사항:
 * - MENU-008: 현재 및 예측 단가를 기준으로 메뉴의 1인분 예상 원가를 표시한다.
 * - COST-001: 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다.
 * - COST-002: 미래 식재료 예측가격과 사용 중량으로 메뉴 미래 원가를 계산한다.
 * - COST-003: 메뉴별 1인분 기준 원가를 산출한다.
 * - COST-004: 1인분 원가와 식수 인원을 곱하여 총 예상 원가를 계산한다.
 * - COST-009: 시설 또는 식단의 목표 원가를 초과하는 메뉴를 탐지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuCostService {

    private final CostRepository costRepository;

    /**
     * 최신 식재료 가격과 사용 중량으로 메뉴 현재 원가를 계산한다. (단건, MENU-008, COST-001, COST-004, COST-009)
     */
    public MenuCostResponse calculateCurrentMenuCost(Long menuId, Integer mealCount, BigDecimal targetCost) {
        int validMealCount = resolveMealCount(mealCount);
        BigDecimal validTargetCost = resolveTargetCost(targetCost);

        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND",
                        "존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        List<MenuIngredientCostVo> items = costRepository.findLatestIngredientsByMenuId(menuId);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_INGREDIENT_NOT_FOUND",
                    "해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        log.info("==================================================");
        log.info(">> [메뉴 현재 원가 계산] 메뉴: {} (ID: {}) | 식재료 품목: {}개", menuName, menuId, items.size());
        log.info("--------------------------------------------------");

        BigDecimal costPerPerson = BigDecimal.ZERO;
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>();

        for (MenuIngredientCostVo item : items) {
            MenuCostResponse.IngredientDetail detail = MenuCostResponse.IngredientDetail.from(item);
            BigDecimal lineCost = detail.getLineCost();

            costPerPerson = costPerPerson.add(lineCost);
            detailList.add(detail);

            log.info("   - [{}] 사용량: {}g | 단가: {}원/g | 재료원가: {}원 (누적: {}원)",
                    item.getIngredientName(), item.getQuantity(), item.getStandardUnitPrice(), lineCost, costPerPerson);
        }

        BigDecimal totalMealCost = costPerPerson.multiply(BigDecimal.valueOf(validMealCount));

        boolean isExceeded = costPerPerson.compareTo(validTargetCost) > 0;
        BigDecimal exceededAmount = isExceeded
                ? costPerPerson.subtract(validTargetCost)
                : BigDecimal.ZERO;

        log.info(">> [총 원가 산출] {} | 1인분: {}원 * 식수: {}명 = 총 {}원 (목표초과: {} / 초과액: {}원)",
                menuName, costPerPerson, validMealCount, totalMealCost, isExceeded, exceededAmount);

        return MenuCostResponse.of(menuId, menuName, costPerPerson, validMealCount, totalMealCost,
                validTargetCost, isExceeded, exceededAmount, detailList);
    }

    /**
     * 등록된 모든 메뉴별 1인분 기준 현재 원가 일괄 산출 (MENU-008, COST-003)
     */
    public List<MenuCostResponse> calculateAllMenuCosts(Integer mealCount, BigDecimal targetCost) {
        return costRepository.findAllMenuIds().stream()
                .map(menuId -> {
                    try {
                        return calculateCurrentMenuCost(menuId, mealCount, targetCost);
                    } catch (Exception e) {
                        log.warn(">> [메뉴 현재 원가 일괄 계산] 메뉴 ID={} 계산 생략: {}", menuId, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /**
     * 미래 식재료 예측가격과 사용 중량으로 메뉴 미래 원가를 계산한다. (단건, MENU-008, COST-002, COST-004, COST-009)
     */
    public MenuCostResponse calculateFutureMenuCost(Long menuId, LocalDate targetDate, Integer mealCount,
                                                    BigDecimal targetCost) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        int validMealCount = resolveMealCount(mealCount);
        BigDecimal validTargetCost = resolveTargetCost(targetCost);

        String menuName = costRepository.findMenuNameById(menuId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MENU_NOT_FOUND",
                        "존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        List<MenuIngredientCostVo> items = costRepository.findPredictedIngredientsByMenuId(menuId, validTargetDate);
        if (items.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MENU_INGREDIENT_NOT_FOUND",
                    "해당 메뉴에 등록된 식재료 구성 정보가 없습니다. ID=" + menuId);
        }

        log.info("==================================================");
        log.info(">> [메뉴 미래 원가 계산] 메뉴: {} (ID: {}) | 기준일: {}", menuName, menuId, validTargetDate);
        log.info(">> 식재료 품목 수: {}개", items.size());
        log.info("--------------------------------------------------");

        BigDecimal costPerPerson = BigDecimal.ZERO;
        List<MenuCostResponse.IngredientDetail> detailList = new ArrayList<>();

        for (MenuIngredientCostVo item : items) {
            MenuCostResponse.IngredientDetail detail = MenuCostResponse.IngredientDetail.from(item);
            BigDecimal lineCost = detail.getLineCost();

            costPerPerson = costPerPerson.add(lineCost);
            detailList.add(detail);

            log.info("   - [{}] 사용량: {}g | 예측단가: {}원/g (기준일: {}) | 재료원가: {}원 (누적: {}원)",
                    item.getIngredientName(), item.getQuantity(), item.getStandardUnitPrice(), item.getPriceDate(),
                    lineCost, costPerPerson);
        }

        BigDecimal totalMealCost = costPerPerson.multiply(BigDecimal.valueOf(validMealCount));

        boolean isExceeded = costPerPerson.compareTo(validTargetCost) > 0;
        BigDecimal exceededAmount = isExceeded
                ? costPerPerson.subtract(validTargetCost)
                : BigDecimal.ZERO;

        log.info(">> [미래 총 원가 산출] {} | 기준일: {} | 1인분: {}원 * 식수: {}명 = 총 {}원",
                menuName, validTargetDate, costPerPerson, validMealCount, totalMealCost);

        return MenuCostResponse.of(menuId, menuName, validTargetDate, costPerPerson, validMealCount, totalMealCost,
                validTargetCost, isExceeded, exceededAmount, detailList);
    }

    /**
     * 등록된 모든 메뉴별 특정 미래 일자 기준 원가 일괄 산출 (MENU-008, COST-002)
     */
    public List<MenuCostResponse> calculateAllFutureMenuCosts(LocalDate targetDate, Integer mealCount,
                                                              BigDecimal targetCost) {
        LocalDate validTargetDate = resolveTargetDate(targetDate);
        return costRepository.findAllMenuIds().stream()
                .map(menuId -> {
                    try {
                        return calculateFutureMenuCost(menuId, validTargetDate, mealCount, targetCost);
                    } catch (Exception e) {
                        log.warn(">> [메뉴 미래 원가 일괄 계산] 메뉴 ID={} 계산 생략: {}", menuId, e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
