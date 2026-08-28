package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GracePeriodInterceptorTest {

    @Test
    void preHandle_allowsRequestWithinGracePeriod() throws Exception {
        HubState fresh = new HubState("fp", UUID.randomUUID(), Instant.now());
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(fresh));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(fresh)).thenReturn(true);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        boolean allowed = interceptor.preHandle(
                mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandle_blocksRequestPastGracePeriod() throws Exception {
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(stale));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(stale)).thenReturn(false);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        boolean allowed = interceptor.preHandle(mock(HttpServletRequest.class), response, new Object());

        assertThat(allowed).isFalse();
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.toString()).contains("license_grace_period_expired");
    }

    @Test
    void preHandle_suspendedGraceExpired_blocksWithSuspendedMessage() throws Exception {
        HubState suspendedLongAgo = new HubState("fp", UUID.randomUUID(), Instant.now(),
                Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(suspendedLongAgo));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(suspendedLongAgo)).thenReturn(true);
        when(licenseService.isSuspendedGraceExpired(suspendedLongAgo)).thenReturn(true);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        boolean allowed = interceptor.preHandle(mock(HttpServletRequest.class), response, new Object());

        assertThat(allowed).isFalse();
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.toString()).contains("license_suspended");
    }

    @Test
    void preHandle_notSuspendedAndWithinGrace_proceeds() throws Exception {
        HubState healthy = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(healthy));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(healthy)).thenReturn(true);
        when(licenseService.isSuspendedGraceExpired(healthy)).thenReturn(false);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        boolean allowed = interceptor.preHandle(
                mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

        assertThat(allowed).isTrue();
    }
}
