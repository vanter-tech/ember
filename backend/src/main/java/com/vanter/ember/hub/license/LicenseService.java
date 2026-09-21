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
    private static final Duration CLOCK_TOLERANCE = Duration.ofMinutes(10);

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

        // Starts with an expired heartbeat: grace is earned only by a signed cloud heartbeat, so
        // deleting hub-state.json to get a fresh 4-day window no longer works.
        HubState activated = new HubState(
                currentFingerprint, licenseKey.restaurantId(), Instant.EPOCH, null, Instant.now());
        stateStore.save(activated);
        return activated;
    }

    /** True when {@code signature} is the cloud's signature over this heartbeat answer and our nonce. */
    public boolean verifyHeartbeat(String status, String serverTime, String nonce, String signature) {
        return LicenseKeyParser.verifyHeartbeat(status, serverTime, nonce, signature, publicKey);
    }

    /** True when the local clock is behind the newest reading ever recorded (clock set back). */
    public boolean isClockRolledBack(HubState state) {
        return state.lastSeenAt() != null
                && Instant.now().isBefore(state.lastSeenAt().minus(CLOCK_TOLERANCE));
    }

    /** Advances (never lowers) the recorded clock reading and persists it. */
    public HubState recordClockSeen(HubState state) {
        HubState updated = state.withLastSeenAtAdvancedTo(Instant.now());
        if (!updated.equals(state)) {
            stateStore.save(updated);
        }
        return updated;
    }

    public boolean isWithinGracePeriod(HubState state) {
        return Duration.between(state.lastHeartbeatAt(), Instant.now()).compareTo(GRACE_PERIOD) <= 0;
    }

    /** @param cloudTime the signed {@code serverTime} of the heartbeat that just succeeded. */
    public HubState recordHeartbeatSuccess(HubState state, Instant cloudTime) {
        boolean clockAgrees = Duration.between(cloudTime, Instant.now()).abs().compareTo(CLOCK_TOLERANCE) <= 0;
        HubState updated = state.withHeartbeatNow(clockAgrees);
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
     * True once a Hub whose restaurant moved to Ember Web (cloud status MIGRATED) has been past the
     * courtesy window: from then on the Hub is read-only. Reuses the suspension courtesy length.
     */
    public boolean isMigratedGraceExpired(HubState state) {
        return state.migratedSince() != null
                && Duration.between(state.migratedSince(), Instant.now()).compareTo(suspendedGrace) > 0;
    }

    /**
     * Stamps {@code migratedSince} the first time the cloud reports MIGRATED and persists it; later
     * cycles are a no-op so the courtesy clock is not reset every heartbeat.
     */
    public HubState recordMigrated(HubState state) {
        if (state.migratedSince() != null) {
            return state;
        }
        HubState updated = state.withMigratedSince(Instant.now());
        stateStore.save(updated);
        return updated;
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
