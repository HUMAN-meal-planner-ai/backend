
package com.human.backend.menu.repository;

import com.human.backend.menu.dto.response.MenuResponse;
import com.human.backend.menu.domain.MenuSlot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public class MenuRepository {

    private final JdbcTemplate jdbcTemplate;

    // DB 연결 사용
    public MenuRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

        // 메뉴 목록과 각 메뉴에 연결된 고유 식재료 수를 함께 조회합니다.
    public List<MenuResponse> getMenus() {
        String sql = """
              SELECT m.menu_id, m.menu_code, m.name, m.upper_category, m.category, m.slot_type, m.serving_weight_g,
                  m.energy_kcal, m.protein_g, m.fat_g, m.carbohydrate_g, m.sodium_mg,
                  (SELECT COUNT(DISTINCT mi.ingredient_id)
                   FROM mealfit.menu_ingredient mi
                   WHERE mi.menu_id = m.menu_id) AS food_count
              FROM mealfit.menu m
              ORDER BY m.menu_code
                """;

        // 조회 결과를 MenuResponse DTO로 변환
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Number weight = (Number) rs.getObject("serving_weight_g");
            Number energy = (Number) rs.getObject("energy_kcal");
            Number protein = (Number) rs.getObject("protein_g");
            Number fat = (Number) rs.getObject("fat_g");
            Number carb = (Number) rs.getObject("carbohydrate_g");
            Number sodium = (Number) rs.getObject("sodium_mg");

            String mainCategory = rs.getString("upper_category");
            String subCategory = rs.getString("category");

            return MenuResponse.builder()
                    .menuId(rs.getLong("menu_id"))
                    .menuCode(rs.getString("menu_code"))
                    .menuName(rs.getString("name"))
                    .mainCategory(mainCategory)
                    .subCategory(subCategory)
                    .slot(toMenuSlot(rs.getString("slot_type")))
                    .weight(weight == null ? null : weight.doubleValue())
                    .energyKcal(energy == null ? null : energy.doubleValue())
                    .proteinG(protein == null ? null : protein.doubleValue())
                    .fatG(fat == null ? null : fat.doubleValue())
                    .carbohydrateG(carb == null ? null : carb.doubleValue())
                    .sodiumMg(sodium == null ? null : sodium.doubleValue())
                    .foodCount(rs.getInt("food_count"))
                    .ingredients(List.of())
                    .build();
        });
    }

    public List<MenuResponse> searchMenus(String keyword) {

        String sql = """
                SELECT DISTINCT
                    m.menu_id,
                    m.menu_code,
                    m.name,
                    m.upper_category,
                    m.category,
                    m.slot_type,
                    m.serving_weight_g,
                    m.energy_kcal,
                    m.protein_g,
                    m.fat_g,
                    m.carbohydrate_g,
                    m.sodium_mg,
                    (SELECT COUNT(DISTINCT mi_count.ingredient_id)
                     FROM mealfit.menu_ingredient mi_count
                     WHERE mi_count.menu_id = m.menu_id) AS food_count
                FROM mealfit.menu m
                LEFT JOIN mealfit.menu_ingredient mi
                    ON m.menu_id = mi.menu_id
                LEFT JOIN mealfit.ingredient i
                    ON mi.ingredient_id = i.ingredient_id
                WHERE
                    m.name ILIKE ?
                    OR m.menu_code ILIKE ?
                    OR m.upper_category ILIKE ?
                    OR m.category ILIKE ?
                    OR i.name ILIKE ?
                ORDER BY m.menu_code
                """;

        String searchKeyword = "%" + keyword + "%";

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {

                    Number weight = (Number) rs.getObject("serving_weight_g");
                    Number energy = (Number) rs.getObject("energy_kcal");
                    Number protein = (Number) rs.getObject("protein_g");
                    Number fat = (Number) rs.getObject("fat_g");
                    Number carb = (Number) rs.getObject("carbohydrate_g");
                    Number sodium = (Number) rs.getObject("sodium_mg");

                    String mainCategory = rs.getString("upper_category");
                    String subCategory = rs.getString("category");

                    return MenuResponse.builder()
                            .menuId(rs.getLong("menu_id"))
                            .menuCode(rs.getString("menu_code"))
                            .menuName(rs.getString("name"))
                            .mainCategory(mainCategory)
                            .subCategory(subCategory)
                            .slot(toMenuSlot(rs.getString("slot_type")))
                            .weight(weight == null ? null : weight.doubleValue())
                            .energyKcal(energy == null ? null : energy.doubleValue())
                            .proteinG(protein == null ? null : protein.doubleValue())
                            .fatG(fat == null ? null : fat.doubleValue())
                            .carbohydrateG(carb == null ? null : carb.doubleValue())
                            .sodiumMg(sodium == null ? null : sodium.doubleValue())
                            .foodCount(rs.getInt("food_count"))
                            .ingredients(List.of())
                            .build();
                },
                searchKeyword,
                searchKeyword,
                searchKeyword,
                searchKeyword,
                searchKeyword);
    }

    private MenuSlot toMenuSlot(String slotType) {
        if (slotType == null || slotType.isBlank()) {
            return MenuSlot.OTHER;
        }
        try {
            return MenuSlot.valueOf(slotType.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MenuSlot.OTHER;
        }
    }
}
