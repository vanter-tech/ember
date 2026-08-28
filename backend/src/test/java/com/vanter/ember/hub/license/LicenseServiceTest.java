package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
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

    private HubStateStore mockStateStore;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        licenseFile = tempDir.resolve("license.key");
        fingerprintService = mock(HardwareFingerprintService.class);
        when(fingerprintService.currentFingerprint()).thenReturn("fingerprint-a");
        stateStore = new HubStateStore(tempDir.resolve("hub-state.json"));
        licenseService = new LicenseService(
                licenseFile, keyPair.getPublic(), new LicenseKeyParser(), fingerprintService, stateStore,
                Duration.ofHours(48));
    }

    /**
     * Builds a service whose {@link HubStateStore} is a Mockito mock (kept in {@link #mockStateStore})
     * so the suspended-grace tests can {@code verify} persistence, plus the extra {@code Duration} arg.
     */
    private LicenseService newServiceWithGrace(Duration grace) {
        mockStateStore = mock(HubStateStore.class);
        return new LicenseService(
                licenseFile, keyPair.getPublic(), new LicenseKeyParser(), fingerprintService,
                mockStateStore, grace);
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

    @Test
    void isSuspendedGraceExpired_nullSuspendedSince_isFalse() {
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));
        HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
        assertThat(service.isSuspendedGraceExpired(state)).isFalse();
    }

    @Test
    void isSuspendedGraceExpired_within48h_isFalse() {
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));
        HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(),
                Instant.now().minus(10, ChronoUnit.HOURS));
        assertThat(service.isSuspendedGraceExpired(state)).isFalse();
    }

    @Test
    void isSuspendedGraceExpired_past48h_isTrue() {
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));
        HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(),
                Instant.now().minus(49, ChronoUnit.HOURS));
        assertThat(service.isSuspendedGraceExpired(state)).isTrue();
    }

    @Test
    void recordSuspended_firstTime_stampsAndPersists() {
        HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));

        HubState after = service.recordSuspended(before);

        assertThat(after.suspendedSince()).isNotNull();
        verify(mockStateStore).save(after);
    }

    @Test
    void recordSuspended_alreadySuspended_isNoOp() {
        Instant original = Instant.now().minus(5, ChronoUnit.HOURS);
        HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), original);
        LicenseService service = newServiceWithGrace(Duration.ofHours(48));

        HubState after = service.recordSuspended(before);

        assertThat(after.suspendedSince()).isEqualTo(original);
        verify(mockStateStore, never()).save(any());
    }
}
