package com.human.backend.admin.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자용 메뉴-식재료 연결 조회 서비스입니다. 조회 전용이며 데이터를 변경하지 않습니다. */
@Service
public class AdminMenuIngredientService {

    private static final int MAX_LIMIT = 100;

    private final JdbcTemplate jdbcTemplate;

    public AdminMenuIngredientService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record MenuSummary(Long menuId, String menuName, String category, int ingredientCount) {
    }

    public record IngredientLink(
            Long ingredientId,
            String ingredientName,
            String category,
            BigDecimal quantity,
            String standardUnit,
            String priceMappingStatus) {
    }

    public record MenuIngredients(Long menuId, String menuName, String category, List<IngredientLink> ingredients) {
    }

    @Transactional(readOnly = true)
    public List<MenuSummary> searchMenus(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        String like = "%" + (keyword == null ? "" : keyword.trim()) + "%";
        return jdbcTemplate.query(
                """
                SELECT m.menu_id, m.name, m.category, COUNT(mi.ingredient_id) AS ingredient_count
                FROM mealfit.menu m
                LEFT JOIN mealfit.menu_ingredient mi ON mi.menu_id = m.menu_id
                WHERE m.name ILIKE ?
                GROUP BY m.menu_id, m.name, m.category
                ORDER BY m.name
                LIMIT ?
                """,
                (rs, i) -> new MenuSummary(
                        rs.getLong("menu_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("ingredient_count")),
                like, safeLimit);
    }

    @Transactional(readOnly = true)
    public MenuIngredients getMenuIngredients(Long menuId) {
        List<MenuSummary> menus = jdbcTemplate.query(
                "SELECT menu_id, name, category FROM mealfit.menu WHERE menu_id = ?",
                (rs, i) -> new MenuSummary(rs.getLong("menu_id"), rs.getString("name"), rs.getString("category"), 0),
                menuId);
        if (menus.isEmpty()) {
            throw new IllegalArgumentException("메뉴를 찾을 수 없습니다: " + menuId);
        }
        MenuSummary menu = menus.get(0);

        List<IngredientLink> ingredients = jdbcTemplate.query(
                """
                SELECT i.ingredient_id, i.name, i.category, mi.quantity, i.standard_unit,
                       (SELECT CASE
                                 WHEN bool_or(ipm.review_status = 'APPROVED') THEN 'APPROVED'
                                 ELSE MIN(ipm.review_status)
                               END
                        FROM mealfit.ingredient_price_mapping ipm
                        WHERE ipm.ingredient_id = i.ingredient_id AND ipm.is_active) AS mapping_status
                FROM mealfit.menu_ingredient mi
                JOIN mealfit.ingredient i ON i.ingredient_id = mi.ingredient_id
                WHERE mi.menu_id = ?
                ORDER BY mi.quantity DESC NULLS LAST, i.ingredient_id
                """,
                (rs, i) -> new IngredientLink(
                        rs.getLong("ingredient_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getBigDecimal("quantity"),
                        rs.getString("standard_unit"),
                        rs.getString("mapping_status") == null ? "UNMAPPED" : rs.getString("mapping_status")),
                menuId);
        return new MenuIngredients(menu.menuId(), menu.menuName(), menu.category(), ingredients);
    }
}