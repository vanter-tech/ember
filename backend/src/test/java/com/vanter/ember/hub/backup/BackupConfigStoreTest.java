package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupConfigStoreTest {

    @TempDir Path tmp;

    private BackupConfigStore store() {
        return new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups"));
    }

    @Test
    void missingFile_returnsDefaults() {
        BackupConfig config = store().loadConfig();

        assertThat(config.destDir()).isEqualTo(tmp.resolve("backups").toString());
        assertThat(config.retention()).isEqualTo(7);
        assertThat(store().loadLastRun()).isNull();
        assertThat(store().loadLastSuccessAt()).isNull();
    }

    @Test
    void roundTripsConfigAndLastRunIndependently() {
        BackupConfigStore store = store();
        store.saveConfig(new BackupConfig("E:\\respaldos", 3));
        BackupSnapshot run = new BackupSnapshot("a.zip", "E:\\respaldos\\a.zip", "2026-09-19T10:00:00Z",
                10, BackupSnapshot.OK, null, "0.2.6.1", false);
        store.saveLastRun(run);
        store.saveLastSuccessAt("2026-09-19T10:00:00Z");

        BackupConfigStore reloaded = store();
        assertThat(reloaded.loadConfig()).isEqualTo(new BackupConfig("E:\\respaldos", 3));
        assertThat(reloaded.loadLastRun()).isEqualTo(run);
        assertThat(reloaded.loadLastSuccessAt()).isEqualTo("2026-09-19T10:00:00Z");
    }

    @Test
    void savingConfigKeepsLastRun() {
        BackupConfigStore store = store();
        store.saveLastSuccessAt("2026-09-19T10:00:00Z");
        store.saveConfig(new BackupConfig("E:\\x", 5));

        assertThat(store().loadLastSuccessAt()).isEqualTo("2026-09-19T10:00:00Z");
    }
}
