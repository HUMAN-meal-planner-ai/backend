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
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(facilityService.createForUser(principal.userId(), request));
    }

    @GetMapping("/me")
    public FacilityResponse getMine(@AuthenticationPrincipal UserPrincipal principal) {
        return facilityService.getMine(principal.userId());
    }

    @PatchMapping("/me")
    public FacilityResponse updateMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody FacilityRequest request) {
        return facilityService.updateMine(principal.userId(), request);
    }
}
