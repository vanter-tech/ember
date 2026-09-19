package com.vanter.ember.hub.backup;

/** {@code destDir} is where scheduled backups are written; a manual backup may target another folder. */
public record BackupConfig(String destDir, int retention) {
    public static final int DEFAULT_RETENTION = 7;
}
