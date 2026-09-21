package com.vanter.ember.hub.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultHubOrchestratorTest {

    @TempDir Path tempDir;

    private HubProperties propertiesWithStateFile(Path stateFile) {
        return new HubProperties(
                tempDir.resolve("pg-data"), tempDir.resolve("pg-bin"),
                tempDir.resolve("license.key"), tempDir.resolve("public.der"), stateFile,
                5432, 8080, "", tempDir.resolve("minio-data"), tempDir.resolve("minio-bin"), 9000);
    }

    @Test
    void snapshot_noStateFile_reportsNoneLicense() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        HubOrchestrator.LicenseSnapshot license = orchestrator.snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.NONE, license.status());
        assertNull(license.lastHeartbeatAt());
    }

    @Test
    void snapshot_stateWithoutSuspension_reportsOk() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant heartbeat = Instant.parse("2026-09-13T10:00:00Z");
        new HubStateStore(stateFile).save(new HubState("fp-1", UUID.randomUUID(), heartbeat));

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.OK, license.status());
        assertEquals(heartbeat, license.lastHeartbeatAt());
    }

    @Test
    void snapshot_suspendedState_reportsSuspended() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant suspendedSince = Instant.parse("2026-09-10T08:00:00Z");
        HubState suspended = new HubState("fp-1", UUID.randomUUID(), Instant.parse("2026-09-09T08:00:00Z"))
                .withSuspendedSince(suspendedSince);
        new HubStateStore(stateFile).save(suspended);

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.SUSPENDED, license.status());
        assertEquals(suspendedSince, license.suspendedSince());
    }

    @Test
    void snapshot_migratedState_reportsMigratedWithItsSince() {
        Path stateFile = tempDir.resolve("hub-state.json");
        Instant migratedSince = Instant.parse("2026-09-21T08:00:00Z");
        HubState migrated = new HubState("fp-1", UUID.randomUUID(), Instant.parse("2026-09-20T08:00:00Z"))
                .withMigratedSince(migratedSince);
        new HubStateStore(stateFile).save(migrated);

        HubOrchestrator.LicenseSnapshot license =
                new DefaultHubOrchestrator(propertiesWithStateFile(stateFile)).snapshot().license();

        assertEquals(HubOrchestrator.LicenseSnapshot.MIGRATED, license.status());
        assertEquals(migratedSince, license.migratedSince());
    }

    @Test
    void removeLicense_deletesLicenseFileAndStateFile_resetsToNone() throws Exception {
        Path stateFile = tempDir.resolve("hub-state.json");
        new HubStateStore(stateFile).save(new HubState("fp-1", UUID.randomUUID(), Instant.now()));
        HubProperties properties = propertiesWithStateFile(stateFile);
        Files.createFile(properties.licenseFile());
        DefaultHubOrchestrator orchestrator = new DefaultHubOrchestrator(properties);
        assertEquals(HubOrchestrator.LicenseSnapshot.OK, orchestrator.snapshot().license().status());

        orchestrator.removeLicense();

        assertFalse(Files.exists(properties.licenseFile()));
        assertFalse(Files.exists(stateFile));
        assertEquals(HubOrchestrator.LicenseSnapshot.NONE, orchestrator.snapshot().license().status());
    }

    @Test
    void removeLicense_noExistingFiles_isANoOp() throws Exception {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        orchestrator.removeLicense();

        assertEquals(HubOrchestrator.LicenseSnapshot.NONE, orchestrator.snapshot().license().status());
    }

    @Test
    void snapshot_beforeStart_allServicesStopped() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        HubOrchestrator.HubStatusSnapshot snapshot = orchestrator.snapshot();

        assertEquals(ServicePhase.STOPPED, snapshot.postgres());
        assertEquals(ServicePhase.STOPPED, snapshot.minio());
        assertEquals(ServicePhase.STOPPED, snapshot.server());
        assertEquals(8080, snapshot.serverPort());
    }

    @Test
    void stopAndWait_whenNothingRunning_endsWithEverythingStopped() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        orchestrator.stopAndWait();

        HubOrchestrator.HubStatusSnapshot s = orchestrator.snapshot();
        assertEquals(ServicePhase.STOPPED, s.server());
        assertEquals(ServicePhase.STOPPED, s.postgres());
        assertEquals(ServicePhase.STOPPED, s.minio());
    }
}
