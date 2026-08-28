package com.vanter.ember.hub.license;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class LicenseService {

    private static final Duration GRACE_PERIOD = Duration.ofDays(4);

    private final Path licenseFile;
    private final PublicKey publicKey;
    private final LicenseKeyParser parser;
    private final HardwareFingerprintService fingerprintService;
    private final HubStateStore stateStore;
    private final Duration suspendedGrace;

    public LicenseService(
            Path licenseFile,
            PublicKey publicKey,
            LicenseKeyParser parser,
            HardwareFingerprintService fingerprintService,
            HubStateStore stateStore,
            Duration suspendedGrace) {
        this.licenseFile = licenseFile;
        this.publicKey = publicKey;
        this.parser = parser;
        this.fingerprintService = fingerprintService;
        this.stateStore = stateStore;
        this.suspendedGrace = suspendedGrace;
    }

    /**
     * First run: verifies the signed license, stamps this machine's fingerprint, and persists it.
     * Later runs: verifies the fingerprint still matches what was stamped — a mismatch means this
     * license.key was copied onto a different PC.
     */
    public HubState validateOrActivate() throws InvalidLicenseException {
        if (!Files.exists(licenseFile)) {
            throw new InvalidLicenseException(
                    "No se encontró license.key en " + licenseFile + ". Contacta a soporte de Ember "
                            + "para obtener tu licencia.");
        }
        String contents;
        try {
            contents = Files.readString(licenseFile);
        } catch (IOException e) {
            throw new InvalidLicenseException("No se pudo leer license.key.", e);
        }
        LicenseKey licenseKey = parser.parseAndVerify(contents, publicKey);
        String currentFingerprint = fingerprintService.currentFingerprint();

        Optional<HubState> existing = stateStore.load();
        if (existing.isPresent()) {
            HubState state = existing.get();
            if (!state.hardwareFingerprint().equals(currentFingerprint)) {
                throw new InvalidLicenseException(
                        "Esta licencia ya está activada en otra PC. Contacta a soporte de Ember "
                                + "para reasignarla.");
            }
            return state;
        }

        HubState activated = new HubState(currentFingerprint, licenseKey.restaurantId(), Instant.now());
        stateStore.save(activated);
        return activated;
    }

    public boolean isWithinGracePeriod(HubState state) {
        return Duration.between(state.lastHeartbeatAt(), Instant.now()).compareTo(GRACE_PERIOD) <= 0;
    }

    public HubState recordHeartbeatSuccess(HubState state) {
        HubState updated = state.withHeartbeatNow();
        stateStore.save(updated);
        return updated;
    }

    /**
     * True once a Hub has been marked {@code SUSPENDED} by the cloud for longer than the courtesy
     * grace window. Until then the Hub keeps operating so a mistaken/transient suspension doesn't
     * halt service instantly.
     */
    public boolean isSuspendedGraceExpired(HubState state) {
        return state.suspendedSince() != null
                && Duration.between(state.suspendedSince(), Instant.now()).compareTo(suspendedGrace) > 0;
    }

    /**
     * Stamps {@code suspendedSince} the first time the cloud reports SUSPENDED and persists it; a
     * later SUSPENDED cycle is a no-op so the courtesy-grace counter is not reset every heartbeat.
     */
    public HubState recordSuspended(HubState state) {
        if (state.suspendedSince() != null) {
            return state;
        }
        HubState updated = state.withSuspendedSince(Instant.now());
        stateStore.save(updated);
        return updated;
    }
}
