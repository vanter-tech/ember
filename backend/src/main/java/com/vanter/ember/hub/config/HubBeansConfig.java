package com.vanter.ember.hub.config;

import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.hub.license.LicenseService;
import java.security.PublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("hub")
public class HubBeansConfig {

    @Bean
    public HubProperties hubProperties() {
        return HubProperties.fromEnvironment();
    }

    @Bean
    public HardwareFingerprintService hardwareFingerprintService() {
        return new HardwareFingerprintService();
    }

    @Bean
    public HubStateStore hubStateStore(HubProperties properties) {
        return new HubStateStore(properties.stateFile());
    }

    @Bean
    public LicenseKeyParser licenseKeyParser() {
        return new LicenseKeyParser();
    }

    @Bean
    @Lazy
    public LicenseService licenseService(
            HubProperties properties,
            LicenseKeyParser parser,
            HardwareFingerprintService fingerprintService,
            HubStateStore stateStore)
            throws InvalidLicenseException {
        PublicKey publicKey = LicenseKeyParser.loadPublicKey(properties.publicKeyFile());
        return new LicenseService(properties.licenseFile(), publicKey, parser, fingerprintService, stateStore);
    }
}
