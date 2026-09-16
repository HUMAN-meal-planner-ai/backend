package com.human.backend.auth.dto.response;

import com.human.backend.auth.entity.AppUser;

public record UserResponse(
        Long userId,
        String email,
        String name,
        String role,
        Long facilityId) {

    public static UserResponse from(AppUser user) {
        Long facilityId = user.getFacility() == null ? null : user.getFacility().getId();
        return new UserResponse(user.getId(), user.getEmail(), user.getName(),
            user.getRole().name(), facilityId);
    }
}
