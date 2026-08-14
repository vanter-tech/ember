package com.vanter.ember.session.service;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrTokenService {

    private static final long QR_EXPIRY_MS = 15 * 60 * 1000L;

    private final JwtService jwtService;

    public String generateQrToken(String sessionId) {
        return jwtService.generateToken(
                sessionId,
                Map.of("rid", TenantContextHolder.requireTenantId().toString()),
                QR_EXPIRY_MS);
    }

    /**
     * A scanning customer has no restaurant bound to their token yet, so the QR token itself is
     * the authority on which tenant the session belongs to — it is server-signed at generation
     * time from the waiter's own tenant context, so its {@code rid} can't be forged.
     */
    public QrTokenData validateQrToken(String token) {
        if (!jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired QR token");
        }
        UUID tenantId = jwtService.extractTenantId(token);
        if (tenantId == null) {
            throw new IllegalArgumentException("QR token carries no restaurant");
        }
        return new QrTokenData(jwtService.extractSubject(token), tenantId);
    }

    public record QrTokenData(String sessionId, UUID tenantId) {}
}
