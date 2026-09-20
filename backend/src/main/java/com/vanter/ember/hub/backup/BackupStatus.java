package com.vanter.ember.hub.backup;

/**
 * {@code defaultDestDir} is the "Esta máquina" folder; {@code destDir} is the scheduled target;
 * {@code progress} is non-null only while a backup or restore is running.
 */
public record BackupStatus(
        BackupSnapshot lastRun,
        String nextScheduledRun,
        String destDir,
        String defaultDestDir,
        int retention,
        BackupProgress progress) {}
