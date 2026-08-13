package com.vanter.ember.session.service;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QrTokenServiceTest {

    private static final String SECRET =
            "ember-secret-key-must-be-at-least-32-characters-long-for-hs256";
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID OTHER_TENANT_ID = UUID.randomUUID();

    private QrTokenService qrTokenService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 86400000L);
        qrTokenService = new QrTokenService(jwtService);
        TenantContextHolder.setTenantId(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void generateQrToken_returnsNonEmptyJwt() {
        String token = qrTokenService.generateQrToken("sess-1");

        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    void validateQrToken_extractsSessionId() {
        String token = qrTokenService.generateQrToken("sess-1");

        assertThat(qrTokenService.validateQrToken(token)).isEqualTo("sess-1");
    }

    @Test
    void generateQrToken_embedsCurrentTenant() {
        String token = qrTokenService.generateQrToken("sess-1");

        assertThat(jwtService.extractTenantId(token)).isEqualTo(TENANT_ID);
    }

    @Test
    void validateQrToken_throwsWhenTokenBelongsToAnotherTenant() {
        String foreignToken = qrTokenService.generateQrToken("sess-1");
        TenantContextHolder.setTenantId(OTHER_TENANT_ID);

        assertThatThrownBy(() -> qrTokenService.validateQrToken(foreignToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to this restaurant");
    }

    @Test
    void validateQrToken_throwsWhenTokenCarriesNoTenant() {
        String untenantedToken = jwtService.generateToken("sess-1", Map.of(), 60_000L);

        assertThatThrownBy(() -> qrTokenService.validateQrToken(untenantedToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to this restaurant");
    }

    @Test
    void validateQrToken_throwsWhenExpired() {
        String expiredToken = jwtService.generateToken(
                "sess-1", Map.of("rid", TENANT_ID.toString()), -1000L);

        assertThatThrownBy(() -> qrTokenService.validateQrToken(expiredToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired QR token");
    }

    @Test
    void validateQrToken_throwsForTamperedToken() {
        String validToken = qrTokenService.generateQrToken("sess-1");
        String tampered = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> qrTokenService.validateQrToken(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired QR token");
    }
}
