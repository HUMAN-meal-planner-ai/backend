package com.human.backend.admin.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 관리자에게 가격 데이터의 출처, 최신성, 저장 규모를 한 번에 전달합니다.
 * status는 UP, STALE, NO_DATA 중 하나이며 테이블 추가 없이 기존 데이터를 집계합니다.
 *
 * @param status 가격 데이터 상태(UP: 최신, STALE: 갱신 필요, NO_DATA: 저장 데이터 없음)
 * @param checkedAt 서버가 현황을 조회한 시각
 * @param latestPriceDate 저장된 가격 중 가장 최근 가격 기준일
 * @param latestCollectedAt 데이터가 DB에 저장된 가장 최근 시각
 * @param totalPriceCount ingredient_price 전체 행 수
 * @param totalSeriesCount price_series 전체 행 수
 * @param activeCollectionTargetCount 현재 KAMIS 자동 수집 조건을 충족하는 시계열 수
 * @param latestDatePriceCount latestPriceDate에 해당하는 가격 행 수
 * @param staleAfterDays STALE 판정에 사용하는 허용 일수
 * @param sources 가격 출처별 집계 목록
 */
public record AdminPriceDataStatusResponse(
        String status,
        Instant checkedAt,
        LocalDate latestPriceDate,
        Instant latestCollectedAt,
        long totalPriceCount,
        long totalSeriesCount,
        long activeCollectionTargetCount,
        long latestDatePriceCount,
        int staleAfterDays,
        List<AdminPriceSourceSummaryResponse> sources) {
}
