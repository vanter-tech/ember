package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadOnlyModeInterceptorTest {

    private final LicenseService licenseService = mock(LicenseService.class);
    private final HubStateStore stateStore = mock(HubStateStore.class);
    private final ReadOnlyModeInterceptor interceptor = new ReadOnlyModeInterceptor(licenseService, stateStore);
    private final HubState migrated =
            new HubState("fp", UUID.randomUUID(), Instant.now(), null, null, Instant.now().minusSeconds(3 * 86400));

    private boolean handle(String method, StringWriter body, HttpServletResponse response) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        return interceptor.preHandle(request, response, new Object());
    }

    @Test
    void afterTheCourtesy_everyWriteIsRefused_withAClearMessage() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(true);

        for (String method : new String[] {"POST", "PUT", "PATCH", "DELETE"}) {
            HttpServletResponse response = mock(HttpServletResponse.class);
            StringWriter body = new StringWriter();

            assertThat(handle(method, body, response)).as(method).isFalse();

            verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
            assertThat(body.toString()).contains("license_migrated").contains("modo consulta");
        }
    }

    @Test
    void reads_areNeverBlocked() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(true);

        for (String method : new String[] {"GET", "HEAD", "OPTIONS"}) {
            assertThat(handle(method, new StringWriter(), mock(HttpServletResponse.class))).as(method).isTrue();
        }
    }

    @Test
    void duringTheCourtesy_writesStillWork() throws Exception {
        when(stateStore.load()).thenReturn(Optional.of(migrated));
        when(licenseService.isMigratedGraceExpired(migrated)).thenReturn(false);

        assertThat(handle("POST", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }

    @Test
    void aHubThatWasNeverMigrated_isUntouched() throws Exception {
        HubState healthy = new HubState("fp", UUID.randomUUID(), Instant.now());
        when(stateStore.load()).thenReturn(Optional.of(healthy));
        when(licenseService.isMigratedGraceExpired(healthy)).thenReturn(false);

        assertThat(handle("DELETE", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }

    @Test
    void withNoStateFile_theInterceptorLeavesItToTheOtherGuards() throws Exception {
        when(stateStore.load()).thenReturn(Optional.empty());

        assertThat(handle("POST", new StringWriter(), mock(HttpServletResponse.class))).isTrue();
    }
}
