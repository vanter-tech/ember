package com.vanter.ember.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class LoggingFilterTest {

    LoggingFilter filter;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new LoggingFilter();
        chain = mock(FilterChain.class);
        MDC.clear();
    }

    @Test
    void setsXTraceIdResponseHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(res.getHeader("X-Trace-Id")).isNotBlank();
    }

    @Test
    void traceIdIsValidUUID() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        String traceId = res.getHeader("X-Trace-Id");
        assertThat(traceId).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/sessions");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilterInternal(req, res, chain);

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    void eachRequestGetsDifferentTraceId() throws Exception {
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        MockHttpServletResponse res2 = new MockHttpServletResponse();

        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/sessions"), res1, chain);
        filter.doFilterInternal(new MockHttpServletRequest("GET", "/api/sessions"), res2, chain);

        assertThat(res1.getHeader("X-Trace-Id"))
                .isNotEqualTo(res2.getHeader("X-Trace-Id"));
    }
}
