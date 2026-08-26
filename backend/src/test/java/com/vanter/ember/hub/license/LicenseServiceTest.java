package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseServiceTest {

    @TempDir Path tempDir;

    private KeyPair keyPair;
    private Path licenseFile;
    private HardwareFingerprintService fingerprintService;
    private HubStateStore stateStore;
    private LicenseService licenseService;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        licenseFile = tempDir.resolve("license.key");
        fingerprintService = mock(HardwareFingerprintService.class);
        when(fingerprintService.currentFingerprint()).thenReturn("fingerprint-a");
        stateStore = new HubStateStore(tempDir.resolve("hub-state.json"));
        licenseService = new LicenseService(
                licenseFile, keyPair.getPublic(), new LicenseKeyParser(), fingerprintService, stateStore);
    }

    private void writeValidLicenseFile() throws Exception {
        LicenseKey key = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        Files.writeString(licenseFile, LicenseKeyParser.sign(key, keyPair.getPrivate()));
    }

    @Test
    void validateOrActivate_withNoLicenseFile_throws() {
        assertThatThrownBy(() -> licenseService.validateOrActivate())
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("license.key");
    }

    @Test
    void validateOrActivate_firstRun_activatesAndStoresState() throws Exception {
        writeValidLicenseFile();

        HubState state = licenseService.validateOrActivate();

        assertThat(state.hardwareFingerprint()).isEqualTo("fingerprint-a");
        assertThat(stateStore.load()).contains(state);
    }

    @Test
    void validateOrActivate_secondRunSameHardware_returnsExistingState() throws Exception {
        writeValidLicenseFile();
        HubState firstRun = licenseService.validateOrActivate();

        HubState secondRun = licenseService.validateOrActivate();

        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void validateOrActivate_secondRunDifferentHardware_throws() throws Exception {
        writeValidLicenseFile();
        licenseService.validateOrActivate();
        when(fingerprintService.currentFingerprint()).thenReturn("fingerprint-b");

        assertThatThrownBy(() -> licenseService.validateOrActivate())
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("otra PC");
    }

    @Test
    void isWithinGracePeriod_trueJustAfterHeartbeat_falseAfterFiveDays() {
        HubState recent = new HubState("fp", UUID.randomUUID(), Instant.now());
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));

        assertThat(licenseService.isWithinGracePeriod(recent)).isTrue();
        assertThat(licenseService.isWithinGracePeriod(stale)).isFalse();
    }

    @Test
    void recordHeartbeatSuccess_updatesTimestampAndPersists() {
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        stateStore.save(stale);

        HubState updated = licenseService.recordHeartbeatSuccess(stale);

        assertThat(licenseService.isWithinGracePeriod(updated)).isTrue();
        assertThat(stateStore.load()).contains(updated);
    }
}
