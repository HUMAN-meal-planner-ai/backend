package com.human.backend.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.human.backend.admin.dto.response.AdminPriceDataStatusResponse;
import com.human.backend.admin.service.AdminPriceDataService;

/**
 * 관리자 전용 가격 데이터 조회 API입니다.
 *
 * 이 컨트롤러는 화면에 필요한 값을 직접 계산하지 않고 서비스의 집계 결과만 반환합니다.
 * 인증과 ADMIN 권한 검사는 공통 SecurityConfig의 /api/admin/** 규칙에서 먼저 처리됩니다.
 */
@RestController
@RequestMapping("/api/admin/price-data")
public class AdminPriceDataController {

    private final AdminPriceDataService adminPriceDataService;

    public AdminPriceDataController(AdminPriceDataService adminPriceDataService) {
        this.adminPriceDataService = adminPriceDataService;
    }

    @GetMapping
    public AdminPriceDataStatusResponse getPriceDataStatus() {
        // GET 요청이므로 DB 데이터를 변경하지 않으며, 현재 저장 상태를 매번 새로 집계합니다.
        return adminPriceDataService.getStatus();
    }
}
