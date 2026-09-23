package com.human.backend.mealplan.service;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.mealplan.dto.request.MealPlanItemRequest;
import com.human.backend.mealplan.dto.request.MealPlanReconfigureRequest;
import com.human.backend.mealplan.dto.request.MealPlanSaveRequest;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.menu.domain.MenuSlot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final CostService costService;
    private final CostRepository costRepository;
    private final Map<LocalDate, MealPlanResponse> savedPlans = new ConcurrentHashMap<>();

    public MealPlanResponse save(MealPlanSaveRequest request) {
        int mealCount = request.mealCount() == null ? 1 : request.mealCount();
        List<MealPlanResponse.MealResponse> meals = request.meals().stream()
                .sorted(Comparator.comparing(MealPlanItemRequest::mealDate))
                .map(item -> toMealResponse(item, mealCount))
                .toList();

        BigDecimal totalCost = meals.stream()
                .map(MealPlanResponse.MealResponse::costPerPerson)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(mealCount));

        MealPlanResponse response = new MealPlanResponse(
                request.weekStartDate(), mealCount, totalCost, meals);
        savedPlans.put(request.weekStartDate(), response);
        return response;
    }

    public MealPlanResponse findWeeklyPlan(LocalDate weekStartDate) {
        MealPlanResponse response = savedPlans.get(weekStartDate);
        if (response == null) {
            throw new IllegalArgumentException("저장된 주간 식단이 없습니다. weekStartDate=" + weekStartDate);
        }
        return response;
    }

    public MealPlanResponse reconfigure(MealPlanReconfigureRequest request) {
        int mealCount = request.mealCount() == null ? 1 : request.mealCount();
        List<Long> menuIds = costRepository.findAllMenuIds();
        if (menuIds.isEmpty()) {
            throw new IllegalStateException("추천할 메뉴가 없습니다.");
        }

        List<MealPlanItemRequest> items = new ArrayList<>();
        Long previousMenuId = null;
        for (int day = 0; day < 7; day++) {
            Long selectedMenuId = selectMenu(menuIds, previousMenuId, mealCount, request.targetCost());
            items.add(new MealPlanItemRequest(
                    request.weekStartDate().plusDays(day),
                    slotOf(selectedMenuId),
                    selectedMenuId));
            previousMenuId = selectedMenuId;
        }

        return save(new MealPlanSaveRequest(request.weekStartDate(), mealCount, items));
    }

    private Long selectMenu(List<Long> menuIds, Long previousMenuId, int mealCount, BigDecimal targetCost) {
        return menuIds.stream()
                .filter(menuId -> !menuId.equals(previousMenuId))
                .filter(menuId -> costService.calculateCurrentMenuCost(menuId, mealCount, targetCost)
                        .getCostPerPerson().compareTo(targetCost) <= 0)
                .findFirst()
                .orElse(menuIds.get(0));
    }

    private MealPlanResponse.MealResponse toMealResponse(MealPlanItemRequest item, int mealCount) {
        MenuCostResponse cost = costService.calculateCurrentMenuCost(item.menuId(), mealCount, null);
        return new MealPlanResponse.MealResponse(
                item.mealDate(), item.slot(), item.menuId(), cost.getMenuName(), cost.getCostPerPerson());
    }

    private MenuSlot slotOf(Long menuId) {
        String menuName = costRepository.findMenuNameById(menuId).orElse("");
        String mainCategory = menuName.contains("국") || menuName.contains("찌개") ? "국" : "부찬";
        String subCategory = menuName.contains("김치") ? "김치" : "일반";
        return MenuSlot.from(mainCategory, subCategory);
    }
}
