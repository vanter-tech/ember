package com.vanter.ember.session.service;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.identity.service.JwtService;
import java.util.Map;
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

    public String validateQrToken(String token) {
        if (!jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Invalid or expired QR token");
        }
        if (!TenantContextHolder.requireTenantId().equals(jwtService.extractTenantId(token))) {
            throw new IllegalArgumentException("QR token does not belong to this restaurant");
        }
        return jwtService.extractSubject(token);
    }
}
