package com.human.backend.budget.util;

import java.math.BigDecimal;

/**
 * [BUDG-003] 예산 및 식단 재구성 분석 도메인 공통 상수
 * [유지보수성 향상]: 분산된 임계치, 기본값, 벤치마크 단가를 중앙 집중 관리하여 정책 변경 시 단일 지점에서 수정 가능
 */
public final class BudgetConstants {

    private BudgetConstants() {
        // 인스턴스화 방지
    }

    // 1. 기본 조회 정책
    public static final int DEFAULT_TOP_N = 5;

    // 2. 주간 비용 기여도(%) 판정 임계값
    public static final BigDecimal URGENT_CONTRIBUTION_THRESHOLD = new BigDecimal("15.0");  // 15% 이상 (초고비용 메뉴)
    public static final BigDecimal HIGH_CONTRIBUTION_THRESHOLD = new BigDecimal("10.0");    // 10% 이상 (고비용 메뉴)
    public static final BigDecimal MODERATE_CONTRIBUTION_THRESHOLD = new BigDecimal("8.0"); // 8% 이상 (주의 메뉴)
    public static final BigDecimal LOW_CONTRIBUTION_THRESHOLD = new BigDecimal("5.0");      // 5% 이상 (일반 메뉴)

    // 3. 슬롯(분류)별 1인분 적정 기준 단가 (원) - 대체 메뉴 전환 시 잠재 절감액 산출 기준
    public static final BigDecimal BENCHMARK_COST_MAIN = new BigDecimal("1800");   // 주찬류 기준 단가
    public static final BigDecimal BENCHMARK_COST_SOUP = new BigDecimal("700");    // 국·찌개류 기준 단가
    public static final BigDecimal BENCHMARK_COST_SIDE = new BigDecimal("400");    // 부찬류 기준 단가
    public static final BigDecimal BENCHMARK_COST_KIMCHI = new BigDecimal("250");  // 김치류 기준 단가
    public static final BigDecimal BENCHMARK_COST_DEFAULT = new BigDecimal("500"); // 기타 기본 기준 단가
}
