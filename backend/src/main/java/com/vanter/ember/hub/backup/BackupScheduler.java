package com.vanter.ember.hub.backup;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wakes up every 15 minutes and asks the backup service whether a backup is due (24 h since the
 * last success, Postgres running). A fixed time of day would never fire on a PC that is switched
 * off overnight. Plain executor, not Spring {@code @Scheduled}: the Spring context is closed
 * during a restore and must not own this.
 */
public final class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final long INITIAL_DELAY_SECONDS = 120;
    private static final long TICK_SECONDS = 15 * 60;

    private final Runnable task;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ember-hub-backup-scheduler");
        t.setDaemon(true);
        return t;
    });

    public BackupScheduler(Runnable task) {
        this.task = task;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::tick, INITIAL_DELAY_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    /** An exception escaping a scheduled task silently cancels all future runs — so never let one. */
    void tick() {
        try {
            task.run();
        } catch (Throwable t) {
            log.error("El respaldo programado falló de forma inesperada", t);
        }
    }
}
