package com.vanter.ember.hub.backup;

/** {@code defaultDestDir} is the "Esta máquina" folder; {@code destDir} is the scheduled target. */
public record BackupStatus(
        BackupSnapshot lastRun,
        String nextScheduledRun,
        String destDir,
        String defaultDestDir,
        int retention) {}
