package com.human.backend.admin.dto.request;

import com.human.backend.auth.entity.AppUser;

import jakarta.validation.constraints.NotNull;

/**
 * ADMIN이 일반 사용자와 시설 관리자 역할을 전환할 때 받는 요청 DTO입니다.
 * enum 변환으로 존재하지 않는 역할 문자열을 걸러내고, 실제 ADMIN 허용 여부는 서비스에서 추가 검증합니다.
 */
public record AdminRoleUpdateRequest(@NotNull AppUser.Role role) {
}
