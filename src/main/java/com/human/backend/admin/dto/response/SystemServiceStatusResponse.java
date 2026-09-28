package com.human.backend.admin.dto.response;

/**
 * 관리자 상태 화면에서 하나의 연동 서비스 상태를 표시하기 위한 응답입니다.
 * key는 화면 목록 식별자, status는 UP/DOWN, responseTimeMs는 실제 점검 소요 시간입니다.
 * 내부 URL이나 인증정보는 응답에 포함하지 않아 관리자 화면에서도 비밀 설정이 노출되지 않습니다.
 */
public record SystemServiceStatusResponse(
        String key,
        String name,
        String status,
        String message,
        long responseTimeMs) {
}
