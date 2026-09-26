package com.human.backend.prediction.scheduler;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;

import com.human.backend.prediction.dto.response.PricePredictionCollectionResult;
import com.human.backend.prediction.service.PricePredictionService;

class PricePredictionSchedulerTest {

    @Test
    void delegatesWeeklyPredictionToSevenDayService() {
        PricePredictionService service = mock(PricePredictionService.class);
        when(service.generateSevenDayPredictions())
                .thenReturn(new PricePredictionCollectionResult(56, 56, 0, 56));
        PricePredictionScheduler scheduler = new PricePredictionScheduler(service);

        scheduler.generateWeeklyPredictions();

        verify(service).generateSevenDayPredictions();
        verify(service, never()).generateNextPredictions();
    }

    @Test
    void logsFailureWithoutBreakingFutureScheduledExecutions() {
        PricePredictionService service = mock(PricePredictionService.class);
        when(service.generateSevenDayPredictions())
                .thenThrow(new IllegalStateException("예측 생성 실패"));
        PricePredictionScheduler scheduler = new PricePredictionScheduler(service);
        Logger logger = (Logger) LoggerFactory.getLogger(PricePredictionScheduler.class);
        ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertDoesNotThrow(scheduler::generateWeeklyPredictions);
            verify(service).generateSevenDayPredictions();
            assertTrue(appender.list.stream().anyMatch(event ->
                    event.getLevel() == Level.ERROR
                            && event.getFormattedMessage().contains("주간 가격예측 생성에 실패했습니다")
                            && event.getFormattedMessage().contains("예측 생성 실패")
                            && event.getThrowableProxy() != null));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void runsOneHourAfterWeeklyKamisCollectionInTheSameZone() throws Exception {
        Method predictionMethod = PricePredictionScheduler.class
                .getMethod("generateWeeklyPredictions");
        Scheduled predictionSchedule = predictionMethod.getAnnotation(Scheduled.class);
        Method kamisMethod = com.human.backend.price.scheduler.KamisPriceCollectionScheduler.class
                .getMethod("collectRecentPrices");
        Scheduled kamisSchedule = kamisMethod.getAnnotation(Scheduled.class);

        assertEquals("${kamis.collection.schedule.cron:0 0 2 * * MON}",
                kamisSchedule.cron());
        assertEquals("${price.prediction.schedule.cron:0 0 3 * * MON}",
                predictionSchedule.cron());
        assertEquals(kamisSchedule.zone(), predictionSchedule.zone());
    }
}
