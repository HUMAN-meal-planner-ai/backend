package com.human.backend.admin.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.admin.dto.response.AdminPriceDataStatusResponse;
import com.human.backend.admin.dto.response.AdminPriceSourceSummaryResponse;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.repository.PriceSeriesRepository;

/**
 * 기존 price_series와 ingredient_price 테이블을 읽어 관리자용 수집 현황을 계산합니다.
 * 별도의 통계 테이블을 두지 않으므로 관리자가 조회하는 시점의 실제 DB 상태가 그대로 반영됩니다.
 */
@Service
public class AdminPriceDataService {

    private final IngredientPriceRepository ingredientPriceRepository;
    private final PriceSeriesRepository priceSeriesRepository;
    private final int staleAfterDays;
    private final ZoneId collectionZone;

    public AdminPriceDataService(
            IngredientPriceRepository ingredientPriceRepository,
            PriceSeriesRepository priceSeriesRepository,
            @Value("${admin.price-data.stale-after-days:14}") int staleAfterDays,
            @Value("${kamis.collection.schedule.zone:Asia/Seoul}") String collectionZone) {
        this.ingredientPriceRepository = ingredientPriceRepository;
        this.priceSeriesRepository = priceSeriesRepository;
        this.staleAfterDays = staleAfterDays;
        this.collectionZone = ZoneId.of(collectionZone);
    }

    @Transactional(readOnly = true)
    public AdminPriceDataStatusResponse getStatus() {
        // 최신 가격일과 최근 저장 시각은 의미가 다릅니다. 가격일은 시장 기준일이고 저장 시각은 수집 실행 시각입니다.
        LocalDate latestPriceDate = ingredientPriceRepository.findLatestPriceDate();
        Instant latestCollectedAt = ingredientPriceRepository.findLatestCollectedAt();

        // 관리자 요약 카드에 필요한 전체 규모와 현재 자동 수집 가능한 대상 수를 각각 계산합니다.
        long totalPriceCount = ingredientPriceRepository.count();
        long totalSeriesCount = priceSeriesRepository.count();
        long activeTargetCount = priceSeriesRepository.countActiveKamisCollectionTargets();

        // 데이터가 전혀 없을 때 null 날짜로 조회하지 않도록 0건으로 바로 처리합니다.
        long latestDatePriceCount = latestPriceDate == null
                ? 0 : ingredientPriceRepository.countByPriceDate(latestPriceDate);

        // Repository 투영 결과를 API 전용 DTO로 바꿔 영속 계층의 타입이 컨트롤러까지 노출되지 않게 합니다.
        List<AdminPriceSourceSummaryResponse> sources = priceSeriesRepository
                .findAdminPriceSourceSummaries().stream()
                .map(source -> new AdminPriceSourceSummaryResponse(
                        source.getSourceName(),
                        safeLong(source.getSeriesCount()),
                        safeLong(source.getPriceCount()),
                        source.getLatestPriceDate(),
                        source.getLatestCollectedAt()))
                .toList();

        // 모든 집계 결과와 판정 기준을 함께 반환해 프론트가 별도의 상태 계산을 하지 않도록 합니다.
        return new AdminPriceDataStatusResponse(
                determineStatus(latestPriceDate, totalPriceCount),
                Instant.now(),
                latestPriceDate,
                latestCollectedAt,
                totalPriceCount,
                totalSeriesCount,
                activeTargetCount,
                latestDatePriceCount,
                staleAfterDays,
                sources);
    }

    private String determineStatus(LocalDate latestPriceDate, long totalPriceCount) {
        // 행 또는 기준일이 하나라도 없으면 최신성 비교가 불가능하므로 NO_DATA로 판정합니다.
        if (totalPriceCount == 0 || latestPriceDate == null) {
            return "NO_DATA";
        }
        // 수집 기준 시간대의 오늘을 사용해 서버가 다른 지역에 있어도 최신성 판정이 같게 합니다.
        LocalDate staleBoundary = LocalDate.now(collectionZone).minusDays(staleAfterDays);
        return latestPriceDate.isBefore(staleBoundary) ? "STALE" : "UP";
    }

    private long safeLong(Long value) {
        // LEFT JOIN 집계 결과가 null인 경우에도 JSON 숫자 필드는 항상 0 이상을 반환합니다.
        return value == null ? 0 : value;
    }
}
