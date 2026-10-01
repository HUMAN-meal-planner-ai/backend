package com.human.backend.mealplan.controller;

import com.human.backend.mealplan.dto.request.MealPlanSaveRequest;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.mealplan.service.MealPlanService;
import com.human.backend.auth.service.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/meal-plans")
@RequiredArgsConstructor
public class MealPlanController {

    private final MealPlanService mealPlanService;

    @PostMapping
    public ResponseEntity<MealPlanResponse> save(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MealPlanSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mealPlanService.save(request, principal));
    }

    @GetMapping("/weekly")
    public MealPlanResponse findWeekly(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "weekStartDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return mealPlanService.findWeeklyPlan(weekStartDate, principal);
    }

    /* 특정 끼니(planId)에 포함된 개별 메뉴(menuId)를 삭제합니다. */
    @DeleteMapping("/{planId}/items/{menuId}")
    public ResponseEntity<Void> deletePlanItem(
            @PathVariable(name = "planId") long planId,
            @PathVariable(name = "menuId") long menuId,
            @AuthenticationPrincipal UserPrincipal principal) {
        
        mealPlanService.deletePlanItem(planId, menuId, principal);
        return ResponseEntity.ok().build();
    }
}
