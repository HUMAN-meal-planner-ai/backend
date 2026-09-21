package com.human.backend.menu.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.menu.domain.MenuSlot;
import com.human.backend.menu.dto.response.MenuResponse;

@Service
public class MenuService {

    private final CostRepository costRepository;

    public MenuService(CostRepository costRepository) {
        this.costRepository = costRepository;
    }

    /**
     * 원가 저장소의 메뉴와 최신 식재료 정보를 화면용 응답으로 조립합니다.
     * slotText가 없으면 전체 목록을 반환하고 값이 있으면 MenuSlot이 일치하는 메뉴만 남깁니다.
     */
    public List<MenuResponse> findMenus(String slotText) {
        MenuSlot requestedSlot = parseSlot(slotText);

        return costRepository.findAllMenuIds().stream()
            .map(this::toResponse)
            .filter(menu -> requestedSlot == null || menu.getSlot() == requestedSlot)
            .toList();
    }

    /** 메뉴 ID 하나를 메뉴 기본 정보와 식재료 상세가 포함된 응답 DTO로 변환합니다. */
    private MenuResponse toResponse(Long menuId) {
        String menuName = costRepository.findMenuNameById(menuId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴 ID입니다. ID=" + menuId));

        // 실제 카테고리 컬럼 연동 전까지 메뉴명으로 국·김치 여부를 임시 분류합니다.
        String mainCategory = menuName.contains("찌개") || menuName.contains("국") ? "국" : "부찬";
        String subCategory = menuName.contains("김치") ? "김치" : "일반";
        MenuSlot slot = MenuSlot.from(mainCategory, subCategory);

        List<MenuResponse.IngredientResponse> ingredients = costRepository
            .findLatestIngredientsByMenuId(menuId)
            .stream()
            .map(this::toIngredientResponse)
            .toList();

        return new MenuResponse(menuId, "MENU-" + menuId, menuName, mainCategory, subCategory,
            slot, null, ingredients.size(), ingredients);
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
}
