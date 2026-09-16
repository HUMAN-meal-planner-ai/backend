package com.human.backend.auth.dto.response;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user) {
}
