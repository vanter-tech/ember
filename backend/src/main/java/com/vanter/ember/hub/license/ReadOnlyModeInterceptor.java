package com.vanter.ember.hub.license;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * "Modo consulta": once the restaurant has moved to Ember Web and the courtesy window has passed,
 * this Hub keeps answering reads (history, the Excel export) but refuses every write. Nothing local
 * is ever deleted. Registered in {@code HubWebConfig} for every path except login, the SPA and the
 * health endpoints.
 */
public class ReadOnlyModeInterceptor implements HandlerInterceptor {

    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final LicenseService licenseService;
    private final HubStateStore stateStore;

    public ReadOnlyModeInterceptor(LicenseService licenseService, HubStateStore stateStore) {
        this.licenseService = licenseService;
        this.stateStore = stateStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!WRITE_METHODS.contains(request.getMethod())) {
            return true;
        }
        Optional<HubState> state = stateStore.load();
        if (state.isEmpty() || !licenseService.isMigratedGraceExpired(state.get())) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"license_migrated\","
                        + "\"message\":\"Tu restaurante ahora usa Ember Web. "
                        + "Este Hub está en modo consulta.\"}");
        return false;
    }
}
