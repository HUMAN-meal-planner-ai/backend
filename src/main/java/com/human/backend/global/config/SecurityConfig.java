package com.human.backend.global.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> {})
            .csrf(csrf -> csrf.disable())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            // JWT 인증은 서버 세션을 사용하지 않습니다.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exception -> exception.authenticationEntryPoint(
                (request, response, authenticationException) ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // 상태 확인, Swagger 문서, 공개 가격 조회는 로그인 토큰 없이 사용할 수 있습니다.
                .requestMatchers("/api/health/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/api/prices/**"
                ).permitAll()
                // 이메일 중복 확인과 회원가입·로그인도 인증 전에 사용하는 공개 API입니다.
                .requestMatchers(HttpMethod.GET, "/api/auth/email-check").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                // /api/admin 하위 주소는 ROLE_ADMIN 권한이 있어야 Controller까지 요청이 전달됩니다.
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 시설 구성원 관리는 ROLE_MANAGER 전용이며 ADMIN도 자동으로 통과하지 않습니다.
                // ADMIN은 전체 서비스, MANAGER는 자기 시설이라는 책임 범위를 분리하기 위한 설정입니다.
                .requestMatchers("/api/manager/**").hasRole("MANAGER")
                // 시설 생성·조회는 기존 가입 흐름에서 사용하지만, 등록된 시설의 수정은 MANAGER만 가능합니다.
                .requestMatchers(HttpMethod.PATCH, "/api/facilities/me").hasRole("MANAGER")
                // 위에서 별도로 지정하지 않은 나머지 API는 역할과 관계없이 로그인된 사용자에게 허용합니다.
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 비밀번호 원문 대신 BCrypt 해시만 DB에 저장합니다.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
