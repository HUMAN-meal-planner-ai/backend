package com.human.backend.cost.controller;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.service.CostService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/cost")
@RequiredArgsConstructor
public class CostController {

    private final CostService costService;

    // 1. 메뉴별 1인분 기준 원가 전체 목록 조회
    // 예시 호출: GET /api/cost/menus?mealCount=50&targetCost=3000
    @GetMapping("/menus")
    public ResponseEntity<List<MenuCostResponse>> getAllMenuCosts(
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        List<MenuCostResponse> responses = costService.calculateAllMenuCosts(mealCount, targetCost);
        return ResponseEntity.ok(responses);
    }

    // 2. 메뉴 현재 원가 계산 결과 단건 조회 API
    // 예시 호출: GET /api/cost/menus/101?mealCount=50&targetCost=3000
    @GetMapping("/menus/{menuId}")
    public ResponseEntity<MenuCostResponse> getMenuCost(
            @PathVariable("menuId") Long menuId,
            @RequestParam(name = "mealCount", defaultValue = "1") Integer mealCount,
            @RequestParam(name = "targetCost", required = false) BigDecimal targetCost) {
        MenuCostResponse response = costService.calculateCurrentMenuCost(menuId, mealCount, targetCost);
        return ResponseEntity.ok(response);
    }
}
