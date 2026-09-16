package com.human.backend.auth.service;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.human.backend.auth.entity.AppUser;

// SecurityContext에 저장하는 최소 사용자 정보입니다.
public record UserPrincipal(Long userId, String email, String name, String role) {

    public static UserPrincipal from(AppUser user) {
        // 인증 필터에서는 지연 로딩 관계를 조회하지 않고 토큰 검증에 필요한 값만 보관합니다.
        return new UserPrincipal(user.getId(), user.getEmail(), user.getName(), user.getRole().name());
    }

    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
