package com.human.backend.menu.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.menu.dto.response.MenuResponse;
import com.human.backend.menu.service.MenuService;

/** 메뉴 목록과 선택적인 식단 슬롯 필터를 제공하는 REST 컨트롤러입니다. */
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * GET /api/menus 또는 GET /api/menus?slot=SOUP 요청을 처리합니다.
     * slot을 생략하면 전체 메뉴를, 전달하면 해당 식단 위치의 메뉴만 반환합니다.
     */
    @GetMapping
    public List<MenuResponse> getMenus(
            @RequestParam(name = "slot", required = false) String slot) {
        return menuService.findMenus(slot);
    }
}
