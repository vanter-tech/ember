package com.vanter.ember.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sliding-window throttle for the unauthenticated auth endpoints, configured by
 * {@link RateLimitProperties}.
 *
 * <p>Two counters guard every request:
 * <ol>
 *   <li>a per-IP ceiling ({@code ipMaxRequests}) spanning all tenants, and</li>
 *   <li>a per-{@code (tenant, IP)} bucket ({@code maxRequests}).</li>
 * </ol>
 * The tenant dimension exists so a busy tenant behind a shared egress IP cannot consume another
 * tenant's allowance; the IP ceiling exists because the tenant is read from the client-controlled
 * host, which would otherwise let a caller mint a fresh bucket per forged {@code Host}.
 *
 * <p>Entries are evicted by a sweep that runs at most once per window, and every mutation goes
 * through {@link ConcurrentHashMap#compute} so a bucket's pruning, counting and removal are atomic
 * with respect to each other.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class AuthRateLimiterFilter extends OncePerRequestFilter {

    private static final String NO_TENANT = "-";
    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String FORWARDED_HOST = "X-Forwarded-Host";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP";

    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;
    private final long windowMs;
    private final Set<String> guardedPaths;
    private final List<String> tenantHostSuffixes;
    private final List<IpAddressMatcher> trustedProxies;

    private final ConcurrentHashMap<String, Window> requestLog = new ConcurrentHashMap<>();
    private final AtomicLong nextSweepAt = new AtomicLong();

    /** Overridable in tests so window expiry can be exercised without sleeping. */
    private LongSupplier clock = System::currentTimeMillis;

    public AuthRateLimiterFilter(RateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.windowMs = Math.max(1L, properties.getWindow().toMillis());
        this.guardedPaths = new LinkedHashSet<>(properties.getPaths());
        this.tenantHostSuffixes = properties.getTenantHostSuffixes().stream()
                .filter(suffix -> suffix != null && !suffix.isBlank())
                .map(suffix -> suffix.trim().toLowerCase(Locale.ROOT))
                .map(suffix -> suffix.startsWith(".") ? suffix : "." + suffix)
                .toList();
        this.trustedProxies = new ArrayList<>();
        for (String proxy : properties.getTrustedProxies()) {
            if (proxy != null && !proxy.isBlank()) {
                // Fails fast at startup on a malformed CIDR rather than silently trusting nothing.
                this.trustedProxies.add(new IpAddressMatcher(proxy.trim()));
            }
        }
    }

    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    /** Live bucket count — a test seam for asserting that expired buckets are actually evicted. */
    int trackedKeys() {
        return requestLog.size();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled() || !guardedPaths.contains(pathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long now = clock.getAsLong();
        sweepIfDue(now);

        String peer = request.getRemoteAddr();
        boolean trustedPeer = isTrustedProxy(peer);
        String clientIp = resolveClientIp(request, peer, trustedPeer);

        if (!tryConsume("i:" + clientIp, now, properties.getIpMaxRequests())) {
            reject(request, response);
            return;
        }
        String tenantKey = "t:" + resolveTenant(request, trustedPeer) + "|" + clientIp;
        if (canTrack(tenantKey, now) && !tryConsume(tenantKey, now, properties.getMaxRequests())) {
            reject(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * Records a hit against {@code key} and reports whether it fit inside the window. A
     * non-positive {@code limit} disables that counter entirely (nothing is tracked).
     */
    private boolean tryConsume(String key, long now, int limit) {
        if (limit <= 0) {
            return true;
        }
        boolean[] allowed = {true};
        requestLog.compute(key, (ignored, window) -> {
            Window current = window == null ? new Window() : window;
            current.prune(now - windowMs);
            allowed[0] = current.hits.size() < limit;
            if (allowed[0]) {
                current.hits.addLast(now);
            }
            return current;
        });
        return allowed[0];
    }

    /**
     * Guards the tracked-key cap. Once the map is full the tenant bucket is skipped rather than
     * created, degrading to the per-IP ceiling instead of growing without bound.
     */
    private boolean canTrack(String key, long now) {
        int cap = properties.getMaxTrackedKeys();
        if (cap <= 0 || requestLog.size() < cap || requestLog.containsKey(key)) {
            return true;
        }
        sweepIfDue(now);
        return requestLog.size() < cap || requestLog.containsKey(key);
    }

    /** Drops buckets whose every hit has aged out; CAS-guarded to one pass per window. */
    private void sweepIfDue(long now) {
        long due = nextSweepAt.get();
        if (now < due || !nextSweepAt.compareAndSet(due, now + windowMs)) {
            return;
        }
        long cutoff = now - windowMs;
        for (String key : requestLog.keySet()) {
            requestLog.computeIfPresent(key, (ignored, window) -> {
                window.prune(cutoff);
                return window.hits.isEmpty() ? null : window;
            });
        }
    }

    /**
     * The peer address, unless it is a configured proxy — then {@code CF-Connecting-IP} when it
     * carries an IP literal (Cloudflare sets it to the true client and strips any client-supplied
     * value at the edge), else the rightmost {@code X-Forwarded-For} hop that is not itself a
     * trusted proxy, i.e. the address the outermost trusted proxy actually observed. Anything a
     * client prepends sits further left and is never reached.
     */
    private String resolveClientIp(HttpServletRequest request, String peer, boolean trustedPeer) {
        if (!trustedPeer) {
            return peer;
        }
        String cfHeader = request.getHeader(CF_CONNECTING_IP);
        if (cfHeader != null && !cfHeader.isBlank()) {
            String cfIp = normalizeIp(cfHeader);
            if (isIpLiteral(cfIp)) {
                return cfIp;
            }
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        String[] hops = forwarded.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = normalizeIp(hops[i]);
            if (hop.isEmpty()) {
                continue;
            }
            if (!isIpLiteral(hop)) {
                // Obfuscated or malformed hop: nothing further left can be believed.
                return peer;
            }
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return peer;
    }

    /**
     * The leading host label when the host sits under a configured tenant suffix, else
     * {@link #NO_TENANT}. {@code X-Forwarded-Host} is consulted only behind a trusted proxy.
     */
    private String resolveTenant(HttpServletRequest request, boolean trustedPeer) {
        String host = null;
        if (trustedPeer) {
            String forwardedHost = request.getHeader(FORWARDED_HOST);
            if (forwardedHost != null && !forwardedHost.isBlank()) {
                host = forwardedHost.split(",")[0].trim();
            }
        }
        if (host == null || host.isEmpty()) {
            host = request.getServerName();
        }
        if (host == null || host.isEmpty()) {
            return NO_TENANT;
        }
        host = stripPort(host.trim().toLowerCase(Locale.ROOT));
        for (String suffix : tenantHostSuffixes) {
            if (host.length() > suffix.length() && host.endsWith(suffix)) {
                String label = host.substring(0, host.length() - suffix.length());
                // A DNS label maxes out at 63 chars and holds no dot; anything else is not a tenant.
                if (!label.isEmpty() && label.length() <= 63 && label.indexOf('.') < 0) {
                    return label;
                }
            }
        }
        return NO_TENANT;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || !isIpLiteral(ip)) {
            return false;
        }
        for (IpAddressMatcher matcher : trustedProxies) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException ex) {
                // Address family mismatch against this matcher; keep checking the rest.
            }
        }
        return false;
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long retryAfterSeconds = Math.max(1L, windowMs / 1000L);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Too many authentication attempts. Retry in " + retryAfterSeconds + " seconds.");
        problem.setTitle(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        objectMapper.writeValue(response.getWriter(), problem);
    }

    /** The request path with the servlet context path (e.g. {@code /v1}) removed. */
    static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String context = request.getContextPath();
        String path = context != null && !context.isEmpty() && uri.startsWith(context)
                ? uri.substring(context.length())
                : uri;
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** Drops a trailing {@code :port} from a host, leaving a bracketed IPv6 literal intact. */
    private static String stripPort(String host) {
        if (host.startsWith("[")) {
            int end = host.indexOf(']');
            return end > 0 ? host.substring(0, end + 1) : host;
        }
        int colon = host.indexOf(':');
        if (colon > 0 && host.indexOf(':', colon + 1) < 0) {
            return host.substring(0, colon);
        }
        return host;
    }

    /** Trims a trailing {@code :port} and IPv6 brackets from {@code [::1]:8080} / {@code 1.2.3.4:80}. */
    private static String normalizeIp(String value) {
        String ip = value.trim();
        if (ip.startsWith("[")) {
            int end = ip.indexOf(']');
            return end > 0 ? ip.substring(1, end) : ip;
        }
        int colon = ip.indexOf(':');
        if (colon > 0 && ip.indexOf(':', colon + 1) < 0) {
            return ip.substring(0, colon);
        }
        return ip;
    }

    /**
     * Rejects anything that is not an IP literal, so a hostname in a forwarded header can never
     * reach {@link IpAddressMatcher} and trigger a DNS lookup on attacker-supplied input.
     */
    private static boolean isIpLiteral(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean allowed = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || c == '.' || c == ':';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /** Sliding window of hit timestamps for one bucket; only touched under the map's bin lock. */
    private static final class Window {
        private final Deque<Long> hits = new ArrayDeque<>();

        void prune(long cutoff) {
            while (!hits.isEmpty() && hits.peekFirst() <= cutoff) {
                hits.pollFirst();
            }
        }
    }
}
