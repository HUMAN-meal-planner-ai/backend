package com.human.backend.facility.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.facility.dto.request.FacilityRequest;
import com.human.backend.facility.dto.response.FacilityResponse;
import com.human.backend.facility.service.FacilityService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @PostMapping
    public ResponseEntity<FacilityResponse> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FacilityRequest request) {
        // 시설 ID를 요청에서 받지 않고 JWT 사용자에게 새 시설을 연결해 다른 계정에 임의 배정하는 것을 막습니다.
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(facilityService.createForUser(principal.userId(), request));
    }

    /** 로그인 사용자가 소속된 시설의 급식 운영 기준을 조회합니다. */
    @GetMapping("/me")
    public FacilityResponse getMine(@AuthenticationPrincipal UserPrincipal principal) {
        return facilityService.getMine(principal.userId());
    }

    /**
     * 시설명, 끼니별 식수, 목표 식재료비를 한 번에 수정합니다.
     * SecurityConfig에서 MANAGER만 허용하고 서비스는 JWT 사용자에게 연결된 시설만 갱신합니다.
     */
    @PatchMapping("/me")
    public FacilityResponse updateMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FacilityRequest request) {
        return facilityService.updateMine(principal.userId(), request);
    }
}
