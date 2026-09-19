package com.vanter.ember.hub.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.hub.backup.BackupConfig;
import com.vanter.ember.hub.backup.BackupException;
import com.vanter.ember.hub.backup.BackupSnapshot;
import com.vanter.ember.hub.backup.BackupStatus;
import com.vanter.ember.hub.backup.HubBackup;
import java.io.IOException;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubControlServerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private FakeOrchestrator orchestrator;
    private FakeBackup backup;
    private HubControlServer server;
    private String base;

    @BeforeEach
    void start() throws IOException {
        orchestrator = new FakeOrchestrator();
        backup = new FakeBackup();
        server = new HubControlServer(orchestrator, backup);
        int port = server.start();
        base = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void status_reflectsOrchestratorSnapshot() throws Exception {
        orchestrator.snapshot = new HubOrchestrator.HubStatusSnapshot(
                ServicePhase.RUNNING, null, ServicePhase.RUNNING, null, ServicePhase.STARTING, null,
                new HubOrchestrator.LicenseSnapshot("OK", null, null), 8080);

        JsonNode body = mapper.readTree(get("/api/status").body());

        assertEquals("RUNNING", body.get("postgres").asText());
        assertEquals("STARTING", body.get("server").asText());
        assertEquals("OK", body.get("license").get("status").asText());
        assertEquals(8080, body.get("serverPort").asInt());
    }

    @Test
    void start_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = post("/api/start", "");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.startCalled);
    }

    @Test
    void start_rejectsNonPost() throws Exception {
        HttpResponse<String> res = get("/api/start");

        assertEquals(405, res.statusCode());
    }

    @Test
    void stop_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = post("/api/stop", "");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.stopCalled);
    }

    @Test
    void license_missingPath_returns400() throws Exception {
        HttpResponse<String> res = post("/api/license", "{}");

        assertEquals(400, res.statusCode());
    }

    @Test
    void license_installFailure_returns400WithMessage() throws Exception {
        orchestrator.installLicenseFailure = new IOException("no existe");

        HttpResponse<String> res = post("/api/license", "{\"path\":\"C:/x/license.key\"}");

        assertEquals(400, res.statusCode());
        assertEquals("no existe", mapper.readTree(res.body()).get("error").asText());
    }

    @Test
    void license_installSuccess_returns200() throws Exception {
        HttpResponse<String> res = post("/api/license", "{\"path\":\"C:/x/license.key\"}");

        assertEquals(200, res.statusCode());
        assertEquals(Path.of("C:/x/license.key"), orchestrator.installedFrom);
    }

    @Test
    void license_delete_callsOrchestratorAndReturns200() throws Exception {
        HttpResponse<String> res = delete("/api/license");

        assertEquals(200, res.statusCode());
        assertTrue(orchestrator.removeLicenseCalled);
    }

    @Test
    void license_delete_failure_returns400WithMessage() throws Exception {
        orchestrator.removeLicenseFailure = new IOException("no se pudo borrar");

        HttpResponse<String> res = delete("/api/license");

        assertEquals(400, res.statusCode());
        assertEquals("no se pudo borrar", mapper.readTree(res.body()).get("error").asText());
    }

    @Test
    void backupStatus_returnsBackupStatus() throws Exception {
        JsonNode body = mapper.readTree(get("/api/backup/status").body());

        assertEquals("C:\\backups", body.get("destDir").asText());
        assertEquals("C:\\backups", body.get("defaultDestDir").asText());
        assertEquals(7, body.get("retention").asInt());
    }

    @Test
    void backupConfig_post_savesAndReturnsIt() throws Exception {
        HttpResponse<String> res = post("/api/backup/config", "{\"destDir\":\"E:\\\\usb\",\"retention\":3}");

        assertEquals(200, res.statusCode());
        assertEquals("E:\\usb", backup.savedConfig.destDir());
        assertEquals(3, backup.savedConfig.retention());
    }

    @Test
    void backupConfig_invalid_returns400() throws Exception {
        backup.setConfigFailure = new IllegalArgumentException("retention debe ser al menos 1.");

        HttpResponse<String> res = post("/api/backup/config", "{\"destDir\":\"E:\\\\usb\",\"retention\":0}");

        assertEquals(400, res.statusCode());
        assertEquals("retention debe ser al menos 1.", mapper.readTree(res.body()).get("error").asText());
    }

    @Test
    void backupNow_passesDestDir_andEmptyBodyMeansConfiguredFolder() throws Exception {
        assertEquals(200, post("/api/backup/now", "{\"destDir\":\"E:\\\\usb\"}").statusCode());
        assertEquals("E:\\usb", backup.nowDestDir);

        assertEquals(200, post("/api/backup/now", "").statusCode());
        assertNull(backup.nowDestDir);
    }

    @Test
    void backupList_returnsSnapshotsArray() throws Exception {
        JsonNode body = mapper.readTree(get("/api/backup/list").body());

        assertTrue(body.isArray());
        assertEquals("ember-backup-x.zip", body.get(0).get("id").asText());
    }

    @Test
    void backupInspect_missingPath_returns400() throws Exception {
        assertEquals(400, post("/api/backup/inspect", "{}").statusCode());
    }

    @Test
    void backupInspect_incompatible_returns409WithCode() throws Exception {
        backup.inspectFailure = new BackupException(BackupException.INCOMPATIBLE, "versión más reciente");

        HttpResponse<String> res = post("/api/backup/inspect", "{\"path\":\"C:\\\\x.zip\"}");

        assertEquals(409, res.statusCode());
        assertEquals("BACKUP_INCOMPATIBLE", mapper.readTree(res.body()).get("code").asText());
    }

    @Test
    void backupRestore_success_passesPathAndFlag() throws Exception {
        HttpResponse<String> res =
                post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\",\"skipSafetySnapshot\":true}");

        assertEquals(200, res.statusCode());
        assertEquals("C:\\x.zip", backup.restoredPath);
        assertTrue(backup.restoredSkipSafety);
    }

    @Test
    void backupRestore_errorsMapToHttpStatuses() throws Exception {
        backup.restoreFailure = new BackupException(BackupException.SAFETY_FAILED, "sin copia previa");
        assertEquals(409, post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}").statusCode());

        backup.restoreFailure = new BackupException(BackupException.INVALID, "no es un respaldo");
        assertEquals(400, post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}").statusCode());

        backup.restoreFailure = new BackupException(BackupException.RESTORE_FAILED, "falló");
        HttpResponse<String> res = post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}");
        assertEquals(500, res.statusCode());
        assertEquals("BACKUP_RESTORE_FAILED", mapper.readTree(res.body()).get("code").asText());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String jsonBody) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + path)).DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static final class FakeOrchestrator implements HubOrchestrator {
        boolean startCalled;
        boolean stopCalled;
        boolean removeLicenseCalled;
        Path installedFrom;
        IOException installLicenseFailure;
        IOException removeLicenseFailure;
        HubOrchestrator.HubStatusSnapshot snapshot = new HubOrchestrator.HubStatusSnapshot(
                ServicePhase.STOPPED, null, ServicePhase.STOPPED, null, ServicePhase.STOPPED, null,
                new HubOrchestrator.LicenseSnapshot("NONE", null, null), 8080);

        @Override
        public void start(String[] launchArgs) {
            startCalled = true;
        }

        @Override
        public void stop() {
            stopCalled = true;
        }

        @Override
        public void stopAndWait() {
            stopCalled = true;
        }

        @Override
        public void installLicense(Path source) throws IOException {
            if (installLicenseFailure != null) {
                throw installLicenseFailure;
            }
            installedFrom = source;
        }

        @Override
        public void removeLicense() throws IOException {
            if (removeLicenseFailure != null) {
                throw removeLicenseFailure;
            }
            removeLicenseCalled = true;
        }

        @Override
        public HubStatusSnapshot snapshot() {
            return snapshot;
        }
    }

    private static final class FakeBackup implements HubBackup {
        BackupConfig savedConfig;
        IllegalArgumentException setConfigFailure;
        String nowDestDir;
        BackupException inspectFailure;
        BackupException restoreFailure;
        String restoredPath;
        boolean restoredSkipSafety;
        final BackupSnapshot snapshot = new BackupSnapshot("ember-backup-x.zip", "C:\\backups\\ember-backup-x.zip",
                "2026-09-19T10:00:00Z", 10, BackupSnapshot.OK, null, "0.2.6.1", false);

        @Override
        public BackupStatus status() {
            return new BackupStatus(null, null, "C:\\backups", "C:\\backups", 7);
        }

        @Override
        public BackupConfig getConfig() {
            return new BackupConfig("C:\\backups", 7);
        }

        @Override
        public BackupConfig setConfig(BackupConfig config) {
            if (setConfigFailure != null) {
                throw setConfigFailure;
            }
            savedConfig = config;
            return config;
        }

        @Override
        public BackupSnapshot backupNow(String destDirOrNull) {
            nowDestDir = destDirOrNull;
            return snapshot;
        }

        @Override
        public List<BackupSnapshot> listSnapshots() {
            return List.of(snapshot);
        }

        @Override
        public BackupSnapshot inspect(String path) throws BackupException {
            if (inspectFailure != null) {
                throw inspectFailure;
            }
            return snapshot;
        }

        @Override
        public void restore(String path, boolean skipSafetySnapshot) throws BackupException {
            if (restoreFailure != null) {
                throw restoreFailure;
            }
            restoredPath = path;
            restoredSkipSafety = skipSafetySnapshot;
        }
    }
}
