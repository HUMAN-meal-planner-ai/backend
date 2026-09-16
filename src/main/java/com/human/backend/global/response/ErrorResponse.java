package com.human.backend.global.response;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> fieldErrors) {
}
