package com.vanter.ember.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rejects any {@code sort} query parameter that is not a plain {@code property[,asc|desc]}.
 * Controllers take a raw {@code Pageable}, so {@code ?sort=} reaches Spring Data unchecked; a
 * crafted value once bypassed its own Sort validation (CVE-2026-47834). Only identifiers are ever
 * legitimate here, so anything else is a 400 before the controller runs.
 */
@Configuration
public class SortParamGuard implements WebMvcConfigurer, HandlerInterceptor {

    private static final Pattern SORT_VALUE =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*(,(?i:asc|desc))?");

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String[] sorts = request.getParameterValues("sort");
        if (sorts != null) {
            for (String sort : sorts) {
                if (!SORT_VALUE.matcher(sort).matches()) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid sort parameter");
                    return false;
                }
            }
        }
        return true;
    }
}
