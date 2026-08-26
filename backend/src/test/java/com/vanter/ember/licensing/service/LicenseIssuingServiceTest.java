package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenseIssuingServiceTest {

    @Test
    void issue_producesLicenseKeyVerifiableWithDerivedPublicKey() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        LicenseIssuingService service = new LicenseIssuingService(privateKeyBase64);
        UUID restaurantId = UUID.randomUUID();

        String licenseKeyContents = service.issue(restaurantId);

        LicenseKey parsed = new LicenseKeyParser().parseAndVerify(licenseKeyContents, service.publicKey());
        assertThat(parsed.restaurantId()).isEqualTo(restaurantId);
    }

    @Test
    void constructor_throwsOnMalformedKey() {
        assertThatThrownBy(() -> new LicenseIssuingService("not-valid-base64!!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("private-key");
    }
}
