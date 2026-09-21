package com.human.backend.manager.dto.request;

import com.human.backend.auth.entity.AppUser;

import jakarta.validation.constraints.NotNull;

/**
 * 시설 관리자가 자기 시설 일반 사용자의 계정 상태를 변경할 때 받는 요청 DTO입니다.
 * enum 타입을 사용하므로 ACTIVE, DISABLED 이외의 문자열은 정상 요청으로 처리되지 않습니다.
 * @NotNull은 status 필드 자체를 생략한 요청을 400 Bad Request로 차단합니다.
 */
public record ManagerMemberStatusRequest(@NotNull AppUser.Status status) {
}
