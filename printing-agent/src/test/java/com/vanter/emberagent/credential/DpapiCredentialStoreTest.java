package com.vanter.emberagent.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vanter.emberagent.AgentCredential;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@EnabledOnOs(OS.WINDOWS)
class DpapiCredentialStoreTest {

    @Test
    void saveThenLoad_roundTripsAndEncryptsOnDisk(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("credential.bin");
        DpapiCredentialStore store = new DpapiCredentialStore(file);
        AgentCredential credential = new AgentCredential("dpapi-key", "https://api.example/v1");

        store.save(credential);

        assertTrue(Files.exists(file));
        byte[] onDisk = Files.readAllBytes(file);
        assertTrue(new String(onDisk).indexOf("dpapi-key") < 0, "raw key must not appear in ciphertext");

        Optional<AgentCredential> loaded = store.load();
        assertTrue(loaded.isPresent());
        assertEquals(credential, loaded.get());
        assertTrue(store.isEncrypted());
    }

    @Test
    void load_emptyWhenNoFile(@TempDir Path dir) {
        assertTrue(new DpapiCredentialStore(dir.resolve("nope.bin")).load().isEmpty());
    }

    @Test
    void clear_removesFile(@TempDir Path dir) {
        Path file = dir.resolve("credential.bin");
        DpapiCredentialStore store = new DpapiCredentialStore(file);
        store.save(new AgentCredential("k", "u"));

        store.clear();

        assertTrue(store.load().isEmpty());
    }
}
