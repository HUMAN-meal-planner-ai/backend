package com.human.backend.admin.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;

public record AdminPriceMappingReviewRequest(
        @NotBlank @Pattern(regexp = "APPROVED|REJECTED") String reviewStatus) {
}

