package com.human.backend.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.admin.service.AdminMenuIngredientService;

/** 관리자 전용 메뉴-식재료 연결 조회 API입니다. 인증과 ADMIN 권한은 /api/admin/** 규칙이 처리합니다. */
@RestController
@RequestMapping("/api/admin/menu-ingredients")
public class AdminMenuIngredientController {

    private final AdminMenuIngredientService service;

    public AdminMenuIngredientController(AdminMenuIngredientService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminMenuIngredientService.MenuSummary> searchMenus(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "30") int limit) {
        return service.searchMenus(keyword, limit);
    }

    @GetMapping("/{menuId}")
    public AdminMenuIngredientService.MenuIngredients getMenuIngredients(@PathVariable Long menuId) {
        return service.getMenuIngredients(menuId);
    }
}