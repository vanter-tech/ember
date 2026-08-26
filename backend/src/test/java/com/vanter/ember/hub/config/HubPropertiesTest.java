package com.vanter.ember.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HubPropertiesTest {

    @Test
    void fromEnvironment_usesDefaultsWhenNoEnvVarsSet() {
        HubProperties properties = HubProperties.fromEnvironment();

        assertThat(properties.dataDir()).isEqualTo(Path.of("./data/postgres"));
        assertThat(properties.postgresBinDir()).isEqualTo(Path.of("./postgres/bin"));
        assertThat(properties.licenseFile()).isEqualTo(Path.of("./license.key"));
        assertThat(properties.publicKeyFile()).isEqualTo(Path.of("./hub-public-key.der"));
        assertThat(properties.stateFile()).isEqualTo(Path.of("./hub-state.json"));
        assertThat(properties.postgresPort()).isEqualTo(5432);
        assertThat(properties.serverPort()).isEqualTo(8080);
        assertThat(properties.activationUrl()).isEmpty();
    }
}
