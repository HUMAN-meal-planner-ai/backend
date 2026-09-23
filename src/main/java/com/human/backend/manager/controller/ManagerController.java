package com.human.backend.manager.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.manager.dto.request.ManagerMemberStatusRequest;
import com.human.backend.manager.dto.response.ManagerMemberResponse;
import com.human.backend.manager.service.ManagerService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/manager")
public class ManagerController {

    private final ManagerService managerService;

    public ManagerController(ManagerService managerService) {
        this.managerService = managerService;
    }

    /**
     * GET /api/manager/members
     * 현재 로그인한 MANAGER와 같은 시설에 속한 구성원 목록을 반환합니다.
     * @AuthenticationPrincipal은 JWT 인증 필터가 SecurityContext에 저장한 사용자 정보입니다.
     */
    @GetMapping("/members")
    public List<ManagerMemberResponse> getMyFacilityMembers(
            @AuthenticationPrincipal UserPrincipal principal) {
        return managerService.getMyFacilityMembers(principal.userId());
    }

    /**
     * PATCH /api/manager/members/{userId}/status
     * 주소의 userId는 변경 대상이고, 요청 본문의 status는 ACTIVE 또는 DISABLED입니다.
     * 컨트롤러는 HTTP 입력을 받고 실제 시설·역할 검사는 서비스 계층에 맡깁니다.
     */
    @PatchMapping("/members/{userId}/status")
    public ManagerMemberResponse updateMemberStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long userId,
            @Valid @RequestBody ManagerMemberStatusRequest request) {
        return managerService.updateMemberStatus(principal.userId(), userId, request);
    }
}
