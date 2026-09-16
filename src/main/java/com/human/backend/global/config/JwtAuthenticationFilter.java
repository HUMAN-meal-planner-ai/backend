package com.human.backend.global.config;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.human.backend.auth.service.JwtService;
import com.human.backend.auth.service.UserPrincipal;
import com.human.backend.auth.repository.AppUserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserRepository appUserRepository;

    public JwtAuthenticationFilter(JwtService jwtService, AppUserRepository appUserRepository) {
        this.jwtService = jwtService;
        this.appUserRepository = appUserRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Bearer 토큰이 없는 공개 요청은 그대로 다음 필터로 전달합니다.
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String email = jwtService.validateAndGetSubject(authorization.substring(7));
            appUserRepository.findByEmail(email)
                .filter(user -> user.isActive())
                .ifPresent(user -> {
                    UserPrincipal principal = UserPrincipal.from(user);
                    var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, principal.authorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                });
        } catch (RuntimeException ignored) {
            // 잘못되거나 만료된 토큰은 인증되지 않은 요청으로 처리합니다.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
