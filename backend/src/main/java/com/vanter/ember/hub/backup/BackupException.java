package com.vanter.ember.hub.backup;

public class BackupException extends Exception {

    public static final String INCOMPATIBLE = "BACKUP_INCOMPATIBLE";
    public static final String SAFETY_FAILED = "BACKUP_SAFETY_FAILED";
    public static final String INVALID = "BACKUP_INVALID";
    public static final String FAILED = "BACKUP_FAILED";
    public static final String RESTORE_FAILED = "BACKUP_RESTORE_FAILED";

    private final String code;

    public BackupException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BackupException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
