package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackupSchedulerTest {

    @Test
    void tick_swallowsExceptionsSoTheScheduleSurvives() {
        AtomicInteger calls = new AtomicInteger();
        BackupScheduler scheduler = new BackupScheduler(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        scheduler.tick();
        scheduler.tick();

        assertThat(calls.get()).isEqualTo(2);
    }
}
