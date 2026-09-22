package com.human.backend.mealplan.controller;

import com.human.backend.mealplan.dto.request.MealPlanReconfigureRequest;
import com.human.backend.mealplan.dto.request.MealPlanSaveRequest;
import com.human.backend.mealplan.dto.response.MealPlanResponse;
import com.human.backend.mealplan.service.MealPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    public ResponseEntity<MealPlanResponse> save(@Valid @RequestBody MealPlanSaveRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mealPlanService.save(request));
    }

    @GetMapping("/weekly")
    public MealPlanResponse findWeekly(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStartDate) {
        return mealPlanService.findWeeklyPlan(weekStartDate);
    }

    @PostMapping("/reconfigure")
    public MealPlanResponse reconfigure(@Valid @RequestBody MealPlanReconfigureRequest request) {
        return mealPlanService.reconfigure(request);
    }
}
