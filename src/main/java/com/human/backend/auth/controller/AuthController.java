package com.human.backend.auth.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.human.backend.auth.dto.request.LoginRequest;
import com.human.backend.auth.dto.request.SignupRequest;
import com.human.backend.auth.dto.response.EmailCheckResponse;
import com.human.backend.auth.dto.response.LoginResponse;
import com.human.backend.auth.dto.response.UserResponse;
import com.human.backend.auth.service.AuthService;
import com.human.backend.auth.service.UserPrincipal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/email-check")
    public EmailCheckResponse checkEmail(@RequestParam @NotBlank @Email String email) {
        return authService.checkEmail(email);
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.getMe(principal.userId());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // 현재 JWT는 무상태 방식입니다. 클라이언트가 저장한 토큰을 삭제하면 로그아웃됩니다.
        return ResponseEntity.noContent().build();
    }
}
