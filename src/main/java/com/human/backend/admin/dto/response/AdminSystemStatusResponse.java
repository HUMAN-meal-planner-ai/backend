package com.human.backend.admin.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 백엔드, DB, 가격 API, AI 서버 상태를 관리자 화면에 한 번에 전달합니다.
 * checkedAt은 브라우저 시간이 아니라 서버 기준 확인 시각이며,
 * overallStatus는 모든 서비스가 정상이면 UP, 하나라도 실패하면 DEGRADED입니다.
 */
public record AdminSystemStatusResponse(
        String overallStatus,
        Instant checkedAt,
        List<SystemServiceStatusResponse> services) {
}
