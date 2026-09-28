
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

    // menu 테이블에서 메뉴 목록 조회
    public List<MenuResponse> getMenus() {
        String sql = """
                SELECT menu_id, menu_code, name, upper_category, category, serving_weight_g
                FROM mealfit.menu
                ORDER BY menu_code
                """;

        // 조회 결과를 MenuResponse DTO로 변환
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Number weight = (Number) rs.getObject("serving_weight_g");

            String mainCategory = rs.getString("upper_category");
            String subCategory = rs.getString("category");

            // 확장된 MenuResponse 형식에 맞춰 DB 메뉴의 슬롯을 카테고리에서 계산합니다.
            return new MenuResponse(
                    rs.getLong("menu_id"),
                    rs.getString("menu_code"),
                    rs.getString("name"),
                    mainCategory,
                    subCategory,
                    MenuSlot.from(mainCategory, subCategory),
                    weight == null ? null : weight.doubleValue(),
                    null,
                    List.of());
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
                    m.serving_weight_g
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

                    String mainCategory = rs.getString("upper_category");

                    String subCategory = rs.getString("category");

                    return new MenuResponse(
                            rs.getLong("menu_id"),
                            rs.getString("menu_code"),
                            rs.getString("name"),
                            mainCategory,
                            subCategory,
                            MenuSlot.from(mainCategory, subCategory),
                            weight == null
                                    ? null
                                    : weight.doubleValue(),
                            null,
                            List.of());
                },
                searchKeyword,
                searchKeyword,
                searchKeyword,
                searchKeyword,
                searchKeyword);
    }
}
