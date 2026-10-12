package com.human.backend.menu.service;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.menu.domain.MenuSlot;
import com.human.backend.menu.dto.response.MenuResponse;
import com.human.backend.menu.repository.MenuRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MenuServiceTest {

    @Test
    void slotFilterUsesDatabaseSlotInsteadOfInferringFromMenuName() {
        CostRepository costRepository = mock(CostRepository.class);
        MenuRepository menuRepository = mock(MenuRepository.class);
        MenuService service = new MenuService(costRepository, menuRepository);

        MenuResponse soupNamedSide = menu(1L, "김치찌개", MenuSlot.SIDE);
        MenuResponse sideNamedSoup = menu(2L, "계란말이", MenuSlot.SOUP);
        when(menuRepository.getMenus()).thenReturn(List.of(soupNamedSide, sideNamedSoup));
        when(costRepository.findLatestIngredientsByMenuId(1L)).thenReturn(List.of());
        when(costRepository.findLatestIngredientsByMenuId(2L)).thenReturn(List.of(
                new MenuIngredientCostVo(20L, "계란", BigDecimal.valueOf(50),
                        BigDecimal.valueOf(3), LocalDate.of(2026, 10, 1))));

        List<MenuResponse> soups = service.findMenus("soup");

        assertThat(soups).extracting(MenuResponse::getMenuId).containsExactly(2L);
        assertThat(soups.get(0).getSlot()).isEqualTo(MenuSlot.SOUP);
        assertThat(soups.get(0).getIngredients()).hasSize(1);
    }

    private MenuResponse menu(Long id, String name, MenuSlot slot) {
        return MenuResponse.builder()
                .menuId(id)
                .menuCode("MENU-" + id)
                .menuName(name)
                .mainCategory("DB category")
                .subCategory("DB subcategory")
                .slot(slot)
                .foodCount(0)
                .ingredients(List.of())
                .build();
    }
}
