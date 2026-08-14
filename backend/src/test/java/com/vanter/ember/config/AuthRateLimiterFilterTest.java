package com.vanter.ember.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimiterFilterTest {

    private static final String CONTEXT_PATH = "/v1";

    RateLimitProperties properties;
    FilterChain chain;
    AtomicLong clock;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        chain = mock(FilterChain.class);
        clock = new AtomicLong(1_700_000_000_000L);
    }

    private AuthRateLimiterFilter newFilter() {
        // Mirrors the Boot-provided mapper, which carries the ProblemDetail Jackson mixin.
        AuthRateLimiterFilter filter =
                new AuthRateLimiterFilter(properties, Jackson2ObjectMapperBuilder.json().build());
        filter.setClock(clock::get);
        return filter;
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", CONTEXT_PATH + "/auth/login");
        request.setContextPath(CONTEXT_PATH);
        request.setRemoteAddr(ip);
        return request;
    }

    private MockHttpServletRequest loginRequest(String ip, String host) {
        MockHttpServletRequest request = loginRequest(ip);
        request.setServerName(host);
        return request;
    }

    private int statusOf(AuthRateLimiterFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, chain);
        return response.getStatus();
    }

    // --- path matching -------------------------------------------------------------------------

    @Test
    void filtersAuthPathsBehindTheServletContextPath() {
        // Regression: the filter compared getRequestURI() (`/v1/auth/login`) against `/auth/login`,
        // so with `server.servlet.context-path=/v1/` it never ran on a single real request.
        AuthRateLimiterFilter filter = newFilter();

        assertThat(filter.shouldNotFilter(loginRequest("1.2.3.4"))).isFalse();
    }

    @Test
    void filtersAuthPathsWhenNoContextPathIsConfigured() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setRemoteAddr("1.2.3.4");

        assertThat(newFilter().shouldNotFilter(request)).isFalse();
    }

    @Test
    void doesNotApplyToNonAuthPaths() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", CONTEXT_PATH + "/sessions");
        request.setContextPath(CONTEXT_PATH);
        request.setRemoteAddr("9.9.9.9");

        assertThat(newFilter().shouldNotFilter(request)).isTrue();
    }

    @Test
    void doesNotApplyWhenDisabled() {
        properties.setEnabled(false);

        assertThat(newFilter().shouldNotFilter(loginRequest("1.2.3.4"))).isTrue();
    }

    // --- window behaviour ----------------------------------------------------------------------

    @Test
    void allowsUpToTenRequestsPerMinute() throws Exception {
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            assertThat(statusOf(filter, loginRequest("1.2.3.4"))).isEqualTo(200);
        }
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void returns429ProblemDetailOnEleventhRequest() throws Exception {
        AuthRateLimiterFilter filter = newFilter();
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(loginRequest("5.6.7.8"), new MockHttpServletResponse(), chain);
        }

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("5.6.7.8"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString())
                .contains("\"status\":429")
                .contains("\"title\":\"Too Many Requests\"")
                .contains("\"instance\":\"/v1/auth/login\"");
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void allowsAgainOnceTheWindowHasElapsed() throws Exception {
        AuthRateLimiterFilter filter = newFilter();
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(loginRequest("5.6.7.8"), new MockHttpServletResponse(), chain);
        }
        assertThat(statusOf(filter, loginRequest("5.6.7.8"))).isEqualTo(429);

        clock.addAndGet(Duration.ofMinutes(1).toMillis() + 1);

        assertThat(statusOf(filter, loginRequest("5.6.7.8"))).isEqualTo(200);
    }

    @Test
    void evictsBucketsWhoseHitsHaveAllExpired() throws Exception {
        AuthRateLimiterFilter filter = newFilter();
        for (int i = 0; i < 50; i++) {
            filter.doFilterInternal(loginRequest("10.0.1." + i), new MockHttpServletResponse(), chain);
        }
        // One IP-ceiling bucket plus one tenant bucket per IP.
        assertThat(filter.trackedKeys()).isEqualTo(100);

        // A request one window later triggers the sweep; every earlier bucket is now stale.
        clock.addAndGet(Duration.ofMinutes(1).toMillis() + 1);
        filter.doFilterInternal(loginRequest("172.16.0.1"), new MockHttpServletResponse(), chain);

        assertThat(filter.trackedKeys()).isEqualTo(2);
    }

    // --- keying --------------------------------------------------------------------------------

    @Test
    void differentIpsHaveSeparateLimits() throws Exception {
        AuthRateLimiterFilter filter = newFilter();
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(loginRequest("10.0.0.1"), new MockHttpServletResponse(), chain);
        }

        assertThat(statusOf(filter, loginRequest("10.0.0.2"))).isEqualTo(200);
    }

    @Test
    void oneTenantCannotExhaustAnotherTenantsBucketOnASharedIp() throws Exception {
        properties.setTenantHostSuffixes(List.of("ember.vanter.com"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(
                    loginRequest("203.0.113.9", "acme.ember.vanter.com"),
                    new MockHttpServletResponse(), chain);
        }
        assertThat(statusOf(filter, loginRequest("203.0.113.9", "acme.ember.vanter.com"))).isEqualTo(429);

        // Same NAT egress IP, different tenant: previously this was already throttled.
        assertThat(statusOf(filter, loginRequest("203.0.113.9", "bistro.ember.vanter.com"))).isEqualTo(200);
    }

    @Test
    void hostsOutsideTheTenantSuffixShareOneBucket() throws Exception {
        properties.setTenantHostSuffixes(List.of("ember.vanter.com"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(
                    loginRequest("203.0.113.10", "attacker" + i + ".example.org"),
                    new MockHttpServletResponse(), chain);
        }

        assertThat(statusOf(filter, loginRequest("203.0.113.10", "another.example.org"))).isEqualTo(429);
    }

    @Test
    void perIpCeilingCapsForgedTenantHosts() throws Exception {
        properties.setTenantHostSuffixes(List.of("ember.vanter.com"));
        properties.setIpMaxRequests(15);
        AuthRateLimiterFilter filter = newFilter();

        // A fresh tenant bucket per request, but the IP ceiling still bites at 15.
        for (int i = 0; i < 15; i++) {
            int status = statusOf(filter, loginRequest("198.51.100.4", "t" + i + ".ember.vanter.com"));
            assertThat(status).isEqualTo(200);
        }

        assertThat(statusOf(filter, loginRequest("198.51.100.4", "t99.ember.vanter.com"))).isEqualTo(429);
    }

    @Test
    void fallsBackToTheIpCeilingOnceTheKeyCapIsReached() throws Exception {
        properties.setTenantHostSuffixes(List.of("ember.vanter.com"));
        properties.setMaxTrackedKeys(4);
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 12; i++) {
            statusOf(filter, loginRequest("198.51.100.5", "t" + i + ".ember.vanter.com"));
        }

        // The map stops growing instead of leaking one bucket per forged host.
        assertThat(filter.trackedKeys()).isLessThanOrEqualTo(4);
    }

    // --- proxy resolution ----------------------------------------------------------------------

    @Test
    void ignoresForwardedHeadersFromAnUntrustedPeer() throws Exception {
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("9.9.9.9");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest spoofed = loginRequest("9.9.9.9");
        spoofed.addHeader("X-Forwarded-For", "203.0.113.200");
        assertThat(statusOf(filter, spoofed)).isEqualTo(429);
    }

    @Test
    void usesForwardedClientIpWhenThePeerIsATrustedProxy() throws Exception {
        properties.setTrustedProxies(List.of("10.0.0.0/8"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.5");
            request.addHeader("X-Forwarded-For", "203.0.113.7");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest sameClient = loginRequest("10.0.0.5");
        sameClient.addHeader("X-Forwarded-For", "203.0.113.7");
        assertThat(statusOf(filter, sameClient)).isEqualTo(429);

        // A different client behind the same proxy is unaffected — the old code throttled them all.
        MockHttpServletRequest otherClient = loginRequest("10.0.0.5");
        otherClient.addHeader("X-Forwarded-For", "203.0.113.8");
        assertThat(statusOf(filter, otherClient)).isEqualTo(200);
    }

    @Test
    void skipsTrustedHopsAndIgnoresClientPrependedEntries() throws Exception {
        properties.setTrustedProxies(List.of("10.0.0.0/8"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.5");
            // The client forged the first entry; the real address was appended by the edge proxy.
            request.addHeader("X-Forwarded-For", "1.1.1." + i + ", 203.0.113.7, 10.0.0.9");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest request = loginRequest("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.7");
        assertThat(statusOf(filter, request)).isEqualTo(429);
    }

    @Test
    void fallsBackToThePeerWhenTheForwardedHopIsNotAnIpLiteral() throws Exception {
        properties.setTrustedProxies(List.of("10.0.0.0/8"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.5");
            request.addHeader("X-Forwarded-For", "_hidden" + i);
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest request = loginRequest("10.0.0.5");
        request.addHeader("X-Forwarded-For", "unknown");
        assertThat(statusOf(filter, request)).isEqualTo(429);
    }

    @Test
    void readsTheTenantFromForwardedHostOnlyBehindATrustedProxy() throws Exception {
        properties.setTenantHostSuffixes(List.of("ember.vanter.com"));
        properties.setTrustedProxies(List.of("10.0.0.0/8"));
        AuthRateLimiterFilter filter = newFilter();

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = loginRequest("10.0.0.5", "internal.lb.local");
            request.addHeader("X-Forwarded-For", "203.0.113.7");
            request.addHeader("X-Forwarded-Host", "acme.ember.vanter.com");
            filter.doFilterInternal(request, new MockHttpServletResponse(), chain);
        }

        MockHttpServletRequest other = loginRequest("10.0.0.5", "internal.lb.local");
        other.addHeader("X-Forwarded-For", "203.0.113.7");
        other.addHeader("X-Forwarded-Host", "bistro.ember.vanter.com");
        assertThat(statusOf(filter, other)).isEqualTo(200);
    }
}
