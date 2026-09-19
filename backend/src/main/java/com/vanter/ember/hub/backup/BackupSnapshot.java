package com.vanter.ember.hub.backup;

/** One backup file (or one failed attempt). {@code createdAt} is ISO-8601 text on purpose. */
public record BackupSnapshot(
        String id,
        String path,
        String createdAt,
        long sizeBytes,
        String status,
        String errorMessage,
        String appVersion,
        boolean preRestoreSafety) {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    public static BackupSnapshot error(String createdAt, String message) {
        return new BackupSnapshot(null, null, createdAt, 0, ERROR, message, null, false);
    }
}
