package com.human.backend.cost.repository;

import com.human.backend.cost.entity.MenuIngredientCostVo;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 인메모리(더미 데이터) 기반의 CostRepository 구현체
 * 개발 및 테스트 단계에서 활용되며, 추후 Database 연동 구현체로 원활히 교체할 수 있습니다.
 */
@Repository
public class MemoryCostRepository implements CostRepository {

    // 맵 객체 생성(더미 데이터용)
    private static final Map<Long, String> MENU_MAP = new HashMap<>();
    private static final Map<Long, List<MenuIngredientCostVo>> MENU_INGREDIENTS_MAP = new HashMap<>();

    // 더미 데이터 생성
    static {
        // [1] menu.csv 더미
        MENU_MAP.put(101L, "돼지고기 김치찌개");
        MENU_MAP.put(102L, "시금치 된장국");

        // [2] menu_ingredient.csv + ingredient_price.csv 결합 더미 (최신 price_date 단가 반영)
        // 101번 메뉴: 김치찌개 (돼지고기 100g, 배추김치 150g, 두부 50g)
        MENU_INGREDIENTS_MAP.put(101L, List.of(
            new MenuIngredientCostVo(1L, "돼지고기(전지)", new BigDecimal("100"), new BigDecimal("14.5"), LocalDate.of(2026, 9, 15)),
            new MenuIngredientCostVo(2L, "배추김치", new BigDecimal("150"), new BigDecimal("5.8"), LocalDate.of(2026, 9, 15)),
            new MenuIngredientCostVo(3L, "두부", new BigDecimal("50"), new BigDecimal("7.2"), LocalDate.of(2026, 9, 15))
        ));

        // 102번 메뉴: 시금치 된장국 (시금치 70g, 재래된장 30g, 대파 20g)
        MENU_INGREDIENTS_MAP.put(102L, List.of(
            new MenuIngredientCostVo(4L, "시금치", new BigDecimal("70"), new BigDecimal("21.0"), LocalDate.of(2026, 9, 15)),
            new MenuIngredientCostVo(5L, "재래된장", new BigDecimal("30"), new BigDecimal("9.5"), LocalDate.of(2026, 9, 15)),
            new MenuIngredientCostVo(6L, "대파", new BigDecimal("20"), new BigDecimal("8.0"), LocalDate.of(2026, 9, 15))
        ));
    }

    // 등록된 모든 메뉴 ID 조회 (메뉴별 일괄 계산용)
    @Override
    public List<Long> findAllMenuIds() {
        return new ArrayList<>(MENU_MAP.keySet());
    }

    // 아이디로 메뉴 찾기(Optional, 스트링 값이 Null이 아니면 쓰고 Null이면 기본값이나 오류를 던지도록 강제한다.)
    // 메서드에 메뉴ID 넣고 호출하면 메뉴ID에 해당하는 메뉴명을 Optional로 반환
    @Override
    public Optional<String> findMenuNameById(Long menuId) {
        return Optional.ofNullable(MENU_MAP.get(menuId));
    }

    // getOrDefault:: 메뉴ID 값에 해당하는 값을 반환하되, 없다면 Collections에 있는 emptyList()를 반환한다
    @Override
    public List<MenuIngredientCostVo> findLatestIngredientsByMenuId(Long menuId) {
        return MENU_INGREDIENTS_MAP.getOrDefault(menuId, Collections.emptyList());
    }
}
