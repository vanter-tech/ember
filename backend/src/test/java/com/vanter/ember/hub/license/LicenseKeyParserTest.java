package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenseKeyParserTest {

    @Test
    void sign_thenParseAndVerify_roundTrips() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();
        LicenseKey original = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String signed = LicenseKeyParser.sign(original, keyPair.getPrivate());
        LicenseKey parsed = parser.parseAndVerify(signed, keyPair.getPublic());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void parseAndVerify_rejectsSignatureFromADifferentKeyPair() throws Exception {
        KeyPair signingKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair wrongKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();
        LicenseKey original = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String signed = LicenseKeyParser.sign(original, signingKeyPair.getPrivate());

        assertThatThrownBy(() -> parser.parseAndVerify(signed, wrongKeyPair.getPublic()))
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("firma");
    }

    @Test
    void parseAndVerify_rejectsMalformedContent() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();

        assertThatThrownBy(() -> parser.parseAndVerify("not-a-license-key", keyPair.getPublic()))
                .isInstanceOf(InvalidLicenseException.class);
    }
}
