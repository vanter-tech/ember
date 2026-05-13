package com.vanter.ember.session.service;

import com.vanter.ember.identity.service.JwtService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QrTokenService {

    private static final long QR_EXPIRY_MS = 15 * 60 * 1000L;

    private final JwtService jwtService;

    public String generateQrToken(String sessionId, int maxParticipants) {
        return jwtService.generateToken(
                sessionId,
                Map.of("maxParticipants", maxParticipants),
                QR_EXPIRY_MS);
    }

    public String validateQrToken(String token) {
        return jwtService.extractSubject(token);
    }

    public int extractMaxParticipants(String token) {
        return jwtService.extractClaim(token,
                claims -> claims.get("maxParticipants", Integer.class));
    }
}
