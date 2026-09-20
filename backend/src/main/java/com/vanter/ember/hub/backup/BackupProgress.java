package com.vanter.ember.hub.backup;

/**
 * What a running backup/restore is doing right now, for the Hub window's progress bar.
 * {@code percent} is {@code null} when the phase has no measurable length (e.g. pg_dump), which the
 * UI shows as an animated bar instead of a filled one.
 */
public record BackupProgress(String operation, String phase, Integer percent) {

    public static final String BACKUP = "BACKUP";
    public static final String RESTORE = "RESTORE";
}
