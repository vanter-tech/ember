package com.vanter.ember.platform.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints and verifies platform-operator tokens, signed with a key entirely separate from
 * {@code jwt.secret} ({@link com.vanter.ember.identity.service.JwtService}). This is the source of
 * the mutual exclusion between platform and tenant auth (EMB-PC-04's filter chains never touch
 * {@link com.vanter.ember.config.TenantContextHolder}): a token signed with one key simply fails
 * verification under the other's key, so there is no claim to check.
 */
@Service
public class PlatformJwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public PlatformJwtService(
            @Value("${platform.jwt.secret}") String secret,
            @Value("${platform.jwt.expiration-ms}") long expirationMs) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException(
                    "platform.jwt.secret must be at least 32 characters long for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subject, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claims(extraClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey)
                .compact();
    }

    public String extractSubject(String token) {
        return extractAllClaims(token).getSubject();
    }

    public <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
