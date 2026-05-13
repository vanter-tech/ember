package com.vanter.ember.session.service;

import com.vanter.ember.identity.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException; // kept for other tests if needed
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrTokenServiceTest {

    private static final String SECRET =
            "ember-secret-key-must-be-at-least-32-characters-long-for-hs256";

    private QrTokenService qrTokenService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86400000L);
        qrTokenService = new QrTokenService(jwtService);
    }

    @Test
    void generateQrToken_returnsNonEmptyJwt() {
        String token = qrTokenService.generateQrToken("sess-1", 4);

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void validateQrToken_extractsSessionId() {
        String token = qrTokenService.generateQrToken("sess-1", 4);

        assertThat(qrTokenService.validateQrToken(token)).isEqualTo("sess-1");
    }

    @Test
    void generateQrToken_embedsMaxParticipants() {
        String token = qrTokenService.generateQrToken("sess-1", 6);

        assertThat(qrTokenService.extractMaxParticipants(token)).isEqualTo(6);
    }

    @Test
    void validateQrToken_throwsWhenExpired() {
        String expiredToken = jwtService.generateToken(
                "sess-1", Map.of("maxParticipants", 4), -1000L);

        assertThatThrownBy(() -> qrTokenService.validateQrToken(expiredToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired QR token");
    }

    @Test
    void validateQrToken_throwsForTamperedToken() {
        String validToken = qrTokenService.generateQrToken("sess-1", 4);
        String tampered = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> qrTokenService.validateQrToken(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired QR token");
    }
}
