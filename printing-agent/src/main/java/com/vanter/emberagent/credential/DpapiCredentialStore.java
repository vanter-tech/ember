package com.vanter.emberagent.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.AgentPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Windows-only credential store: the JSON bytes are wrapped with DPAPI at machine scope
 * ({@code CRYPTPROTECT_LOCAL_MACHINE}) so any process on this PC — but no other machine — can
 * read them back. Closes F-24 (API key was plaintext on disk).
 */
public final class DpapiCredentialStore implements CredentialStore {

    private static final Logger log = System.getLogger(DpapiCredentialStore.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    public DpapiCredentialStore() {
        this(AgentPaths.credentialFile());
    }

    DpapiCredentialStore(Path file) {
        this.file = file;
    }

    @Override
    public Optional<AgentCredential> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] protectedBytes = Files.readAllBytes(file);
            byte[] plain = Crypt32Util.cryptUnprotectData(
                    protectedBytes, WinCrypt.CRYPTPROTECT_LOCAL_MACHINE);
            return Optional.of(mapper.readValue(plain, AgentCredential.class));
        } catch (IOException e) {
            throw new UncheckedIOException("credential.bin unreadable", e);
        } catch (RuntimeException e) {
            log.log(Level.WARNING,
                    "credential.bin present but could not be decrypted on this machine: {0}",
                    e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(AgentCredential credential) {
        try {
            byte[] plain = mapper.writeValueAsBytes(credential);
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    plain, null, WinCrypt.CRYPTPROTECT_LOCAL_MACHINE, "EmberAgent", null);
            Files.createDirectories(file.getParent());
            Files.write(file, protectedBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write credential.bin", e);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean isEncrypted() {
        return true;
    }
}
