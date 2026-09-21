package com.human.backend.price.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import com.human.backend.price.dto.response.PriceTargetCollectionResult;
import com.human.backend.price.service.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KamisPriceCollectionScheduler {
    private static final Logger log = LoggerFactory.getLogger(KamisPriceCollectionScheduler.class);

    private final PriceService priceService;
    private final int lookbackDays;
    private final ZoneId zoneId;

    public KamisPriceCollectionScheduler(
            PriceService priceService,
            @Value("${kamis.collection.schedule.lookback-days:7}") int lookbackDays,
            @Value("${kamis.collection.schedule.zone:Asia/Seoul}") String zoneId) {
        if (lookbackDays < 1) {
            throw new IllegalArgumentException("KAMIS 가격 재조회 일수는 1일 이상이어야 합니다.");
        }
        this.priceService = priceService;
        this.lookbackDays = lookbackDays;
        this.zoneId = ZoneId.of(zoneId);
    }

    @Scheduled(
            cron = "${kamis.collection.schedule.cron:0 0 6 * * *}",
            zone = "${kamis.collection.schedule.zone:Asia/Seoul}")
    public void collectRecentPrices() {
        collectRecentPrices(LocalDate.now(zoneId));
    }

    void collectRecentPrices(LocalDate endDate) {
        LocalDate startDate = endDate.minusDays(lookbackDays - 1L);
        log.info("KAMIS 자동 가격 수집을 시작합니다. 조회 기간={}~{}", startDate, endDate);

        try {
            List<PriceTargetCollectionResult> results = priceService.collectAll(startDate, endDate);
            logCompletion(startDate, endDate, results);
        } catch (RuntimeException exception) {
            log.error(
                    "KAMIS 자동 가격 수집 작업에 실패했습니다. 조회 기간={}~{}, 사유={}",
                    startDate, endDate, exception.getMessage(), exception);
        }
    }

    private void logCompletion(
            LocalDate startDate, LocalDate endDate,
            List<PriceTargetCollectionResult> results) {
        long succeeded = results.stream()
                .filter(PriceTargetCollectionResult::success)
                .count();
        int fetchedRows = sum(results, PriceTargetCollectionResult::fetchedRows);
        int outOfRangeRows = sum(results, PriceTargetCollectionResult::outOfRangeRowsSkipped);
        int insertedRows = sum(results, PriceTargetCollectionResult::pricesInserted);
        int duplicateRows = sum(results, PriceTargetCollectionResult::duplicatesSkipped);
        int invalidRows = sum(results, PriceTargetCollectionResult::invalidRowsSkipped);

        log.info(
                "KAMIS 자동 가격 수집을 완료했습니다. 조회 기간={}~{}, 대상={}, 성공={}, 실패={}, "
                        + "조회 행={}, 신규 저장={}, 중복 제외={}, 기간 밖 제외={}, 유효하지 않은 행 제외={}",
                startDate, endDate, results.size(), succeeded, results.size() - succeeded,
                fetchedRows, insertedRows, duplicateRows, outOfRangeRows, invalidRows);
    }

    private int sum(
            List<PriceTargetCollectionResult> results,
            java.util.function.ToIntFunction<PriceTargetCollectionResult> mapper) {
        return results.stream().mapToInt(mapper).sum();
    }
}
