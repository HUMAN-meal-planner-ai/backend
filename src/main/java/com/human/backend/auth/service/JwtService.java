package com.human.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class JwtService {

    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-seconds}") long expirationSeconds) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String createToken(AppUserTokenData user) {
        long issuedAt = Instant.now().getEpochSecond();
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", user.email());
        payload.put("uid", user.userId());
        payload.put("role", user.role());
        payload.put("iat", issuedAt);
        payload.put("exp", issuedAt + expirationSeconds);

        String unsigned = encodeJson(header) + "." + encodeJson(payload);
        return unsigned + "." + BASE64_URL_ENCODER.encodeToString(sign(unsigned));
    }

    public String validateAndGetSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Malformed JWT");
        }

        byte[] expected = sign(parts[0] + "." + parts[1]);
        byte[] provided = BASE64_URL_DECODER.decode(parts[2]);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                BASE64_URL_DECODER.decode(parts[1]), new TypeReference<>() {});
            long expiresAt = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= expiresAt) {
                throw new IllegalArgumentException("Expired JWT");
            }
            return String.valueOf(payload.get("sub"));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JWT payload", exception);
        }
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private String encodeJson(Object value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create JWT", exception);
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }

    public record AppUserTokenData(Long userId, String email, String role) {
    }
}
