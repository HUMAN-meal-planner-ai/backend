package com.human.backend.admin.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.human.backend.admin.dto.request.AdminRoleUpdateRequest;
import com.human.backend.admin.dto.response.AdminUserResponse;
import com.human.backend.admin.service.AdminUserService;
import com.human.backend.auth.service.UserPrincipal;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * GET /api/admin/users
     * ADMIN 권한으로 전체 회원과 시설 연결 상태를 조회합니다.
     * /api/admin/** 권한 검사는 이 메서드보다 먼저 SecurityConfig에서 실행됩니다.
     */
    @GetMapping
    public List<AdminUserResponse> getUsers() {
        return adminUserService.getUsers();
    }

    /**
     * PATCH /api/admin/users/{userId}/role
     * 경로의 userId는 변경 대상이고 principal.userId는 변경을 요청한 ADMIN입니다.
     * 두 ID를 서비스에 모두 전달하여 자기 자신의 ADMIN 권한 변경을 막을 수 있게 합니다.
     */
    @PatchMapping("/{userId}/role")
    public AdminUserResponse updateRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody AdminRoleUpdateRequest request) {
        return adminUserService.updateRole(principal.userId(), userId, request);
    }
}
