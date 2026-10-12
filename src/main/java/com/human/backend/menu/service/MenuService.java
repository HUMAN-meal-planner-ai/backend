package com.human.backend.menu.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.menu.domain.MenuSlot;
import com.human.backend.menu.dto.response.MenuResponse;

import com.human.backend.menu.repository.MenuRepository;

@Service
public class MenuService {

    private final CostRepository costRepository;
    private final MenuRepository menuRepository;

    public MenuService(CostRepository costRepository, MenuRepository menuRepository) {
        this.costRepository = costRepository;
        this.menuRepository = menuRepository;
    }

    /**
     * 원가 저장소의 메뉴와 최신 식재료 정보를 화면용 응답으로 조립합니다.
     * slotText가 없으면 전체 목록을 반환하고 값이 있으면 MenuSlot이 일치하는 메뉴만 남깁니다.
     */
    public List<MenuResponse> findMenus(String slotText) {
        MenuSlot requestedSlot = parseSlot(slotText);

        return menuRepository.getMenus().stream()
                .filter(menu -> requestedSlot == null || menu.getSlot() == requestedSlot)
                .map(this::withLatestIngredients)
                .toList();
    }

    /** 실제 메뉴 분류를 유지하면서 최신 원가 식재료 정보를 응답에 포함합니다. */
    private MenuResponse withLatestIngredients(MenuResponse menu) {
        List<MenuResponse.IngredientResponse> ingredients = costRepository
                .findLatestIngredientsByMenuId(menu.getMenuId())
                .stream()
                .map(this::toIngredientResponse)
                .toList();

        return MenuResponse.builder()
                .menuId(menu.getMenuId())
                .menuCode(menu.getMenuCode())
                .menuName(menu.getMenuName())
                .mainCategory(menu.getMainCategory())
                .subCategory(menu.getSubCategory())
                .slot(menu.getSlot())
                .weight(menu.getWeight())
                .energyKcal(menu.getEnergyKcal())
                .proteinG(menu.getProteinG())
                .fatG(menu.getFatG())
                .carbohydrateG(menu.getCarbohydrateG())
                .sodiumMg(menu.getSodiumMg())
                .foodCount(menu.getFoodCount())
                .ingredients(ingredients)
                .build();
    }

    /** 원가 계산용 식재료 객체에서 메뉴 API에 공개할 필드만 골라 응답으로 변환합니다. */
    private MenuResponse.IngredientResponse toIngredientResponse(MenuIngredientCostVo ingredient) {
        return new MenuResponse.IngredientResponse(
                ingredient.getIngredientId(), ingredient.getIngredientName(), ingredient.getQuantity(),
                ingredient.getStandardUnitPrice(), ingredient.getPriceDate());
    }

    /**
     * 쿼리 문자열을 대소문자와 관계없이 MenuSlot enum으로 변환합니다.
     * 빈 값은 필터 없음으로 처리하고 지원하지 않는 값은 명확한 오류로 거부합니다.
     */
    private MenuSlot parseSlot(String slotText) {
        if (slotText == null || slotText.isBlank()) {
            return null;
        }
        try {
            return MenuSlot.valueOf(slotText.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 메뉴 슬롯입니다. slot=" + slotText);
        }
    }

    public List<MenuResponse> getMenus() {
        return menuRepository.getMenus();
    }
    public List<MenuResponse> searchMenus(String keyword) {
    if (keyword == null || keyword.isBlank()) {
        return menuRepository.getMenus();
    }

    return menuRepository.searchMenus(keyword.trim());
}

}
