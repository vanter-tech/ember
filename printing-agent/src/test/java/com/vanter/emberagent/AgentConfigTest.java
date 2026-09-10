package com.vanter.emberagent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.credential.CredentialStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentConfigTest {

    /** In-memory {@link CredentialStore} so the resolution order can be tested without touching disk. */
    private static final class FakeStore implements CredentialStore {
        private AgentCredential held;

        FakeStore(AgentCredential held) {
            this.held = held;
        }

        @Override
        public Optional<AgentCredential> load() {
            return Optional.ofNullable(held);
        }

        @Override
        public void save(AgentCredential credential) {
            this.held = credential;
        }

        @Override
        public void clear() {
            this.held = null;
        }

        @Override
        public boolean isEncrypted() {
            return true;
        }
    }

    private Path writeProperties(Path dir) throws Exception {
        Path file = dir.resolve("agent.properties");
        Files.writeString(file, "backend.base-url=https://props.example/v1\nagent.api-key=props-key\n");
        return file;
    }

    @Test
    void resolve_storeHitWinsOverProperties(@TempDir Path dir) throws Exception {
        FakeStore store = new FakeStore(new AgentCredential("store-key", "https://store.example/v1"));

        Optional<AgentConfig> config = AgentConfig.resolve(store, writeProperties(dir));

        assertTrue(config.isPresent());
        assertEquals("store-key", config.get().apiKey());
        assertEquals("https://store.example/v1", config.get().backendBaseUrl());
    }

    @Test
    void resolve_fallsBackToPropertiesWhenStoreEmpty(@TempDir Path dir) throws Exception {
        Optional<AgentConfig> config = AgentConfig.resolve(new FakeStore(null), writeProperties(dir));

        assertTrue(config.isPresent());
        assertEquals("props-key", config.get().apiKey());
        assertEquals("https://props.example/v1", config.get().backendBaseUrl());
    }

    @Test
    void resolve_emptyWhenNeitherStoreNorPropertiesExist(@TempDir Path dir) {
        Optional<AgentConfig> config =
                AgentConfig.resolve(new FakeStore(null), dir.resolve("missing.properties"));

        assertTrue(config.isEmpty());
    }

    @Test
    void resolve_emptyWhenPropertiesFileArgIsNull() {
        assertTrue(AgentConfig.resolve(new FakeStore(null), null).isEmpty());
    }
}
