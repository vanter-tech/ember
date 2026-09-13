package com.vanter.ember.hub.bootstrap;

/**
 * Lets a caller of {@link HubBootstrapRunner#startServices(HubBootProgressListener)} observe the
 * real, already-sequential Postgres-then-MinIO startup order without changing it — added for the
 * Tauri shell's control server (spec docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md
 * Task 1) so its per-service cards can auto-expand/collapse in sync with what is actually
 * happening instead of guessing timing client-side.
 */
public interface HubBootProgressListener {

    void onPostgresStarting();

    void onPostgresReady();

    void onMinioStarting();

    void onMinioReady();

    HubBootProgressListener NO_OP = new HubBootProgressListener() {
        @Override
        public void onPostgresStarting() {}

        @Override
        public void onPostgresReady() {}

        @Override
        public void onMinioStarting() {}

        @Override
        public void onMinioReady() {}
    };
}
