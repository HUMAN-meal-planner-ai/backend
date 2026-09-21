package com.human.backend.admin.dto.response;

import java.time.Instant;

import com.human.backend.auth.entity.AppUser;
import com.human.backend.facility.entity.Facility;

/**
 * 관리자 회원 목록에 필요한 공개 정보만 전달하는 응답 DTO입니다.
 * passwordHash는 보안상 절대 응답에 포함하지 않습니다.
 */
public record AdminUserResponse(
        Long userId,
        String email,
        String name,
        String role,
        String status,
        Long facilityId,
        String facilityName,
        String facilityType,
        Instant createdAt) {

    public static AdminUserResponse from(AppUser user) {
        Facility facility = user.getFacility();
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getName(),
            user.getRole().name(),
            user.getStatus().name(),
            facility == null ? null : facility.getId(),
            facility == null ? null : facility.getName(),
            facility == null ? null : facility.getFacilityType(),
            user.getCreatedAt()
        );
    }
}
