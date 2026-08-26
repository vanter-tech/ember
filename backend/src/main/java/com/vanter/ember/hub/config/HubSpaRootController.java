package com.vanter.ember.hub.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

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
}
