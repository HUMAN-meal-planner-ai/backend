package com.human.backend.menu.dto.response;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class WeeklyMealResponse {
     private Long planId;          // 식단 ID (삭제를 위해 필수!)

    private LocalDate planDate;   // 식단 날짜

    private String mealType;      // 조식, 중식, 석식 등

    private List<MenuResponse> menuItems; // 메뉴 목록
}
