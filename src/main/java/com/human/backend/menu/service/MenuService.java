
package com.human.backend.menu.service;

import com.human.backend.menu.repository.MenuRepository;
import com.human.backend.menu.dto.response.MenuResponse;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MenuService {

    private final MenuRepository menuRepository;

    public MenuService(MenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<MenuResponse> getMenus() {
        return menuRepository.getMenus();
    }

    // public List<MenuByCode> getByCodes() {
    //     return  menuRepository.getMenuByCode();
    // }
}