package com.human.backend.errorlog.controller;

import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.errorlog.dto.ErrorLogDetailResponse;
import com.human.backend.errorlog.dto.ErrorLogSummaryResponse;
import com.human.backend.errorlog.service.ErrorLogService;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 오류 로그를 조회하고 처리 상태를 변경하는 관리자 전용 API입니다.
 * 이 클래스에 권한 어노테이션이 보이지 않는 이유는 SecurityConfig에서
 * /api/admin/** 전체를 ROLE_ADMIN으로 일괄 보호하고 있기 때문입니다.
 */
@RestController
@RequestMapping("/api/admin/error-logs")
@Validated
public class AdminErrorLogController {

    private final ErrorLogService errorLogService;

    public AdminErrorLogController(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @GetMapping
    public Page<ErrorLogSummaryResponse> getLogs(
            // resolved를 생략하면 전체, false이면 미처리, true이면 처리 완료 로그를 조회합니다.
            @RequestParam(required = false) Boolean resolved,
            // 잘못된 페이지 값은 서비스까지 전달되기 전에 Bean Validation이 400으로 거절합니다.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return errorLogService.getLogs(resolved, page, size);
    }

    @GetMapping("/{id}")
    public ErrorLogDetailResponse getLog(@PathVariable Long id) {
        return errorLogService.getLog(id);
    }

    @PatchMapping("/{id}/resolve")
    public ErrorLogDetailResponse resolve(
            // JWT 필터가 인증한 현재 ADMIN의 정보입니다. 누가 처리했는지를 resolvedBy에 기록합니다.
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return errorLogService.resolve(id, principal.userId());
    }
}
