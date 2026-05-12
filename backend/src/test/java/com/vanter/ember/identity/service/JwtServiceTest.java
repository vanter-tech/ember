package com.vanter.ember.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    private static final String SECRET =
            "ember-secret-key-must-be-at-least-32-characters-long-for-hs256";
    private static final long EXPIRATION_MS = 3_600_000L;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, EXPIRATION_MS);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = jwtService.generateToken("user@test.com", Map.of("role", "CUSTOMER"));
        assertThat(token).isNotBlank();
    }

    @Test
    void extractSubject_returnsCorrectSubject() {
        String token = jwtService.generateToken("user@test.com", Map.of());
        assertThat(jwtService.extractSubject(token)).isEqualTo("user@test.com");
    }

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = jwtService.generateToken("user@test.com", Map.of());
        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        JwtService shortLivedService = new JwtService(SECRET, -1L);
        String token = shortLivedService.generateToken("user@test.com", Map.of());
        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = jwtService.generateToken("user@test.com", Map.of()) + "tampered";
        assertThat(jwtService.isTokenValid(token)).isFalse();
    }
}
