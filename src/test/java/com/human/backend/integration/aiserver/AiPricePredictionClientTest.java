package com.human.backend.integration.aiserver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.human.backend.prediction.dto.request.AiWeeklyPriceHistoryPoint;
import com.human.backend.prediction.dto.request.AiWeeklyPricePredictionRequest;
import com.human.backend.prediction.dto.request.AiWeeklyPriceSeriesRequest;
import com.human.backend.prediction.dto.response.AiPricePredictionBatchResponse;
import com.human.backend.prediction.dto.response.AiWeeklyPricePredictionBatchResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class AiPricePredictionClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsSuccessfulPredictionResponse() throws IOException {
        startServer("/api/v1/price-predictions/next", 200, """
                {"predictions":[{"seriesId":3,"baseDate":"2026-09-18",
                "targetDate":"2026-09-19","basePrice":"0.791000",
                "predictedPrice":"0.791000","standardUnit":"g",
                "modelName":"lag_1_baseline","modelVersion":"lag_1_baseline_v1",
                "generatedAt":"2026-09-22T04:00:00Z"}]}
                """);

        AiPricePredictionBatchResponse response = client().predictNext(List.of(3L));

        assertEquals(3L, response.predictions().get(0).seriesId());
        assertEquals("0.791000", response.predictions().get(0).predictedPrice().toPlainString());
    }

    @Test
    void callsSevenDayPredictionEndpoint() throws IOException {
        startServer("/api/v1/price-predictions/7-days", 200, """
                {"predictions":[{"seriesId":3,"baseDate":"2026-09-18",
                "targetDate":"2026-09-25","basePrice":"0.791000",
                "predictedPrice":"0.812000","predictedMaxPrice":"0.844000",
                "standardUnit":"g","ridgeScore":0.73,"volatilityScore":0.81,
                "combinedRiskScore":0.77,"modelName":"weekly_mean_ridge",
                "modelVersion":"weekly_ridge_v1",
                "generatedAt":"2026-09-22T04:00:00Z"}]}
                """);

        AiWeeklyPricePredictionRequest request = new AiWeeklyPricePredictionRequest(List.of(
                new AiWeeklyPriceSeriesRequest(
                        3L,
                        "F00993",
                        "g",
                        List.of(
                                new AiWeeklyPriceHistoryPoint(
                                        LocalDate.of(2026, 9, 17),
                                        new BigDecimal("0.780000")),
                                new AiWeeklyPriceHistoryPoint(
                                        LocalDate.of(2026, 9, 18),
                                        new BigDecimal("0.791000"))))));
        AiWeeklyPricePredictionBatchResponse response = client().predictSevenDays(request);

        assertEquals("2026-09-25", response.predictions().get(0).targetDate().toString());
        assertEquals(0.77, response.predictions().get(0).combinedRiskScore());
    }

    @Test
    void mapsNotFoundResponse() throws IOException {
        startServer("/api/v1/price-predictions/next", 404,
                "{\"detail\":\"실측 가격 없음\"}");

        AiPricePredictionClientException exception = assertThrows(
                AiPricePredictionClientException.class,
                () -> client().predictNext(List.of(999L)));

        assertEquals(AiPricePredictionClientException.FailureType.NOT_FOUND,
                exception.getFailureType());
    }

    @Test
    void mapsServerErrorResponse() throws IOException {
        startServer("/api/v1/price-predictions/next", 500,
                "{\"detail\":\"서버 오류\"}");

        AiPricePredictionClientException exception = assertThrows(
                AiPricePredictionClientException.class,
                () -> client().predictNext(List.of(3L)));

        assertEquals(AiPricePredictionClientException.FailureType.SERVER_ERROR,
                exception.getFailureType());
    }

    @Test
    void mapsReadTimeout() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/price-predictions/next", exchange -> {
            try {
                Thread.sleep(300);
                respond(exchange, 200, "{\"predictions\":[]}");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        AiPricePredictionClient timeoutClient = new AiPricePredictionClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofMillis(50));

        AiPricePredictionClientException exception = assertThrows(
                AiPricePredictionClientException.class,
                () -> timeoutClient.predictNext(List.of(3L)));

        assertEquals(AiPricePredictionClientException.FailureType.TIMEOUT,
                exception.getFailureType());
    }

    private AiPricePredictionClient client() {
        return new AiPricePredictionClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private void startServer(String path, int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path,
                exchange -> respond(exchange, status, body));
        server.start();
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
