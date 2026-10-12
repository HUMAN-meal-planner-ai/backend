package com.human.backend.mealplan.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MealPlanRepositoryIntegrationTest {

    private static final long FACILITY_ID = 987_654L;
    private static final long USER_ID = 987_655L;
    private static final long ORIGINAL_MENU_ID = 987_656L;
    private static final long ADDED_MENU_ID = 987_657L;
    private static final long REPLACEMENT_MENU_ID = 987_658L;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 10, 5);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MealPlanRepository repository;

    @BeforeEach
    void cleanTestRows() {
        repository.deleteWeeklyPlans(FACILITY_ID, WEEK_START, WEEK_START.plusDays(6));
        jdbcTemplate.update("DELETE FROM mealfit.menu WHERE menu_id IN (?, ?, ?)",
                ORIGINAL_MENU_ID, ADDED_MENU_ID, REPLACEMENT_MENU_ID);
        jdbcTemplate.update("INSERT INTO mealfit.menu (menu_id, name) VALUES (?, ?)",
                ORIGINAL_MENU_ID, "기존 메뉴");
        jdbcTemplate.update("INSERT INTO mealfit.menu (menu_id, name) VALUES (?, ?)",
                ADDED_MENU_ID, "추가 메뉴");
        jdbcTemplate.update("INSERT INTO mealfit.menu (menu_id, name) VALUES (?, ?)",
                REPLACEMENT_MENU_ID, "교체 메뉴");
    }

    @AfterEach
    void removeTestRows() {
        repository.deleteWeeklyPlans(FACILITY_ID, WEEK_START, WEEK_START.plusDays(6));
        jdbcTemplate.update("DELETE FROM mealfit.menu WHERE menu_id IN (?, ?, ?)",
                ORIGINAL_MENU_ID, ADDED_MENU_ID, REPLACEMENT_MENU_ID);
    }

    @Test
    void addDeleteReplaceAndReloadWeeklyMenuItems() {
        long planId = repository.insertPlan(FACILITY_ID, USER_ID, WEEK_START, "LUNCH", 25);
        repository.insertPlanItem(planId, ORIGINAL_MENU_ID, 1);
        repository.insertPlanItem(planId, ADDED_MENU_ID, 2);

        assertThat(loadMenuIds()).containsExactly(ORIGINAL_MENU_ID, ADDED_MENU_ID);

        repository.deletePlanItem(planId, ORIGINAL_MENU_ID);
        assertThat(loadMenuIds()).containsExactly(ADDED_MENU_ID);

        repository.insertPlanItem(planId, REPLACEMENT_MENU_ID, 2);
        assertThat(loadMenuIds()).containsExactly(ADDED_MENU_ID, REPLACEMENT_MENU_ID);

        repository.deleteWeeklyPlans(FACILITY_ID, WEEK_START, WEEK_START.plusDays(6));
        long replacementPlanId = repository.insertPlan(FACILITY_ID, USER_ID, WEEK_START, "LUNCH", 25);
        repository.insertPlanItem(replacementPlanId, REPLACEMENT_MENU_ID, 1);

        List<MealPlanRepository.WeeklyMealRow> reloaded = repository.findWeeklyPlans(
                FACILITY_ID, WEEK_START, WEEK_START.plusDays(6));
        assertThat(reloaded).hasSize(1);
        assertThat(reloaded.get(0).menuIds()).isEqualTo(String.valueOf(REPLACEMENT_MENU_ID));
        assertThat(reloaded.get(0).menuNames()).isEqualTo("교체 메뉴");
    }

    private List<Long> loadMenuIds() {
        return repository.findWeeklyPlans(FACILITY_ID, WEEK_START, WEEK_START.plusDays(6))
                .stream()
                .flatMap(row -> List.of(row.menuIds().split(",")).stream())
                .map(Long::valueOf)
                .toList();
    }
}
