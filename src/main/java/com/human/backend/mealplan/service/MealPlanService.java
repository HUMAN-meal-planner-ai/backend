package com.human.backend.mealplan.service;

import com.human.backend.cost.dto.response.MenuCostResponse;
import com.human.backend.cost.repository.CostRepository;
import com.human.backend.cost.service.CostService;
import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.mealplan.dto.request.MealPlanItemRequest;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

        LocalDate weekStartDate = request.weekStartDate();
        LocalDate weekEndDate = weekStartDate.plusDays(6);
        mealPlanRepository.deleteWeeklyPlans(user.getFacility().getId(), weekStartDate, weekEndDate);

        int mealCount = request.mealCount() == null ? 1 : request.mealCount();

        // 날짜 및 끼니별로 그룹핑하여 여러 메뉴가 들어있는 끼니를 각각 insertPlan + insertPlanItem 여러 개 처리
        Map<String, List<MealPlanItemRequest>> groupedByDateAndType = request.meals().stream()
                .filter(item -> item.mealDate() != null && item.menuId() != null)
                .sorted(Comparator.comparing(MealPlanItemRequest::mealDate))
                .collect(Collectors.groupingBy(
                        item -> item.mealDate() + "::" + (item.mealType() == null ? "LUNCH" : item.mealType().toUpperCase()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<MealPlanResponse.MealResponse> meals = new ArrayList<>();
        for (List<MealPlanItemRequest> itemsForMeal : groupedByDateAndType.values()) {
            if (itemsForMeal.isEmpty()) continue;
            Set<Long> seenMenuIds = new HashSet<>();
            List<MealPlanItemRequest> uniqueItems = itemsForMeal.stream()
                    .filter(item -> seenMenuIds.add(item.menuId()))
                    .toList();
            if (uniqueItems.isEmpty()) continue;

            MealPlanItemRequest first = uniqueItems.get(0);
            String mealType = first.mealType() == null || first.mealType().isBlank() ? "LUNCH" : first.mealType().trim().toUpperCase();

            long planId = mealPlanRepository.insertPlan(
                    user.getFacility().getId(),
                    user.getId(),
                    first.mealDate(),
                    mealType,
                    mealCount);

            List<MealPlanResponse.MealMenuItemResponse> menuItems = new ArrayList<>();
            List<String> menuNames = new ArrayList<>();
            BigDecimal mealCostSum = BigDecimal.ZERO;

            for (int i = 0; i < uniqueItems.size(); i++) {
                MealPlanItemRequest item = uniqueItems.get(i);
                if (item.menuId() != null) {
                    mealPlanRepository.insertPlanItem(planId, item.menuId(), i + 1);

                    String menuName = null;
                    BigDecimal itemCost = BigDecimal.ZERO;
                    try {
                        MenuCostResponse cost = costService.calculateCurrentMenuCost(item.menuId(), mealCount, null);
                        menuName = cost.getMenuName();
                        if (cost.getCostPerPerson() != null) {
                            itemCost = cost.getCostPerPerson();
                        }
                    } catch (Exception ignored) {
                        menuName = costRepository.findMenuNameById(item.menuId()).orElse("메뉴-" + item.menuId());
                    }

                    menuItems.add(new MealPlanResponse.MealMenuItemResponse(item.menuId(), menuName));
                    menuNames.add(menuName);
                    mealCostSum = mealCostSum.add(itemCost);
                }
            }

            meals.add(new MealPlanResponse.MealResponse(
                    planId,
                    first.mealDate(),
                    mealType,
                    first.slot() != null ? first.slot() : MenuSlot.OTHER,
                    menuItems.isEmpty() ? null : menuItems.get(0).menuId(),
                    String.join(", ", menuNames),
                    mealCostSum,
                    menuItems
            ));
        }

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
                .map(row -> {
                    List<MealPlanResponse.MealMenuItemResponse> menuItems = new ArrayList<>();
                    if (row.menuNames() != null && !row.menuNames().isBlank()) {
                        String[] names = row.menuNames().split(",\\s*");
                        String[] ids = row.menuIds() != null ? row.menuIds().split(",") : new String[0];
                        for (int i = 0; i < names.length; i++) {
                            Long id = null;
                            if (i < ids.length && !ids[i].isBlank()) {
                                try {
                                    id = Long.parseLong(ids[i].trim());
                                } catch (NumberFormatException ignored) {}
                            }
                            menuItems.add(new MealPlanResponse.MealMenuItemResponse(id, names[i]));
                        }
                    }
                    Long firstMenuId = menuItems.isEmpty() ? null : menuItems.get(0).menuId();
                    return new MealPlanResponse.MealResponse(
                            row.planId(),
                            row.mealDate(),
                            row.mealType(),
                            MenuSlot.OTHER,
                            firstMenuId,
                            row.menuNames(),
                            row.costPerPerson(),
                            menuItems);
                })
                .toList();

        if (meals.isEmpty()) {
            return new MealPlanResponse(weekStartDate, 0, BigDecimal.ZERO, List.of());
        }

        BigDecimal totalCost = meals.stream()
                .map(MealPlanResponse.MealResponse::costPerPerson)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .multiply(BigDecimal.valueOf(meals.size()));

        return new MealPlanResponse(weekStartDate, meals.size(), totalCost, meals);
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
                planId, item.mealDate(), mealType, item.slot(), item.menuId(), cost.getMenuName(), cost.getCostPerPerson());
    }

    private AppUser findUser(UserPrincipal principal) {
        return appUserRepository.findById(principal.userId())
                .orElseThrow(() -> new IllegalStateException("로그인 사용자를 찾을 수 없습니다."));
    }

    /**
     * 특정 식단(planId)에 포함된 개별 메뉴(menuId)를 삭제합니다.
     */
    @Transactional
    public void deletePlanItem(long planId, long menuId, UserPrincipal principal) {
        AppUser user = findUser(principal);
        if (user.getFacility() == null) {
            throw new IllegalStateException("시설 정보가 등록된 사용자만 메뉴를 수정할 수 있습니다.");
        }
        
        // Repository의 개별 메뉴 삭제 쿼리 호출
        mealPlanRepository.deletePlanItem(planId, menuId);
    }

    
}
