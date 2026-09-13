package com.vanter.ember.hub.control;

/** Mirrors the real lifecycle of one Hub-managed service (Postgres, MinIO, or the Spring server). */
public enum ServicePhase {
    STOPPED,
    STARTING,
    RUNNING,
    STOPPING,
    ERROR
}
