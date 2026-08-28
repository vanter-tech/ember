package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
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

    @Test
    void save_thenLoad_roundTripsSuspendedSince() {
        Path stateFile = tempDir.resolve("suspended/hub-state.json");
        HubStateStore store = new HubStateStore(stateFile);
        Instant suspended = Instant.parse("2026-08-28T10:15:30Z");
        HubState state = new HubState("fp-x", UUID.randomUUID(), Instant.now(), suspended);

        store.save(state);

        HubState loaded = store.load().orElseThrow();
        assertThat(loaded.suspendedSince()).isEqualTo(suspended);
    }

    @Test
    void load_legacyStateFileWithoutSuspendedSince_readsNull() throws Exception {
        Path stateFile = tempDir.resolve("legacy/hub-state.json");
        Files.createDirectories(stateFile.getParent());
        HubStateStore store = new HubStateStore(stateFile);
        UUID restaurantId = UUID.randomUUID();
        String legacyJson = "{\"hardwareFingerprint\":\"fp-legacy\",\"restaurantId\":\""
                + restaurantId + "\",\"lastHeartbeatAt\":\"2026-08-01T00:00:00Z\"}";
        Files.writeString(stateFile, legacyJson);

        HubState loaded = store.load().orElseThrow();

        assertThat(loaded.hardwareFingerprint()).isEqualTo("fp-legacy");
        assertThat(loaded.suspendedSince()).isNull();
    }
}
