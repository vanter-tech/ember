package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The plain-text blob behind the dashboard's "Copiar diagnóstico" button — enough for a support
 * ticket without leaking the API key: the backend appears as a bare host, never the full URL.
 */
public final class DiagnosticsReport {

    private DiagnosticsReport() {}

    public static String build(StatusHub.Snapshot snapshot, CredentialStore store) {
        Optional<AgentCredential> credential = store.load();
        String backendHost = credential
                .map(c -> hostOf(c.backendBaseUrl()))
                .orElse("(sin emparejar)");

        StringBuilder sb = new StringBuilder();
        sb.append("=== Ember Agent — diagnóstico ===\n");
        sb.append("version=").append(version()).append('\n');
        sb.append("os=").append(System.getProperty("os.name", "?"))
                .append(' ').append(System.getProperty("os.version", "")).append('\n');
        sb.append("java.version=").append(System.getProperty("java.version", "?")).append('\n');
        sb.append("backendHost=").append(backendHost).append('\n');
        sb.append("agentId=").append(snapshot.agentId() == null || snapshot.agentId().isBlank()
                ? "(desconocido)" : snapshot.agentId()).append('\n');
        sb.append("credencial cifrada=").append(store.isEncrypted() ? "sí" : "no").append('\n');
        sb.append("fase=").append(snapshot.phase()).append(" — ").append(snapshot.detail()).append('\n');
        sb.append("impresoras=").append(snapshot.printerCount()).append('\n');
        sb.append("--- últimos ").append(snapshot.recentJobs().size()).append(" trabajos ---\n");
        DateTimeFormatter fmt = DateTimeFormatter.ISO_INSTANT;
        for (StatusHub.JobRecord job : snapshot.recentJobs()) {
            sb.append(fmt.format(job.at()))
                    .append("  ").append(nullToDash(job.role()))
                    .append("  ").append(nullToDash(job.queue()))
                    .append("  ").append(nullToDash(job.result()));
            if (job.error() != null && !job.error().isBlank()) {
                sb.append("  ").append(job.error());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String version() {
        String v = DiagnosticsReport.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url).getHost();
            return host != null ? host : url;
        } catch (RuntimeException e) {
            return url;
        }
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
