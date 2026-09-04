package com.vanter.ember.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Throttling policy for the unauthenticated auth endpoints, applied by
 * {@link AuthRateLimiterFilter}.
 *
 * <p>Buckets are keyed by {@code (tenant, client IP)} so a busy tenant behind a shared NAT/office
 * egress cannot exhaust another tenant's allowance. The tenant is taken from the request host's
 * leading label (a tenant subdomain, see {@link CorsProperties}) — the only tenant signal that
 * exists before a JWT is issued. Because the {@code Host} header is client-controlled, an attacker
 * could otherwise mint an unlimited number of buckets, so {@link #ipMaxRequests} keeps a per-IP
 * ceiling across all tenants on top of the per-tenant bucket.
 */
@ConfigurationProperties(prefix = "ember.ratelimit")
@Data
public class RateLimitProperties {

    /** Set false to disable throttling entirely (integration tests, single-user self-hosting). */
    private boolean enabled = true;

    /** Requests allowed per window for one {@code (tenant, IP)} pair. */
    private int maxRequests = 10;

    /**
     * Requests allowed per window for one IP across every tenant. Must be {@code >= maxRequests}
     * to be meaningful; {@code 0} disables the ceiling.
     */
    private int ipMaxRequests = 30;

    /** Sliding window the counters above apply to. */
    private Duration window = Duration.ofMinutes(1);

    /**
     * Proxies whose {@code X-Forwarded-For} / {@code X-Forwarded-Host} are believed, as literal
     * addresses or CIDR blocks. Empty by default: with no proxy in front of the app, any forwarded
     * header is attacker-supplied and honouring it would make the limiter trivially bypassable.
     */
    private List<String> trustedProxies = new ArrayList<>();

    /**
     * Host suffixes under which the leading label identifies a tenant, e.g.
     * {@code ember.vanter.com} matches {@code acme.ember.vanter.com} as tenant {@code acme}.
     * Hosts matching nothing here share one untenanted bucket.
     */
    private List<String> tenantHostSuffixes = new ArrayList<>();

    /**
     * Safety cap on tracked buckets. Once exceeded (after a sweep), new keys stop being created and
     * requests fall back to the per-IP ceiling, so the map cannot grow without bound.
     */
    private int maxTrackedKeys = 100_000;

    /**
     * Paths the limiter guards, compared after the servlet context path is stripped.
     *
     * <p>{@code /sessions/join} (QA_SIMULATION_REPORT.md E-15) takes a 5-character table code
     * looked up across every tenant with no other throttle — a customer's own auth doesn't
     * exempt it, the filter only matches on path.
     *
     * <p>{@code /printing/agents/token} (QA_SIMULATION_REPORT.md E-22, derived from the earlier
     * static audit, not re-verified live this session) is {@code permitAll} and
     * {@link com.vanter.ember.printing.service.PrintAgentService#authenticateByApiKey} scans
     * every {@code ACTIVE} print agent across the whole platform with one BCrypt comparison
     * each — an anonymous caller can burn real CPU per request with no other throttle in place.
     */
    private List<String> paths = new ArrayList<>(
            List.of("/auth/login", "/auth/login/pin", "/auth/register", "/platform/auth/login",
                    "/hub-activations", "/hub-heartbeat", "/sessions/join", "/printing/agents/token"));
}
