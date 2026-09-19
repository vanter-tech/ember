package com.vanter.ember.hub.backup;

import java.util.List;

/** What {@code HubControlServer} needs; {@link HubBackupService} is the real implementation. */
public interface HubBackup {

    BackupStatus status();

    BackupConfig getConfig();

    /** @throws IllegalArgumentException when {@code destDir} is blank or {@code retention} is below 1 */
    BackupConfig setConfig(BackupConfig config);

    /** Never throws: a failure comes back as an {@code ERROR} snapshot. Null/blank = configured folder. */
    BackupSnapshot backupNow(String destDirOrNull);

    List<BackupSnapshot> listSnapshots();

    /** Validates a backup file (any path) and its version compatibility, without touching any data. */
    BackupSnapshot inspect(String path) throws BackupException;

    /**
     * Restores {@code path} over the live data. Order: validate + version gate (nothing touched if it
     * fails) -> pre-restore safety snapshot (unless {@code skipSafetySnapshot}) -> stop everything ->
     * rebuild Postgres if unusable -> {@code dropdb}/{@code createdb}/{@code pg_restore} -> swap MinIO
     * files -> start again. Blocks until done.
     */
    void restore(String path, boolean skipSafetySnapshot) throws BackupException;
}
