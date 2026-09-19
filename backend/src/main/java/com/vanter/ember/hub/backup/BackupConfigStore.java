package com.vanter.ember.hub.backup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists backup config + last-run info as a small JSON file next to {@code hub-state.json} —
 * deliberately NOT in Postgres: a restore replaces the whole database and must never be able to
 * erase the backup system's own state.
 */
public final class BackupConfigStore {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Persisted(BackupConfig config, BackupSnapshot lastRun, String lastSuccessAt) {}

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final Path defaultDestDir;

    public BackupConfigStore(Path file, Path defaultDestDir) {
        this.file = file;
        this.defaultDestDir = defaultDestDir;
    }

    public Path defaultDestDir() {
        return defaultDestDir;
    }

    public synchronized BackupConfig loadConfig() {
        BackupConfig saved = read().config();
        if (saved == null || saved.destDir() == null || saved.destDir().isBlank()) {
            return new BackupConfig(defaultDestDir.toString(),
                    saved == null || saved.retention() < 1 ? BackupConfig.DEFAULT_RETENTION : saved.retention());
        }
        return saved;
    }

    public synchronized void saveConfig(BackupConfig config) {
        Persisted p = read();
        write(new Persisted(config, p.lastRun(), p.lastSuccessAt()));
    }

    public synchronized BackupSnapshot loadLastRun() {
        return read().lastRun();
    }

    public synchronized void saveLastRun(BackupSnapshot lastRun) {
        Persisted p = read();
        write(new Persisted(p.config(), lastRun, p.lastSuccessAt()));
    }

    public synchronized String loadLastSuccessAt() {
        return read().lastSuccessAt();
    }

    public synchronized void saveLastSuccessAt(String isoInstant) {
        Persisted p = read();
        write(new Persisted(p.config(), p.lastRun(), isoInstant));
    }

    private Persisted read() {
        if (!Files.exists(file)) {
            return new Persisted(null, null, null);
        }
        try {
            return MAPPER.readValue(file.toFile(), Persisted.class);
        } catch (IOException e) {
            return new Persisted(null, null, null); // corrupt/unreadable file -> fall back to defaults
        }
    }

    private void write(Persisted p) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(file.toFile(), p);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + file, e);
        }
    }
}
