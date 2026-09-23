package com.human.backend.cost.util;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 원가 및 예산 도메인 공통 상수 정의 클래스
 * [유지보수성 향상]: 분산된 기본값 및 매직 넘버를 중앙 집중 관리하여 데이터 일관성 보장
 */
public final class CostConstants {

    private CostConstants() {
        // 인스턴스화 방지
    }

    // 1. 기본 식별자 및 일자 기준
    public static final Long DEFAULT_FACILITY_ID = 1L;
    public static final LocalDate DEFAULT_BASE_DATE = LocalDate.of(2026, 9, 17);
    public static final LocalDate DEFAULT_PREDICTION_DATE = LocalDate.of(2026, 9, 20);

    // 2. 기본 원가 및 예산 기준 금액
    public static final BigDecimal DEFAULT_TARGET_COST = new BigDecimal("2500");
    public static final BigDecimal DEFAULT_MONTHLY_BUDGET = new BigDecimal("65000000");

    // 3. 연산 및 소수점 정밀도 정책
    public static final BigDecimal HUNDRED = new BigDecimal("100");
    public static final int PERCENT_CALC_SCALE = 4;   // 백분율 나눗셈 중간 정밀도
    public static final int PERCENT_DISPLAY_SCALE = 2; // 백분율 최종 표시 자릿수 (예: 12.34%)
    public static final int UNIT_PRICE_SCALE = 2;      // 1인분/단가 소수점 자릿수
    public static final int TOTAL_AMOUNT_SCALE = 0;    // 원화 최종 합계 금액 (원 단위 정수)
}
