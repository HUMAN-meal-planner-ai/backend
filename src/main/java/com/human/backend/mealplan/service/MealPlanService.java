package com.human.backend.mealplan.service;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.mealplan.dto.request.MealPlanItemRequest;
import com.human.backend.mealplan.dto.request.MealPlanReconfigureRequest;
import com.human.backend.mealplan.dto.request.MealPlanSaveRequest;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.menu.domain.MenuSlot;
import com.human.backend.mealplan.repository.MealPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MealPlanService {

    private final CostService costService;
    private final CostRepository costRepository;
    private final MealPlanRepository mealPlanRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public MealPlanResponse save(MealPlanSaveRequest request, UserPrincipal principal) {
        AppUser user = findUser(principal);
        if (user.getFacility() == null) {
            throw new IllegalStateException("식단을 저장하려면 먼저 시설을 등록해야 합니다.");
        }

        int mealCount = request.mealCount() == null ? 1 : request.mealCount();
        List<MealPlanResponse.MealResponse> meals = request.meals().stream()
                .sorted(Comparator.comparing(MealPlanItemRequest::mealDate))
                .map(item -> saveMeal(user, item, mealCount))
                .toList();

        BigDecimal totalCost = meals.stream()
                .map(MealPlanResponse.MealResponse::costPerPerson)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(mealCount));

        return new MealPlanResponse(request.weekStartDate(), mealCount, totalCost, meals);
    }

    @Transactional(readOnly = true)
    public MealPlanResponse findWeeklyPlan(LocalDate weekStartDate, UserPrincipal principal) {
        AppUser user = findUser(principal);
        if (user.getFacility() == null) {
            throw new IllegalStateException("주간 식단을 조회하려면 먼저 시설을 등록해야 합니다.");
        }

        return findWeeklyPlanForFacility(weekStartDate, user.getFacility().getId());
    }

    /** 자동화 기능처럼 시설 ID를 이미 알고 있는 호출자를 위한 조회입니다. */
    @Transactional(readOnly = true)
    public MealPlanResponse findWeeklyPlan(LocalDate weekStartDate, Long facilityId) {
        if (facilityId == null) {
            throw new IllegalArgumentException("시설 ID가 필요합니다.");
        }
        return findWeeklyPlanForFacility(weekStartDate, facilityId);
    }

    private MealPlanResponse findWeeklyPlanForFacility(LocalDate weekStartDate, long facilityId) {

        List<MealPlanResponse.MealResponse> meals = mealPlanRepository.findWeeklyPlans(
                        facilityId, weekStartDate, weekStartDate.plusDays(6))
                .stream()
                .map(row -> new MealPlanResponse.MealResponse(
                        row.mealDate(),
                        row.mealType(),
                        MenuSlot.OTHER,
                        null,
                        row.menuNames(),
                        row.costPerPerson()))
                .toList();

        if (meals.isEmpty()) {
            throw new IllegalArgumentException("저장된 주간 식단이 없습니다. weekStartDate=" + weekStartDate);
        }

        BigDecimal totalCost = meals.stream()
                .map(MealPlanResponse.MealResponse::costPerPerson)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(meals.size()));

        return new MealPlanResponse(weekStartDate, meals.size(), totalCost, meals);
    }

    public MealPlanResponse reconfigure(MealPlanReconfigureRequest request, UserPrincipal principal) {
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
                    "LUNCH",
                    slotOf(selectedMenuId),
                    selectedMenuId));
            previousMenuId = selectedMenuId;
        }

        return save(new MealPlanSaveRequest(request.weekStartDate(), mealCount, items), principal);
    }

    private Long selectMenu(List<Long> menuIds, Long previousMenuId, int mealCount, BigDecimal targetCost) {
        return menuIds.stream()
                .filter(menuId -> !menuId.equals(previousMenuId))
                .filter(menuId -> costService.calculateCurrentMenuCost(menuId, mealCount, targetCost)
                        .getCostPerPerson().compareTo(targetCost) <= 0)
                .findFirst()
                .orElse(menuIds.get(0));
    }

    private MealPlanResponse.MealResponse saveMeal(AppUser user, MealPlanItemRequest item, int mealCount) {
        MenuCostResponse cost = costService.calculateCurrentMenuCost(item.menuId(), mealCount, null);
        String mealType = item.mealType() == null || item.mealType().isBlank()
                ? "LUNCH"
                : item.mealType().trim().toUpperCase();
        long planId = mealPlanRepository.insertPlan(
                user.getFacility().getId(),
                user.getId(),
                item.mealDate(),
                mealType,
                mealCount);
        mealPlanRepository.insertPlanItem(planId, item.menuId(), 1);

        return new MealPlanResponse.MealResponse(
                item.mealDate(), mealType, item.slot(), item.menuId(), cost.getMenuName(), cost.getCostPerPerson());
    }

    private AppUser findUser(UserPrincipal principal) {
        return appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new IllegalStateException("로그인 사용자를 찾을 수 없습니다."));
    }

    private MenuSlot slotOf(Long menuId) {
        String menuName = costRepository.findMenuNameById(menuId).orElse("");
        String mainCategory = menuName.contains("국") || menuName.contains("찌개") ? "국" : "부찬";
        String subCategory = menuName.contains("김치") ? "김치" : "일반";
        return MenuSlot.from(mainCategory, subCategory);
    }
}
