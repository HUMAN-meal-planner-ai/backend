package com.human.backend.facility.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MonthlyBudgetRepository {

    private final JdbcTemplate jdbcTemplate;

    public MonthlyBudgetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void saveMonth(Long facilityId, YearMonth month, BigDecimal amount) {
        LocalDate monthStart = month.atDay(1);
        jdbcTemplate.update(
            "DELETE FROM mealfit.monthly_budget WHERE facility_id = ? AND budget_month = ?",
            facilityId, monthStart);
        jdbcTemplate.update(
            "INSERT INTO mealfit.monthly_budget (facility_id, budget_month, budget_amount) VALUES (?, ?, ?)",
            facilityId, monthStart, amount);
    }

    public int findMonthlyMealCount(Long facilityId, YearMonth month) {
        Integer mealCount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(meal_count), 0) FROM mealfit.meal_plan WHERE facility_id = ? AND plan_date >= ? AND plan_date < ?",
            Integer.class,
            facilityId,
            month.atDay(1),
            month.plusMonths(1).atDay(1));
        return mealCount == null ? 0 : mealCount;
    }

    public Optional<BigDecimal> findBudget(Long facilityId, YearMonth month) {
        return jdbcTemplate.query(
            "SELECT budget_amount FROM mealfit.monthly_budget WHERE facility_id = ? AND budget_month = ? LIMIT 1",
            (resultSet, rowNumber) -> resultSet.getBigDecimal("budget_amount"),
                facilityId,
                month.atDay(1))
                .stream()
                .findFirst();
    }
}