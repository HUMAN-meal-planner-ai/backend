package com.human.backend.admin.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 관리자 가격 현황 화면에 표시할 출처별 저장 통계입니다.
 * 가격 행이 아직 없는 출처도 시계열 등록 상태를 확인할 수 있도록 결과에 포함됩니다.
 *
 * @param sourceName KAMIS 등 price_series에 저장된 수집 출처 이름
 * @param seriesCount 해당 출처로 등록된 시계열 수
 * @param priceCount 해당 출처의 시계열에 연결된 가격 행 수
 * @param latestPriceDate 해당 출처의 가장 최근 가격 기준일
 * @param latestCollectedAt 해당 출처 데이터의 가장 최근 DB 저장 시각
 */
public record AdminPriceSourceSummaryResponse(
        String sourceName,
        long seriesCount,
        long priceCount,
        LocalDate latestPriceDate,
        Instant latestCollectedAt) {
}
