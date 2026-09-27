package com.human.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;

import com.human.backend.errorlog.entity.ErrorLog;
import com.human.backend.errorlog.repository.ErrorLogRepository;
import com.human.backend.errorlog.service.ErrorLogService;

@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class AdminErrorLogIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ErrorLogService errorLogService;

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void clearLogs() {
        errorLogRepository.deleteAll();
    }

    @Test
    void serverErrorIsPersistedWithoutRequestBodyOrAuthorizationHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/meal-plans");
        request.addHeader("Authorization", "Bearer secret-token");
        request.setContent("{\"password\":\"secret\"}".getBytes());

        errorLogService.record(new IllegalStateException("calculation failed"), 500,
            "INTERNAL_ERROR", request, null);

        ErrorLog saved = errorLogRepository.findAll().get(0);
        assertThat(saved.getRequestMethod()).isEqualTo("POST");
        assertThat(saved.getRequestPath()).isEqualTo("/api/meal-plans");
        assertThat(saved.getStackTrace()).contains("IllegalStateException");
        assertThat(saved.getStackTrace()).doesNotContain("secret-token", "password");
    }

    @Test
    void onlyAdminCanReadErrorLogs() throws Exception {
        errorLogRepository.save(new ErrorLog(500, "INTERNAL_ERROR", "java.lang.RuntimeException",
            "failure", "stack trace", "GET", "/api/test", null, null));

        mockMvc.perform(get("/api/admin/error-logs").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].errorCode").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.content[0].stackTrace").doesNotExist());

        mockMvc.perform(get("/api/admin/error-logs").with(user("member").roles("USER")))
            .andExpect(status().isForbidden());
    }
}
