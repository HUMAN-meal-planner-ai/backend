package com.human.backend.auth.service;

import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.human.backend.auth.dto.request.LoginRequest;
import com.human.backend.auth.dto.request.SignupRequest;
import com.human.backend.auth.dto.response.EmailCheckResponse;
import com.human.backend.auth.dto.response.LoginResponse;
import com.human.backend.auth.dto.response.UserResponse;
import com.human.backend.auth.entity.AppUser;
import com.human.backend.auth.repository.AppUserRepository;
import com.human.backend.auth.service.JwtService.AppUserTokenData;
import com.human.backend.global.exception.ApiException;

@Service
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public EmailCheckResponse checkEmail(String rawEmail) {
        String email = normalizeEmail(rawEmail);
        return new EmailCheckResponse(email, !appUserRepository.existsByEmail(email));
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        if (appUserRepository.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }

        try {
            AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.name().trim());
            return UserResponse.from(appUserRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // 동시에 같은 이메일로 가입하는 경우 DB 고유 제약을 최종 방어선으로 사용합니다.
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        AppUser user = appUserRepository.findByEmail(normalizeEmail(request.email()))
            .orElseThrow(this::invalidCredentials);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        String token = jwtService.createToken(new AppUserTokenData(
            user.getId(), user.getEmail(), user.getRole().name()));
        return new LoginResponse(token, "Bearer", jwtService.getExpirationSeconds(), UserResponse.from(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        AppUser user = appUserRepository.findById(userId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        return UserResponse.from(user);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException invalidCredentials() {
        // 이메일 존재 여부가 노출되지 않도록 같은 메시지를 반환합니다.
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
