package com.vanter.ember.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseKeyParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HubBeansConfigTest {

    @Test
    void hubProfile_registersFingerprintAndStateBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(HubBeansConfig.class)
                .withPropertyValues("spring.profiles.active=hub")
                .run(context -> {
                    assertThat(context).hasSingleBean(HubProperties.class);
                    assertThat(context).hasSingleBean(HardwareFingerprintService.class);
                    assertThat(context).hasSingleBean(HubStateStore.class);
                    assertThat(context).hasSingleBean(LicenseKeyParser.class);
                });
    }

    @Test
    void defaultProfile_registersNoHubBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(HubBeansConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(HubProperties.class));
    }
}
