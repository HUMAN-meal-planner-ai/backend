package com.human.backend.manager.dto.response;

import com.human.backend.auth.entity.AppUser;

/**
 * 시설 관리자에게 공개해도 되는 자기 시설 구성원 정보입니다.
 * Entity를 직접 반환하면 passwordHash나 내부 연관관계까지 JSON에 섞일 수 있으므로 DTO로 필요한 값만 제한합니다.
 */
public record ManagerMemberResponse(
        Long userId,
        String email,
        String name,
        String role,
        String status) {

    /** AppUser Entity를 API 응답 형식으로 바꾸는 공통 변환 메서드입니다. */
    public static ManagerMemberResponse from(AppUser user) {
        return new ManagerMemberResponse(
            user.getId(), user.getEmail(), user.getName(),
            user.getRole().name(), user.getStatus().name());
    }
}
