package com.vanter.ember.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformJwtServiceTest {

    private PlatformJwtService platformJwtService;

    private static final String SECRET =
            "ember-platform-secret-key-must-be-at-least-32-chars-for-hs256";
    private static final long EXPIRATION_MS = 3_600_000L;

    @BeforeEach
    void setUp() {
        platformJwtService = new PlatformJwtService(SECRET, EXPIRATION_MS);
    }

    @Test
    void constructor_rejectsShortSecret() {
        assertThatThrownBy(() -> new PlatformJwtService("too-short", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generateToken_returnsNonBlankToken() {
        String token = platformJwtService.generateToken(
                "operator@ember.local", Map.of("role", "PLATFORM_ADMIN"));
        assertThat(token).isNotBlank();
    }

    @Test
    void extractSubject_returnsCorrectSubject() {
        String token = platformJwtService.generateToken("operator@ember.local", Map.of());
        assertThat(platformJwtService.extractSubject(token)).isEqualTo("operator@ember.local");
    }

    @Test
    void isTokenValid_returnsTrueForFreshToken() {
        String token = platformJwtService.generateToken("operator@ember.local", Map.of());
        assertThat(platformJwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void isTokenValid_returnsFalseForExpiredToken() {
        PlatformJwtService shortLivedService = new PlatformJwtService(SECRET, -1L);
        String token = shortLivedService.generateToken("operator@ember.local", Map.of());
        assertThat(shortLivedService.isTokenValid(token)).isFalse();
    }

    @Test
    void isTokenValid_returnsFalseForTamperedToken() {
        String token = platformJwtService.generateToken("operator@ember.local", Map.of())
                + "tampered";
        assertThat(platformJwtService.isTokenValid(token)).isFalse();
    }

    @Test
    void tokenSignedByOneInstance_isInvalidUnderADifferentSecret() {
        String token = platformJwtService.generateToken("operator@ember.local", Map.of());
        PlatformJwtService otherSecretService = new PlatformJwtService(
                "a-completely-different-secret-key-at-least-32-chars", EXPIRATION_MS);

        assertThat(otherSecretService.isTokenValid(token)).isFalse();
    }
}
