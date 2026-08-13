package com.vanter.ember.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CorsConfig.class);

    private static CorsConfiguration configFrom(CorsConfigurationSource source) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/login");
        return source.getCorsConfiguration(request);
    }

    @Test
    void defaultsAllowLocalDevOriginsAndNothingElse() {
        runner.run(ctx -> {
            CorsConfiguration config = configFrom(ctx.getBean(CorsConfigurationSource.class));

            assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
            assertThat(config.checkOrigin("http://localhost:3000")).isEqualTo("http://localhost:3000");
            assertThat(config.checkOrigin("https://evil.example.com")).isNull();
            // No patterns configured by default: tenant subdomains must be opted into per deployment.
            assertThat(config.checkOrigin("https://acme.ember.vanter.com")).isNull();
            assertThat(config.getAllowCredentials()).isTrue();
        });
    }

    @Test
    void configuredPatternAllowsAnyTenantSubdomain() {
        runner.withPropertyValues("ember.cors.allowed-origin-patterns=https://*.ember.vanter.com")
                .run(ctx -> {
                    CorsConfiguration config = configFrom(ctx.getBean(CorsConfigurationSource.class));

                    assertThat(config.checkOrigin("https://acme.ember.vanter.com"))
                            .isEqualTo("https://acme.ember.vanter.com");
                    assertThat(config.checkOrigin("https://other-diner.ember.vanter.com"))
                            .isEqualTo("https://other-diner.ember.vanter.com");
                    // The exact list keeps working alongside the pattern.
                    assertThat(config.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
                });
    }

    @Test
    void configuredPatternRejectsLookalikeOrigins() {
        runner.withPropertyValues("ember.cors.allowed-origin-patterns=https://*.ember.vanter.com")
                .run(ctx -> {
                    CorsConfiguration config = configFrom(ctx.getBean(CorsConfigurationSource.class));

                    // Attacker-controlled apex, tenant-looking label.
                    assertThat(config.checkOrigin("https://acme.ember.vanter.com.evil.com")).isNull();
                    // Scheme downgrade must not slip through.
                    assertThat(config.checkOrigin("http://acme.ember.vanter.com")).isNull();
                    // The apex itself is not a tenant host.
                    assertThat(config.checkOrigin("https://ember.vanter.com")).isNull();
                });
    }

    @Test
    void originsAndMethodsAreOverridableFromConfiguration() {
        runner.withPropertyValues(
                        "ember.cors.allowed-origins=https://app.ember.vanter.com",
                        "ember.cors.allowed-methods=GET,POST",
                        "ember.cors.exposed-headers=X-Request-Id",
                        "ember.cors.max-age=600")
                .run(ctx -> {
                    CorsConfiguration config = configFrom(ctx.getBean(CorsConfigurationSource.class));

                    assertThat(config.checkOrigin("https://app.ember.vanter.com"))
                            .isEqualTo("https://app.ember.vanter.com");
                    assertThat(config.checkOrigin("http://localhost:5173")).isNull();
                    assertThat(config.getAllowedMethods()).containsExactly("GET", "POST");
                    assertThat(config.getExposedHeaders()).containsExactly("X-Request-Id");
                    assertThat(config.getMaxAge()).isEqualTo(600L);
                });
    }
}
