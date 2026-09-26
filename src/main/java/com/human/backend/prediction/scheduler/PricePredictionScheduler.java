package com.human.backend.prediction.scheduler;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.prediction.service.PricePredictionService;

@Component
public class PricePredictionScheduler {

    private static final Logger log = LoggerFactory.getLogger(PricePredictionScheduler.class);

    private final PricePredictionService service;

    public PricePredictionScheduler(PricePredictionService service) {
        this.service = service;
    }

    @Scheduled(
            cron = "${price.prediction.schedule.cron:0 0 3 * * MON}",
            zone = "${kamis.collection.schedule.zone:Asia/Seoul}")
    public void generateWeeklyPredictions() {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info("주간 가격예측 생성을 시작합니다. 실행 시작 시각={}", startedAt);

        try {
            PricePredictionCollectionResult result = service.generateSevenDayPredictions();
            log.info(
                    "주간 가격예측 생성을 완료했습니다. 실행 시작 시각={}, 소요 시간={}ms, "
                            + "대상={}, 응답={}, 신규 저장={}, 중복 제외={}",
                    startedAt, elapsedMillis(startedNanos), result.requestedSeries(),
                    result.receivedPredictions(), result.insertedPredictions(),
                    result.duplicatesSkipped());
        } catch (RuntimeException exception) {
            log.error(
                    "주간 가격예측 생성에 실패했습니다. 실행 시작 시각={}, 소요 시간={}ms, 사유={}",
                    startedAt, elapsedMillis(startedNanos), exception.getMessage(), exception);
        }
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
