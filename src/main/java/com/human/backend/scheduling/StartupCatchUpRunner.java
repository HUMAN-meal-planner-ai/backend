package com.human.backend.scheduling;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.human.backend.prediction.repository.PricePredictionRepository;
import com.human.backend.prediction.scheduler.PricePredictionScheduler;
import com.human.backend.price.repository.IngredientPriceRepository;
import com.human.backend.price.scheduler.KamisPriceCollectionScheduler;

/**
 * 서버가 꺼져 있어 정기 실행 시각을 놓친 경우를 보정합니다.
 *
 * <p>기동이 끝나면 별도 스레드에서 다음 순서로 확인합니다.
 * <ol>
 *   <li>가장 최근 KAMIS 수집 예정 시각 이후에 저장된 가격이 없으면 가격을 수집합니다.</li>
 *   <li>가장 최근 예측 예정 시각 이후에 생성된 예측이 없고, 예측 기준일보다 새로운 가격이 있으면 예측을 생성합니다.</li>
 * </ol>
 * 가격 수집과 예측 저장은 모두 중복 저장을 건너뛰므로 여러 번 실행되어도 안전합니다.
 * 예측은 갱신된 가격을 사용해야 하므로 가격 수집 다음에 실행합니다.
 */
@Component
public class StartupCatchUpRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupCatchUpRunner.class);

    private final IngredientPriceRepository priceRepository;
    private final PricePredictionRepository predictionRepository;
    private final KamisPriceCollectionScheduler priceScheduler;
    private final PricePredictionScheduler predictionScheduler;
    private final boolean enabled;
    private final int maxPriceGapDays;
    private final CronExpression priceCron;
    private final CronExpression predictionCron;
    private final ZoneId priceZone;
    private final ZoneId predictionZone;
    private final Clock clock;

    @Autowired
    public StartupCatchUpRunner(
            IngredientPriceRepository priceRepository,
            PricePredictionRepository predictionRepository,
            KamisPriceCollectionScheduler priceScheduler,
            PricePredictionScheduler predictionScheduler,
            @Value("${app.catch-up.enabled:true}") boolean enabled,
            @Value("${app.catch-up.max-price-gap-days:90}") int maxPriceGapDays,
            @Value("${kamis.collection.schedule.cron:0 0 10 * * *}") String priceCron,
            @Value("${price.prediction.schedule.cron:0 0 3 * * MON}") String predictionCron,
            @Value("${kamis.collection.schedule.zone:Asia/Seoul}") String zone) {
        this(priceRepository, predictionRepository, priceScheduler, predictionScheduler,
                enabled, maxPriceGapDays, priceCron, predictionCron, zone, Clock.systemUTC());
    }

    StartupCatchUpRunner(
            IngredientPriceRepository priceRepository,
            PricePredictionRepository predictionRepository,
            KamisPriceCollectionScheduler priceScheduler,
            PricePredictionScheduler predictionScheduler,
            boolean enabled,
            int maxPriceGapDays,
            String priceCron,
            String predictionCron,
            String zone,
            Clock clock) {
        this.priceRepository = priceRepository;
        this.predictionRepository = predictionRepository;
        this.priceScheduler = priceScheduler;
        this.predictionScheduler = predictionScheduler;
        this.enabled = enabled;
        this.maxPriceGapDays = Math.max(1, maxPriceGapDays);
        this.priceCron = CronExpression.parse(priceCron);
        this.predictionCron = CronExpression.parse(predictionCron);
        this.priceZone = ZoneId.of(zone);
        this.predictionZone = ZoneId.of(zone);
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!enabled) {
            log.info("기동 시 누락 보정이 비활성화되어 있습니다.");
            return;
        }
        // 기동과 HTTP 요청 처리를 막지 않도록 별도 스레드에서 실행합니다.
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "startup-catch-up");
            thread.setDaemon(true);
            return thread;
        });
        executor.submit(this::runCatchUp);
        executor.shutdown();
    }

    /** 가격 수집 보정 후 예측 보정을 순서대로 실행합니다. 한쪽이 실패해도 다른 쪽은 진행합니다. */
    void runCatchUp() {
        try {
            catchUpPrices();
        } catch (RuntimeException exception) {
            log.error("기동 시 가격 수집 보정에 실패했습니다. 사유={}", exception.getMessage(), exception);
        }
        try {
            catchUpPredictions();
        } catch (RuntimeException exception) {
            log.error("기동 시 가격 예측 보정에 실패했습니다. 사유={}", exception.getMessage(), exception);
        }
    }

    boolean catchUpPrices() {
        Instant now = clock.instant();
        ZonedDateTime scheduled = lastScheduledTime(priceCron, priceZone, now);
        if (scheduled == null) {
            return false;
        }
        Instant latestCollectedAt = priceRepository.findLatestCollectedAt();
        if (latestCollectedAt != null && !latestCollectedAt.isBefore(scheduled.toInstant())) {
            log.info("가격 데이터가 최근 수집 예정 시각({}) 이후 갱신되어 있어 보정하지 않습니다.", scheduled);
            return false;
        }

        LocalDate endDate = now.atZone(priceZone).toLocalDate();
        LocalDate startDate = endDate.minusDays(priceScheduler.getLookbackDays() - 1L);
        if (latestCollectedAt != null) {
            // 서버가 오래 꺼져 있었다면 마지막 수집일부터 다시 조회해 공백을 메웁니다.
            LocalDate lastCollectedDate = latestCollectedAt.atZone(priceZone).toLocalDate();
            if (lastCollectedDate.isBefore(startDate)) {
                startDate = lastCollectedDate;
            }
        }
        LocalDate earliest = endDate.minusDays(maxPriceGapDays - 1L);
        if (startDate.isBefore(earliest)) {
            startDate = earliest;
        }
        log.info("가격 수집 예정 시각({})을 놓친 것으로 판단해 보정 수집을 시작합니다. 조회 기간={}~{}",
                scheduled, startDate, endDate);
        priceScheduler.collectRange(startDate, endDate);
        return true;
    }

    boolean catchUpPredictions() {
        Instant now = clock.instant();
        ZonedDateTime scheduled = lastScheduledTime(predictionCron, predictionZone, now);
        if (scheduled == null) {
            return false;
        }
        Instant latestGeneratedAt = predictionRepository.findLatestWeeklyGeneratedAt();
        if (latestGeneratedAt != null && !latestGeneratedAt.isBefore(scheduled.toInstant())) {
            log.info("주간 예측이 최근 예측 예정 시각({}) 이후 생성되어 있어 보정하지 않습니다.", scheduled);
            return false;
        }
        LocalDate latestPriceDate = priceRepository.findLatestPriceDate();
        LocalDate latestPredictionBaseDate = predictionRepository.findLatestWeeklyBaseDate();
        if (latestPriceDate != null && latestPredictionBaseDate != null
                && !latestPredictionBaseDate.isBefore(latestPriceDate)) {
            log.info("예측 기준일({})이 최신 가격일({}) 이상이라 새로 예측할 데이터가 없어 보정하지 않습니다.",
                    latestPredictionBaseDate, latestPriceDate);
            return false;
        }
        log.info("가격 예측 예정 시각({})을 놓친 것으로 판단해 보정 예측을 시작합니다.", scheduled);
        predictionScheduler.generateWeeklyPredictions();
        return true;
    }

    /** now 이전(같은 시각 포함)에 크론이 마지막으로 실행되었어야 할 시각을 반환합니다. */
    static ZonedDateTime lastScheduledTime(CronExpression cron, ZoneId zone, Instant now) {
        ZonedDateTime current = now.atZone(zone);
        ZonedDateTime cursor = current.minus(35, ChronoUnit.DAYS);
        ZonedDateTime last = null;
        for (int i = 0; i < 5000; i++) {
            ZonedDateTime next = cron.next(cursor);
            if (next == null || next.isAfter(current)) {
                break;
            }
            last = next;
            cursor = next;
        }
        return last;
    }
}
