package com.human.backend.menu.repository;
import com.human.backend.menu.dto.response.MenuResponse;
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
            SELECT menu_code, name, upper_category, category, serving_weight_g
            FROM mealfit.menu
            ORDER BY menu_code
            """;

        // 조회 결과를 MenuResponse DTO로 변환
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Number weight = (Number) rs.getObject("serving_weight_g");

            return new MenuResponse(
                rs.getString("menu_code"),
                rs.getString("name"),
                rs.getString("upper_category"),
                rs.getString("category"),
                weight == null ? null : weight.doubleValue(),
                null
            );
        });
    }
    // public  List<MenuByCode> getMenuByCode() {
    //     return List.of();
    // }
}