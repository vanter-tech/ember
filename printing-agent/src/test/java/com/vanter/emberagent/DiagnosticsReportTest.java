package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DiagnosticsReportTest {

    private static final class FakeStore implements CredentialStore {
        @Override
        public Optional<AgentCredential> load() {
            return Optional.of(new AgentCredential("super-secret-key", "https://api.ember.example/v1"));
        }

        @Override
        public void save(AgentCredential credential) {}

        @Override
        public void clear() {}

        @Override
        public boolean isEncrypted() {
            return true;
        }
    }

    @Test
    void build_includesAgentIdEncryptionFlagAndOneLinePerJob_butNoHostOrRuntimeVersions() {
        StatusHub.Snapshot snapshot = new StatusHub.Snapshot(
                StatusHub.Phase.CONNECTED, "Conectado", Instant.now(), "agent-42", 2,
                List.of(
                        new StatusHub.JobRecord(Instant.now(), "KITCHEN", "Cocina 1", "PRINTED", null),
                        new StatusHub.JobRecord(Instant.now(), "BAR", "Barra", "ERROR", "cola no encontrada")));

        String report = DiagnosticsReport.build(snapshot, new FakeStore());

        assertTrue(report.contains("agentId=agent-42"), report);
        assertTrue(report.contains("cifrada=sí"), report);
        assertFalse(report.contains("super-secret-key"), "must never leak the API key");
        assertFalse(report.contains("api.ember.example"), "must not reveal the backend host");
        assertFalse(report.toLowerCase().contains("backendhost"), report);
        assertFalse(report.contains("java.version"), "no runtime version");
        assertFalse(report.contains(System.getProperty("java.version")), "no runtime version");
        assertFalse(report.contains("os="), "no OS version");
        assertFalse(report.contains(System.getProperty("os.version")), "no OS version");
        assertTrue(report.contains("PRINTED"), report);
        assertTrue(report.contains("cola no encontrada"), report);
    }

    @Test
    void build_unpairedStore_stillProducesAReport() {
        CredentialStore empty = new CredentialStore() {
            @Override
            public Optional<AgentCredential> load() {
                return Optional.empty();
            }

            @Override
            public void save(AgentCredential credential) {}

            @Override
            public void clear() {}

            @Override
            public boolean isEncrypted() {
                return false;
            }
        };
        StatusHub.Snapshot snapshot = new StatusHub.Snapshot(
                StatusHub.Phase.UNPAIRED, "Sin emparejar", null, "", 0, List.of());

        String report = DiagnosticsReport.build(snapshot, empty);

        assertTrue(report.contains("agentId=(desconocido)"), report);
        assertTrue(report.contains("cifrada=no"), report);
    }
}
