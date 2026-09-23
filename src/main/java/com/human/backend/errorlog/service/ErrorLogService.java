package com.human.backend.errorlog.service;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.errorlog.dto.ErrorLogDetailResponse;
import com.human.backend.errorlog.dto.ErrorLogSummaryResponse;
import com.human.backend.errorlog.entity.ErrorLog;
import com.human.backend.errorlog.repository.ErrorLogRepository;
import com.human.backend.global.exception.ApiException;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogService.class);
    // 비정상적으로 긴 스택 트레이스가 DB 용량을 과도하게 차지하지 않도록 상한을 둡니다.
    private static final int MAX_STACK_TRACE_LENGTH = 16_000;

    private final ErrorLogRepository errorLogRepository;

    public ErrorLogService(ErrorLogRepository errorLogRepository) {
        this.errorLogRepository = errorLogRepository;
    }

    /**
     * 예외와 현재 HTTP 요청 문맥을 DB에 기록합니다.
     *
     * REQUIRES_NEW가 중요한 이유:
     * 업무 처리 중 발생한 예외 때문에 기존 트랜잭션이 rollback-only 상태가 되었더라도,
     * 오류 로그는 별도의 새 트랜잭션으로 저장해야 함께 롤백되지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Exception exception, int httpStatus, String errorCode,
            HttpServletRequest request, UserPrincipal principal) {
        try {
            // printStackTrace는 기본적으로 콘솔용 출력이므로 StringWriter를 이용해 DB 저장용 문자열로 변환합니다.
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            ErrorLog errorLog = new ErrorLog(
                httpStatus,
                truncate(errorCode, 100),
                truncate(exception.getClass().getName(), 255),
                truncate(exception.getMessage(), 2_000),
                truncate(writer.toString(), MAX_STACK_TRACE_LENGTH),
                truncate(request.getMethod(), 10),
                truncate(request.getRequestURI(), 1_000),
                // 공개 API나 인증 전 오류라면 principal이 없으므로 null을 저장합니다.
                principal == null ? null : principal.userId(),
                principal == null ? null : truncate(principal.email(), 150)
            );
            // 즉시 SQL을 실행해 트랜잭션 종료 시점이 아니라 이 블록 안에서 저장 실패를 처리합니다.
            errorLogRepository.saveAndFlush(errorLog);
        } catch (RuntimeException persistenceException) {
            // DB 장애가 원래 예외의 원인일 수도 있습니다. 로그 저장 실패를 다시 던지면
            // 예외 처리기까지 실패하므로 콘솔에만 남기고 원래 오류 응답 흐름을 유지합니다.
            log.error("Failed to persist server error log", persistenceException);
        }
    }

    /** resolved가 없으면 전체, true/false이면 처리 여부에 맞는 로그만 최신순으로 조회합니다. */
    @Transactional(readOnly = true)
    public Page<ErrorLogSummaryResponse> getLogs(Boolean resolved, int page, int size) {
        // 대량 조회로 서버가 느려지는 것을 방지하기 위해 한 번에 최대 100건만 허용합니다.
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "occurredAt"));
        Page<ErrorLog> logs = resolved == null
            ? errorLogRepository.findAll(pageable)
            : errorLogRepository.findByResolved(resolved, pageable);
        // 목록에서는 큰 stackTrace를 제외한 Summary DTO로 변환해 응답 크기를 줄입니다.
        return logs.map(ErrorLogSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public ErrorLogDetailResponse getLog(Long id) {
        // 상세 조회에서만 stackTrace를 포함합니다.
        return ErrorLogDetailResponse.from(find(id));
    }

    @Transactional
    public ErrorLogDetailResponse resolve(Long id, Long adminUserId) {
        ErrorLog errorLog = find(id);
        // 트랜잭션 안에서 엔티티 값을 변경하면 JPA 변경 감지(dirty checking)가 UPDATE SQL을 실행합니다.
        errorLog.resolve(adminUserId);
        return ErrorLogDetailResponse.from(errorLog);
    }

    private ErrorLog find(Long id) {
        return errorLogRepository.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ERROR_LOG_NOT_FOUND", "오류 로그를 찾을 수 없습니다."));
    }

    /** DB 컬럼 길이 초과 때문에 오류 로그 저장 자체가 실패하는 상황을 예방합니다. */
    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
