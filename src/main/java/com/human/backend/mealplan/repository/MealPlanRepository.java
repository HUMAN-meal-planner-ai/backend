package com.human.backend.mealplan.repository;

import java.math.BigDecimal;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 주간 식단을 DB에 저장하고 조회하는 Repository입니다.
 *
 * 식단은 두 테이블에 나누어 저장됩니다.
 * - meal_plan: 날짜, 끼니, 식수, 원가 같은 식단 기본 정보
 * - meal_plan_item: 한 끼에 포함된 menu_id 목록
 *
 * 화면에 메뉴 이름을 보여줄 때는 meal_plan_item과 menu를 JOIN합니다.
 */
@Repository
public class MealPlanRepository {

    private final JdbcTemplate jdbcTemplate;

    public MealPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 식단 기본 정보를 저장하고 새로 만들어진 plan_id를 반환합니다. */
    public long insertPlan(
            long facilityId,
            long userId,
            LocalDate mealDate,
            String mealType,
            int mealCount) {
        String sql = """
                INSERT INTO mealfit.meal_plan (
                    facility_id,
                        user_id,
                    plan_date,
                    meal_type,
                    meal_count,
                        version
                    ) VALUES (?, ?, ?, ?, ?, 1)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, facilityId);
            statement.setLong(2, userId);
            statement.setObject(3, mealDate);
            statement.setString(4, mealType);
            statement.setInt(5, mealCount);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("식단 저장 후 plan_id를 받지 못했습니다.");
        }
        return generatedId.longValue();
    }

    /** 저장된 식단에 메뉴 하나를 연결합니다. */
    public void insertPlanItem(long planId, long menuId, int displayOrder) {
        jdbcTemplate.update(
                """
                INSERT INTO mealfit.meal_plan_item (plan_id, menu_id, display_order)
                VALUES (?, ?, ?)
                """,
                planId,
                menuId,
                displayOrder);
    }

    /**
     * 주간 식단을 조회하면서 메뉴 이름을 함께 가져옵니다.
     * string_agg는 한 끼에 메뉴가 여러 개일 때 이름을 하나의 문자열로 합칩니다.
         * LEFT JOIN이므로 메뉴 연결이 없어도 조식/중식/석식 행 자체는 표시됩니다.
     */
    public List<WeeklyMealRow> findWeeklyPlans(
            long facilityId,
            LocalDate weekStartDate,
            LocalDate weekEndDate) {
        return jdbcTemplate.query(
                """
                SELECT
                    mp.plan_id,
                    mp.plan_date,
                    mp.meal_type,
                    mp.meal_count,
                    STRING_AGG(m.name, ', ' ORDER BY mpi.display_order) AS menu_names
                FROM mealfit.meal_plan mp
                                LEFT JOIN mealfit.meal_plan_item mpi
                  ON mpi.plan_id = mp.plan_id
                                LEFT JOIN mealfit.menu m
                  ON m.menu_id = mpi.menu_id
                WHERE mp.facility_id = ?
                  AND mp.plan_date BETWEEN ? AND ?
                GROUP BY
                    mp.plan_id,
                    mp.plan_date,
                    mp.meal_type,
                                        mp.meal_count
                ORDER BY mp.plan_date, mp.meal_type, mp.plan_id
                """,
                (resultSet, rowNumber) -> new WeeklyMealRow(
                        resultSet.getLong("plan_id"),
                        resultSet.getObject("plan_date", LocalDate.class),
                        resultSet.getString("meal_type"),
                        resultSet.getInt("meal_count"),
                        BigDecimal.ZERO,
                        resultSet.getString("menu_names")),
                facilityId,
                weekStartDate,
                weekEndDate);
    }

    /** DB 한 행을 Service가 이해하기 쉬운 불변 객체로 표현합니다. */
    public record WeeklyMealRow(
            long planId,
            LocalDate mealDate,
            String mealType,
            int mealCount,
            BigDecimal costPerPerson,
            String menuNames) {
    }
}
