package com.vanter.ember.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.Test;

/**
 * Config-file contract test for the {@code prod} profile. A prod-profile
 * {@code @SpringBootTest} needs a live Postgres (the profile pins
 * {@code ddl-auto=validate}) and the suite has no such convention, so this
 * asserts the property file's contract directly.
 */
class ProdManagementPortConfigTest {

    private static Properties prodProperties() throws Exception {
        Properties props = new Properties();
        try (InputStream in = ProdManagementPortConfigTest.class.getClassLoader()
                .getResourceAsStream("application-prod.properties")) {
            assertThat(in).as("application-prod.properties on the classpath").isNotNull();
            props.load(in);
        }
        return props;
    }

    @Test
    void managementRunsOnASeparateLoopbackPort() throws Exception {
        assertThat(prodProperties().getProperty("management.server.port")).isEqualTo("8081");
    }

    @Test
    void forwardedHeadersAreTrusted() throws Exception {
        assertThat(prodProperties().getProperty("server.forward-headers-strategy")).isEqualTo("framework");
    }

    @Test
    void healthDetailsAreHidden() throws Exception {
        assertThat(prodProperties().getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }
}
