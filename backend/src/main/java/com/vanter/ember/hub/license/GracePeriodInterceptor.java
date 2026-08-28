package com.vanter.ember.hub.license;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks order/payment-creating requests once the license grace period (spec §2.8) has lapsed —
 * read-only endpoints are untouched, and no local data is ever deleted; this only stops NEW
 * writes until the Hub reconnects and a heartbeat succeeds again.
 */
public class GracePeriodInterceptor implements HandlerInterceptor {

    private final LicenseService licenseService;
    private final HubStateStore stateStore;

    public GracePeriodInterceptor(LicenseService licenseService, HubStateStore stateStore) {
        this.licenseService = licenseService;
        this.stateStore = stateStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HubState state = stateStore.load()
                .orElseThrow(() -> new IllegalStateException(
                        "hub-state.json missing after startup license validation"));

        if (licenseService.isSuspendedGraceExpired(state)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"license_suspended\","
                            + "\"message\":\"La licencia de Ember Hub está suspendida. "
                            + "Contacta a Vanter para reactivarla.\"}");
            return false;
        }

        if (licenseService.isWithinGracePeriod(state)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"license_grace_period_expired\","
                        + "\"message\":\"La licencia de Ember Hub no ha podido validarse con la nube "
                        + "en más de 4 días. Verifica tu conexión a internet.\"}");
        return false;
    }
}
