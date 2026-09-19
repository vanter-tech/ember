package com.vanter.ember.hub.control;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Business-logic-free façade the Tauri shell's {@link HubControlServer} talks to instead of the
 * old Swing {@code HubDashboard} reading/mutating its own fields in-process. The real
 * implementation ({@link DefaultHubOrchestrator}) wraps {@code HubBootstrapRunner} and a
 * {@code SpringApplication} run unchanged; this interface only exists so
 * {@link HubControlServer}'s tests can run against a fake instead of booting real Postgres/MinIO.
 */
public interface HubOrchestrator {

    /** Starts Postgres+MinIO+the Spring server in the background; returns immediately. */
    void start(String[] launchArgs);

    /** Stops the Spring server then Postgres+MinIO in the background; returns immediately. */
    void stop();

    /**
     * Same as {@link #stop()} but blocks until the Spring server, MinIO and Postgres are fully
     * stopped. Used by restore, which must own the data directories.
     */
    void stopAndWait();

    /** Copies {@code source} into the configured license file location. */
    void installLicense(Path source) throws IOException;

    /** Deletes the current license file and any persisted activation state, resetting to NONE. */
    void removeLicense() throws IOException;

    HubStatusSnapshot snapshot();

    record LicenseSnapshot(String status, Instant lastHeartbeatAt, Instant suspendedSince) {
        public static final String OK = "OK";
        public static final String SUSPENDED = "SUSPENDED";
        public static final String NONE = "NONE";
    }

    record HubStatusSnapshot(
            ServicePhase postgres,
            String postgresError,
            ServicePhase minio,
            String minioError,
            ServicePhase server,
            String serverError,
            LicenseSnapshot license,
            int serverPort) {}
}
