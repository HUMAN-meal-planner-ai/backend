package com.human.backend.auth.dto.response;

public record EmailCheckResponse(String email, boolean available) {
}
