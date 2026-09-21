package com.human.backend.menu.controller;

import com.human.backend.menu.dto.response.MenuResponse;
import com.human.backend.menu.service.MenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @GetMapping
    public List<MenuResponse> getMenus(
            @RequestParam(name = "slot", required = false) String slot) {
        return menuService.findMenus(slot);
    }
}
