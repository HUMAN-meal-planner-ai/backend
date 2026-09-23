package com.human.backend.global.exception;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.human.backend.global.response.ErrorResponse;
import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.errorlog.service.ErrorLogService;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorLogService errorLogService;

    public GlobalExceptionHandler(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException exception, HttpServletRequest request) {
        // 사용자의 잘못된 입력인 4xx는 운영 장애가 아니므로 저장하지 않고,
        // 서버 문제를 의미하는 5xx ApiException만 관리 대상 로그로 남깁니다.
        if (exception.getStatus().is5xxServerError()) {
            persist(exception, exception.getStatus().value(), exception.getCode(), request);
        }
        return build(exception.getStatus(), exception.getCode(), exception.getMessage(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        // 검증 실패도 4xx이므로 DB 오류 로그에는 넣지 않고 필드별 안내만 반환합니다.
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
            fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "입력값을 확인해주세요.", fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        // 예상하지 못한 예외는 기존 서버 콘솔과 새 DB 로그 양쪽에 기록합니다.
        log.error("Unhandled API exception", exception);
        persist(exception, HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR", request);
        // stackTrace나 DB 오류 문구는 공격자에게 내부 구조를 알려줄 수 있으므로 클라이언트에 노출하지 않습니다.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "서버에서 요청을 처리하지 못했습니다.", Map.of());
    }

    private void persist(Exception exception, int httpStatus, String errorCode, HttpServletRequest request) {
        // SecurityContext는 현재 요청의 인증 정보를 보관합니다. 로그인 전 요청이면 principal은 null입니다.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof UserPrincipal user
            ? user : null;
        try {
            errorLogService.record(exception, httpStatus, errorCode, request, principal);
        } catch (RuntimeException loggingException) {
            // DB 장애 자체가 원인인 경우에도 원래 500 응답은 정상적으로 반환합니다.
            log.error("Failed to record server error in database", loggingException);
        }
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
            Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ErrorResponse(
            Instant.now(), status.value(), code, message, fieldErrors));
    }
}
