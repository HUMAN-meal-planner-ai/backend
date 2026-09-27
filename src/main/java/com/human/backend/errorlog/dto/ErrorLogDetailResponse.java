package com.human.backend.errorlog.dto;

import java.time.Instant;

import com.human.backend.errorlog.entity.ErrorLog;

/** 특정 로그를 열었을 때 사용하는 상세 DTO이며, 원인 분석에 필요한 stackTrace를 포함합니다. */
public record ErrorLogDetailResponse(
        Long id,
        Instant occurredAt,
        int httpStatus,
        String errorCode,
        String exceptionClass,
        String errorMessage,
        String stackTrace,
        String requestMethod,
        String requestPath,
        Long userId,
        String userEmail,
        boolean resolved,
        Instant resolvedAt,
        Long resolvedBy) {

    public static ErrorLogDetailResponse from(ErrorLog log) {
        return new ErrorLogDetailResponse(log.getId(), log.getOccurredAt(), log.getHttpStatus(),
            log.getErrorCode(), log.getExceptionClass(), log.getErrorMessage(), log.getStackTrace(),
            log.getRequestMethod(), log.getRequestPath(), log.getUserId(), log.getUserEmail(),
            log.isResolved(), log.getResolvedAt(), log.getResolvedBy());
    }
}
