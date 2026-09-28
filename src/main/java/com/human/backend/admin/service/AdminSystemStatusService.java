package com.human.backend.admin.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.human.backend.admin.dto.response.AdminSystemStatusResponse;
import com.human.backend.admin.dto.response.SystemServiceStatusResponse;

/**
 * 관리자 요청 시 MealFit의 핵심 실행 구성요소를 짧은 제한 시간 안에 점검합니다.
 * 외부 서비스 점검 실패를 예외로 다시 던지지 않아 관리자 상태 화면 자체는 항상 열리게 합니다.
 */
@Service
public class AdminSystemStatusService {

    private static final String UP = "UP";
    private static final String DOWN = "DOWN";

    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient;
    private final String kamisApiUrl;
    private final String aiServerUrl;
    private final Duration requestTimeout;

    public AdminSystemStatusService(
            JdbcTemplate jdbcTemplate,
            @Value("${kamis.api.url}") String kamisApiUrl,
            @Value("${ai.server.url:http://localhost:8000}") String aiServerUrl,
            @Value("${admin.health.request-timeout:3s}") Duration requestTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.kamisApiUrl = kamisApiUrl;
        this.aiServerUrl = aiServerUrl;
        this.requestTimeout = requestTimeout;
        // 관리자 화면 한 번의 조회 때문에 외부 장애가 긴 대기로 이어지지 않도록 연결 제한 시간을 둡니다.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public AdminSystemStatusResponse getSystemStatus() {
        // Backend 자체는 이 메서드까지 요청이 도달한 것으로 확인하고, 나머지 의존 서비스는 실제 호출로 점검합니다.
        List<SystemServiceStatusResponse> services = List.of(
                new SystemServiceStatusResponse("backend", "Spring 백엔드", UP,
                        "관리자 API가 정상적으로 응답했습니다.", 0),
                checkDatabase(),
                checkHttpService("kamis", "KAMIS 가격 API", kamisApiUrl),
                checkHttpService("ai", "FastAPI AI 서버", appendPath(aiServerUrl, "/api/health")));

        // 백엔드가 살아 있어도 필수 연동 중 하나가 실패하면 전체 상태를 저하 상태로 표시합니다.
        String overallStatus = services.stream().allMatch(service -> UP.equals(service.status()))
                ? UP : "DEGRADED";
        return new AdminSystemStatusResponse(overallStatus, Instant.now(), services);
    }

    private SystemServiceStatusResponse checkDatabase() {
        // 테이블을 읽지 않는 SELECT 1을 사용해 업무 데이터와 무관하게 DB 연결 가능 여부만 확인합니다.
        long startedAt = System.nanoTime();
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean available = result != null && result == 1;
            return status("database", "PostgreSQL / Supabase", available,
                    available ? "DB 연결과 기본 쿼리가 정상입니다." : "DB 기본 쿼리 결과가 올바르지 않습니다.",
                    startedAt);
        } catch (RuntimeException exception) {
            // 상태 조회 API 자체가 500으로 실패하지 않도록 DB 오류를 DOWN 상태 데이터로 변환합니다.
            return status("database", "PostgreSQL / Supabase", false,
                    "DB 연결을 확인할 수 없습니다.", startedAt);
        }
    }

    private SystemServiceStatusResponse checkHttpService(String key, String name, String url) {
        // 본문은 사용하지 않고 HTTP 상태와 응답 시간만 필요하므로 discarding 핸들러를 사용합니다.
        long startedAt = System.nanoTime();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(requestTimeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            // 인증 파라미터가 없는 KAMIS 점검은 4xx일 수 있으나 서버가 응답했다면 연결 자체는 정상입니다.
            boolean available = response.statusCode() < 500;
            String message = available
                    ? "외부 서비스가 응답했습니다. (HTTP " + response.statusCode() + ")"
                    : "외부 서비스가 서버 오류를 반환했습니다. (HTTP " + response.statusCode() + ")";
            return status(key, name, available, message, startedAt);
        } catch (InterruptedException exception) {
            // 인터럽트 표식을 복원해야 상위 실행 환경이 작업 중단 사실을 올바르게 감지할 수 있습니다.
            Thread.currentThread().interrupt();
            return status(key, name, false, "상태 확인 작업이 중단되었습니다.", startedAt);
        } catch (Exception exception) {
            return status(key, name, false, "외부 서비스에 연결할 수 없습니다.", startedAt);
        }
    }

    private SystemServiceStatusResponse status(
            String key, String name, boolean available, String message, long startedAt) {
        // nanoTime은 시스템 시각 변경의 영향을 받지 않아 짧은 요청 소요 시간 측정에 적합합니다.
        long elapsedMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        return new SystemServiceStatusResponse(key, name, available ? UP : DOWN, message, elapsedMs);
    }

    private String appendPath(String baseUrl, String path) {
        // 환경변수 URL 끝의 슬래시 유무와 관계없이 중복 슬래시 없는 상태 확인 주소를 만듭니다.
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
    }
}
