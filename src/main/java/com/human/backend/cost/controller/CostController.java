package com.human.backend.cost.controller;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.service.CostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cost")
@CrossOrigin(origins = "*") // React 로컬 포트(예: 3000, 5173 등)의 CORS 차단 방지
public class CostController {

    private final CostService costService;

    public CostController(CostService costService) {
        this.costService = costService;
    }

    /**
     * 메뉴 현재 원가 계산 결과 조회 API
     * 예시 호출: GET /api/cost/menus/101
     */
    @GetMapping("/menus/{menuId}")
    public ResponseEntity<MenuCostResponse> getMenuCost(@PathVariable("menuId") Long menuId) {
        MenuCostResponse response = costService.calculateCurrentMenuCost(menuId);
        return ResponseEntity.ok(response);
    }
}
