package com.human.backend.errorlog.dto;

import java.time.Instant;

import com.human.backend.errorlog.entity.ErrorLog;

/**
 * 목록 화면용 DTO입니다. 용량이 큰 stackTrace는 제외하고 검색 결과에 필요한 요약만 반환합니다.
 * 엔티티를 그대로 응답하지 않으면 DB 구조와 외부 API 구조가 강하게 결합되는 것도 막을 수 있습니다.
 */
public record ErrorLogSummaryResponse(
        Long id,
        Instant occurredAt,
        int httpStatus,
        String errorCode,
        String exceptionClass,
        String errorMessage,
        String requestMethod,
        String requestPath,
        Long userId,
        String userEmail,
        boolean resolved,
        Instant resolvedAt,
        Long resolvedBy) {

    public static ErrorLogSummaryResponse from(ErrorLog log) {
        return new ErrorLogSummaryResponse(log.getId(), log.getOccurredAt(), log.getHttpStatus(),
            log.getErrorCode(), log.getExceptionClass(), log.getErrorMessage(), log.getRequestMethod(),
            log.getRequestPath(), log.getUserId(), log.getUserEmail(), log.isResolved(),
            log.getResolvedAt(), log.getResolvedBy());
    }
}
