package com.human.backend.menu.service;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.menu.domain.MenuSlot;
import com.human.backend.menu.dto.response.MenuResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MenuService {

	private final CostRepository costRepository;

	public List<MenuResponse> findMenus(String slotText) { // slotText에는 RICE, SOUP, SIDE, KIMCHI, etc.가 들어올 수 있음
		MenuSlot requestedSlot = parseSlot(slotText);			

		return costRepository.findAllMenuIds().stream()
				.map(this::toResponse)
				.filter(menu -> requestedSlot == null || menu.getSlot() == requestedSlot)
				.toList();
	}

	private MenuResponse toResponse(Long menuId) {
		String menuName = costRepository.findMenuNameById(menuId)
				.orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메뉴 ID입니다. ID=" + menuId));

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

	private MenuResponse.IngredientResponse toIngredientResponse(MenuIngredientCostVo ingredient) {
		return new MenuResponse.IngredientResponse(
				ingredient.getIngredientId(), ingredient.getIngredientName(), ingredient.getQuantity(),
				ingredient.getStandardUnitPrice(), ingredient.getPriceDate());
	}

	private MenuSlot parseSlot(String slotText) {
		if (slotText == null || slotText.isBlank()) return null; // slotText가 null이거나 비어있으면 null을 반환
		try {
			return MenuSlot.valueOf(slotText.toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException("지원하지 않는 메뉴 슬롯입니다. slot=" + slotText);
		}
	}
}
