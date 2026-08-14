package com.vanter.ember.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Browser-origin policy shared by the REST API ({@link CorsConfig}) and the STOMP/SockJS
 * handshake ({@link WebSocketConfig}), so a tenant that can call the API can also open a socket.
 *
 * <p>Tenants are served from per-business subdomains ({@code <businessName>.ember.vanter.com}),
 * which cannot be enumerated at build time — those belong in {@link #allowedOriginPatterns}
 * (wildcards), while fixed origins (local dev, the marketing/admin host) stay in
 * {@link #allowedOrigins}. Because credentials are allowed, the CORS spec forbids answering with
 * {@code *}: a pattern match is echoed back as the concrete origin, a bare {@code *} would not be.
 */
@ConfigurationProperties(prefix = "ember.cors")
@Data
public class CorsProperties {

    /** Exact origins, compared literally (no wildcards). */
    private List<String> allowedOrigins = new ArrayList<>(List.of(
            "http://localhost:5173",
            "http://localhost:3000"));

    /** Wildcard origin patterns, e.g. {@code https://*.ember.vanter.com} for tenant subdomains. */
    private List<String> allowedOriginPatterns = new ArrayList<>();

    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** Response headers the browser may read (e.g. a pagination or trace header). */
    private List<String> exposedHeaders = new ArrayList<>();

    private boolean allowCredentials = true;

    /** Preflight cache lifetime, in seconds. */
    private long maxAge = 3600;
}
