package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Hermetic: an empty {@link CredentialStore} plus a non-existent {@code agent.properties} means
 * {@link AgentConfig#resolve} returns empty, so the loop parks in {@code UNPAIRED} and never
 * touches the network.
 */
class AgentRunnerTest {

    private static final class EmptyStore implements CredentialStore {
        @Override
        public Optional<AgentCredential> load() {
            return Optional.empty();
        }

        @Override
        public void save(AgentCredential credential) {}

        @Override
        public void clear() {}

        @Override
        public boolean isEncrypted() {
            return false;
        }
    }

    @Test
    void runForever_withNoCredential_parksInUnpairedAndStopsCleanly() throws Exception {
        StatusHub status = new StatusHub();
        AgentRunner runner = new AgentRunner(
                new EmptyStore(), status, Path.of("does-not-exist-" + System.nanoTime() + ".properties"));

        Thread worker = new Thread(runner::runForever, "agent-runner-test");
        worker.setDaemon(true);
        worker.start();

        assertTrue(awaitPhase(status, StatusHub.Phase.UNPAIRED, Duration.ofSeconds(5)),
                "expected the loop to reach UNPAIRED");

        runner.stop();
        worker.join(Duration.ofSeconds(5).toMillis());
        assertFalse(worker.isAlive(), "runForever should exit promptly after stop()");
        assertEquals(StatusHub.Phase.UNPAIRED, status.snapshot().phase());
    }

    private static boolean awaitPhase(StatusHub status, StatusHub.Phase target, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            if (status.snapshot().phase() == target) {
                return true;
            }
            Thread.sleep(50);
        }
        return false;
    }
}
