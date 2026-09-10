package com.vanter.emberagent.status;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one observable surface the Swing dashboard/tray consume. The headless core
 * ({@code Main}/{@code PrintJobHandler}) pushes state in; nothing reads back out of it except the
 * UI. No Swing dependency so it stays unit-testable. Thread-safe: mutation under a lock,
 * listeners on a {@link CopyOnWriteArrayList}. Keeps the last {@value #MAX_JOBS} job records.
 */
public final class StatusHub {

    public enum Phase { UNPAIRED, CONNECTING, CONNECTED, RETRYING }

    public record JobRecord(Instant at, String role, String queue, String result, String error) {}

    public record Snapshot(
            Phase phase, String detail, Instant lastSeen, String agentId,
            int printerCount, List<JobRecord> recentJobs) {}

    private static final int MAX_JOBS = 20;

    private final Object lock = new Object();
    private final Deque<JobRecord> jobs = new ArrayDeque<>();
    private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();

    private Phase phase = Phase.UNPAIRED;
    private String detail = "Sin emparejar";
    private Instant lastSeen;
    private String agentId = "";
    private int printerCount;

    /** Registers a listener and immediately hands it the current snapshot. */
    public void addListener(Consumer<Snapshot> listener) {
        listeners.add(listener);
        listener.accept(snapshot());
    }

    public void setPhase(Phase newPhase, String newDetail) {
        synchronized (lock) {
            this.phase = newPhase;
            this.detail = newDetail;
        }
        publish();
    }

    public void setConnected(String connectedAgentId, int connectedPrinterCount) {
        synchronized (lock) {
            this.phase = Phase.CONNECTED;
            this.detail = "Conectado";
            this.agentId = connectedAgentId;
            this.printerCount = connectedPrinterCount;
            this.lastSeen = Instant.now();
        }
        publish();
    }

    public void recordJob(JobRecord record) {
        synchronized (lock) {
            jobs.addFirst(record);
            while (jobs.size() > MAX_JOBS) {
                jobs.removeLast();
            }
            this.lastSeen = Instant.now();
        }
        publish();
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(phase, detail, lastSeen, agentId, printerCount, new ArrayList<>(jobs));
        }
    }

    private void publish() {
        Snapshot s = snapshot();
        listeners.forEach(l -> l.accept(s));
    }
}
