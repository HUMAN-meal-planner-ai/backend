package com.human.backend.errorlog.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 서버에서 발생한 5xx 오류 한 건을 표현하는 JPA 엔티티입니다.
 *
 * 콘솔 로그는 서버 재시작이나 배포 과정에서 사라질 수 있으므로, 관리자가 나중에
 * 조회할 수 있어야 하는 정보만 error_log 테이블에 영구 보관합니다. 비밀번호가
 * 들어갈 수 있는 요청 본문과 JWT가 들어 있는 Authorization 헤더는 의도적으로
 * 필드에 포함하지 않았습니다.
 */
@Entity
@Table(name = "error_log", schema = "mealfit")
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "error_log_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "exception_class", nullable = false, length = 255)
    private String exceptionClass;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    // 예외가 어느 호출 경로에서 발생했는지 추적하기 위한 전체 호출 스택입니다.
    // VARCHAR보다 긴 문자열이 필요하므로 PostgreSQL의 TEXT 타입으로 매핑합니다.
    @Column(name = "stack_trace", nullable = false, columnDefinition = "text")
    private String stackTrace;

    @Column(name = "request_method", nullable = false, length = 10)
    private String requestMethod;

    @Column(name = "request_path", nullable = false, length = 1000)
    private String requestPath;

    // 로그인하지 않은 공개 API에서도 오류가 날 수 있으므로 사용자 정보는 nullable입니다.
    // AppUser 연관관계로 만들지 않아 사용자가 삭제되어도 과거 오류 기록은 유지됩니다.
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email", length = 150)
    private String userEmail;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    // JPA가 DB 조회 결과로 객체를 만들 때 사용하는 생성자입니다.
    // 애플리케이션 코드가 빈 오류 로그를 만들지 못하도록 protected로 제한합니다.
    protected ErrorLog() {
    }

    public ErrorLog(int httpStatus, String errorCode, String exceptionClass, String errorMessage,
            String stackTrace, String requestMethod, String requestPath, Long userId, String userEmail) {
        // 서버의 지역 시간대에 영향을 받지 않도록 UTC 기준 시각인 Instant를 사용합니다.
        this.occurredAt = Instant.now();
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
        this.exceptionClass = exceptionClass;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.requestMethod = requestMethod;
        this.requestPath = requestPath;
        this.userId = userId;
        this.userEmail = userEmail;
        this.resolved = false;
    }

    /**
     * 관리자가 로그 확인 및 조치를 끝냈음을 표시합니다.
     * 이미 처리된 로그를 다시 호출해도 최초 처리 시각과 관리자를 보존하도록 멱등적으로 작성했습니다.
     */
    public void resolve(Long adminUserId) {
        if (!resolved) {
            resolved = true;
            resolvedAt = Instant.now();
            resolvedBy = adminUserId;
        }
    }

    public Long getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public int getHttpStatus() { return httpStatus; }
    public String getErrorCode() { return errorCode; }
    public String getExceptionClass() { return exceptionClass; }
    public String getErrorMessage() { return errorMessage; }
    public String getStackTrace() { return stackTrace; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestPath() { return requestPath; }
    public Long getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public boolean isResolved() { return resolved; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Long getResolvedBy() { return resolvedBy; }
}
