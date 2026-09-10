package com.vanter.emberagent.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vanter.emberagent.status.StatusHub.JobRecord;
import com.vanter.emberagent.status.StatusHub.Phase;
import com.vanter.emberagent.status.StatusHub.Snapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StatusHubTest {

    @Test
    void addListener_receivesImmediateSnapshot() {
        StatusHub hub = new StatusHub();
        AtomicReference<Snapshot> seen = new AtomicReference<>();

        hub.addListener(seen::set);

        assertEquals(Phase.UNPAIRED, seen.get().phase());
    }

    @Test
    void setConnected_flipsPhaseAndNotifiesListeners() {
        StatusHub hub = new StatusHub();
        List<Snapshot> seen = new ArrayList<>();
        hub.addListener(seen::add);

        hub.setConnected("agent-1", 3);

        Snapshot last = seen.get(seen.size() - 1);
        assertEquals(Phase.CONNECTED, last.phase());
        assertEquals("agent-1", last.agentId());
        assertEquals(3, last.printerCount());
        assertEquals("Conectado", last.detail());
    }

    @Test
    void setPhase_updatesPhaseAndDetail() {
        StatusHub hub = new StatusHub();

        hub.setPhase(Phase.RETRYING, "Reintentando en 10s");

        assertEquals(Phase.RETRYING, hub.snapshot().phase());
        assertEquals("Reintentando en 10s", hub.snapshot().detail());
    }

    @Test
    void recordJob_keepsNewestFirstAndEvictsBeyondMax() {
        StatusHub hub = new StatusHub();

        for (int i = 0; i < 25; i++) {
            hub.recordJob(new JobRecord(Instant.now(), "KITCHEN", "queue-" + i, "OK", null));
        }

        List<JobRecord> jobs = hub.snapshot().recentJobs();
        assertEquals(20, jobs.size());
        assertEquals("queue-24", jobs.get(0).queue());
        assertEquals("queue-5", jobs.get(19).queue());
    }

    @Test
    void snapshot_recentJobsIsDetachedFromLaterMutation() {
        StatusHub hub = new StatusHub();
        hub.recordJob(new JobRecord(Instant.now(), "BAR", "q", "OK", null));

        Snapshot first = hub.snapshot();
        hub.recordJob(new JobRecord(Instant.now(), "BAR", "q2", "OK", null));

        assertEquals(1, first.recentJobs().size());
        assertEquals(2, hub.snapshot().recentJobs().size());
    }
}
