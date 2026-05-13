package com.vanter.ember.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class AuthRateLimiterFilterTest {

    AuthRateLimiterFilter filter;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new AuthRateLimiterFilter();
        chain = mock(FilterChain.class);
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    @Test
    void allowsUpToTenRequestsPerMinute() throws Exception {
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilterInternal(loginRequest("1.2.3.4"), res, chain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        verify(chain, times(10)).doFilter(any(), any());
    }

    @Test
    void returns429OnEleventhRequest() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(loginRequest("5.6.7.8"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("5.6.7.8"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotApplyToNonAuthPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/sessions");
        req.setRemoteAddr("9.9.9.9");
        MockHttpServletResponse res = new MockHttpServletResponse();

        assertThat(filter.shouldNotFilter(req)).isTrue();
    }

    @Test
    void differentIpsHaveSeparateLimits() throws Exception {
        for (int i = 0; i < 10; i++) {
            filter.doFilterInternal(loginRequest("10.0.0.1"), new MockHttpServletResponse(), chain);
        }
        MockHttpServletResponse ipBResponse = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("10.0.0.2"), ipBResponse, chain);

        assertThat(ipBResponse.getStatus()).isEqualTo(200);
    }
}
