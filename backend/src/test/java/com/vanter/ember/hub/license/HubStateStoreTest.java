package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HubStateStoreTest {

    @TempDir Path tempDir;

    @Test
    void load_returnsEmptyWhenFileDoesNotExist() {
        HubStateStore store = new HubStateStore(tempDir.resolve("hub-state.json"));

        assertThat(store.load()).isEmpty();
    }

    @Test
    void save_thenLoad_roundTrips() {
        Path stateFile = tempDir.resolve("nested/hub-state.json");
        HubStateStore store = new HubStateStore(stateFile);
        HubState state = new HubState(
                "abc123", UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));

        store.save(state);
        Optional<HubState> loaded = store.load();

        assertThat(loaded).contains(state);
    }
}
