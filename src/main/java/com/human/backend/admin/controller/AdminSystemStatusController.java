package com.human.backend.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.admin.dto.response.AdminSystemStatusResponse;
import com.human.backend.admin.service.AdminSystemStatusService;

/** 핵심 서비스 상태를 조회하는 ADMIN 전용 API입니다. 권한 검사는 SecurityConfig에서 처리합니다. */
@RestController
@RequestMapping("/api/admin/system-status")
public class AdminSystemStatusController {

    private final AdminSystemStatusService adminSystemStatusService;

    public AdminSystemStatusController(AdminSystemStatusService adminSystemStatusService) {
        this.adminSystemStatusService = adminSystemStatusService;
    }

    @GetMapping
    public AdminSystemStatusResponse getSystemStatus() {
        // Controller는 HTTP 주소만 담당하고 실제 DB·외부 서비스 확인은 Service에 위임합니다.
        return adminSystemStatusService.getSystemStatus();
    }
}
