package com.human.backend;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** 관리자 상태 API의 권한과 외부 서비스 장애 격리 동작을 검증합니다. */
@SpringBootTest(properties = {
    // 실제 외부 서버를 호출하지 않고 즉시 연결 실패하는 주소를 사용해 장애 격리를 재현합니다.
    "kamis.api.url=http://127.0.0.1:1",
    "ai.server.url=http://127.0.0.1:1",
    "admin.health.request-timeout=100ms"
})
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AdminSystemStatusIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void adminReceivesDegradedStatusWhenExternalServicesAreUnavailable() throws Exception {
        // 외부 서비스 두 곳이 꺼져 있어도 500이 아닌 정상 응답과 DEGRADED 상태가 반환되어야 합니다.
        mockMvc.perform(get("/api/admin/system-status").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overallStatus").value("DEGRADED"))
            .andExpect(jsonPath("$.services.length()").value(4))
            .andExpect(jsonPath("$.services[0].key").value("backend"))
            .andExpect(jsonPath("$.services[1].key").value("database"))
            .andExpect(jsonPath("$.services[2].status").value("DOWN"))
            .andExpect(jsonPath("$.services[3].status").value("DOWN"));
    }

    @Test
    void ordinaryUserCannotReadSystemStatus() throws Exception {
        // 상태 정보에는 운영 구조가 포함되므로 ROLE_ADMIN 이외 사용자의 접근을 차단합니다.
        mockMvc.perform(get("/api/admin/system-status").with(user("member").roles("USER")))
            .andExpect(status().isForbidden());
    }
}
