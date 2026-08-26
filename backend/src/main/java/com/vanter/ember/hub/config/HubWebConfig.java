package com.vanter.ember.hub.config;

import com.vanter.ember.hub.license.GracePeriodInterceptor;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

@Configuration
@Profile("hub")
public class HubWebConfig implements WebMvcConfigurer {

    private final GracePeriodInterceptor gracePeriodInterceptor;

    public HubWebConfig(LicenseService licenseService, HubStateStore stateStore) {
        this.gracePeriodInterceptor = new GracePeriodInterceptor(licenseService, stateStore);
    }

    @Bean
    public GracePeriodInterceptor gracePeriodInterceptor() {
        return gracePeriodInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gracePeriodInterceptor)
                .addPathPatterns("/sessions/*/items", "/sessions/*/participants/*/confirm", "/billing/**");
    }

    /**
     * Serves the bundled React SPA (see {@code ember-hub/build-frontend.ps1}, built with {@code
     * vite build --base=/app/}) from {@code classpath:/static/}, falling back to {@code
     * index.html} for any path under {@code /app/} with no matching file — client-side routes
     * (React Router) resolve correctly on a direct navigation or refresh. Mounted at {@code /app}
     * specifically, NOT the root — {@code /kitchen/orders} (and others) are both a real protected
     * API endpoint AND a frontend route, so root-mounting would force choosing between exposing
     * the API unauthenticated or breaking the frontend route; {@code /app} never collides with any
     * {@code @RequestMapping} (see {@code SecurityConfig}'s matching {@code permitAll}).
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.isEmpty()) {
                            return new ClassPathResource("/static/index.html");
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
