package com.vanter.emberagent.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.AgentCredential;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlaintextCredentialStoreTest {

    @Test
    void saveThenLoad_roundTrips(@TempDir Path dir) {
        Path file = dir.resolve("credential.json");
        PlaintextCredentialStore store = new PlaintextCredentialStore(file);
        AgentCredential credential = new AgentCredential("plain-key", "https://api.example/v1");

        store.save(credential);
        Optional<AgentCredential> loaded = store.load();

        assertTrue(loaded.isPresent());
        assertEquals(credential, loaded.get());
        assertFalse(store.isEncrypted());
    }

    @Test
    void load_emptyWhenNoFile(@TempDir Path dir) {
        assertTrue(new PlaintextCredentialStore(dir.resolve("nope.json")).load().isEmpty());
    }

    @Test
    void clear_removesFile(@TempDir Path dir) {
        Path file = dir.resolve("credential.json");
        PlaintextCredentialStore store = new PlaintextCredentialStore(file);
        store.save(new AgentCredential("k", "u"));

        store.clear();

        assertTrue(store.load().isEmpty());
    }
}
