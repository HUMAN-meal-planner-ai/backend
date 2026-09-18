
package com.human.backend.menu.controller;

import com.human.backend.menu.service.MenuService;
import com.human.backend.menu.dto.response.MenuResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/api/menus")
    public List<MenuResponse> getMenus() {
        return menuService.getMenus();
    }

    // @GetMapping("/api/menus/")
    // public List<MenuByCode> getMenuByCode() {
    //     return menuService.getMenuByCode();
    // }
    
}