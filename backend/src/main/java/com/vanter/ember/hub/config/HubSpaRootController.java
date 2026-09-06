package com.vanter.ember.hub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * {@code HubWebConfig}'s resource handler can't serve {@code index.html} for the bare {@code
 * /app}/{@code /app/} root itself: Spring's {@code ResourceHttpRequestHandler} rejects an empty
 * resource path before the custom {@code PathResourceResolver} fallback ever runs (unlike a real
 * sub-path such as {@code /app/admin/settings}, which resolves correctly). A server-side forward
 * keeps the browser's URL at {@code /app/} — required so React Router's {@code basename="/app"}
 * still sees the root route, unlike a redirect to {@code /app/index.html}.
 */
@Controller
@Profile("hub")
class HubSpaRootController {

    @GetMapping({"/app", "/app/"})
    void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/app/index.html").forward(request, response);
    }

    /**
     * The bare host ({@code http://<hub-ip>:<port>/}) is the address a LAN terminal's user
     * actually types; without this it hits {@code anyRequest().authenticated()} and returns a
     * bare 401. Send it to the SPA. {@code GET /} is {@code permitAll}ed in {@code SecurityConfig}.
     */
    @GetMapping("/")
    RedirectView root() {
        return new RedirectView("/app/");
    }
}
