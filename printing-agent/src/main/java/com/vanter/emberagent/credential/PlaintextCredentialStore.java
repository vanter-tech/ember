package com.vanter.emberagent.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Non-Windows fallback: the credential is written as clear JSON, with a WARN on every save.
 * DPAPI is Windows-only; macOS/Linux keep this path (spec global constraints).
 */
public final class PlaintextCredentialStore implements CredentialStore {

    private static final Logger log = System.getLogger(PlaintextCredentialStore.class.getName());

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    public PlaintextCredentialStore() {
        this(AgentPaths.plaintextCredentialFile());
    }

    PlaintextCredentialStore(Path file) {
        this.file = file;
    }

    @Override
    public Optional<AgentCredential> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(mapper.readValue(Files.readAllBytes(file), AgentCredential.class));
        } catch (IOException e) {
            throw new UncheckedIOException("credential.json unreadable", e);
        }
    }

    @Override
    public void save(AgentCredential credential) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(credential));
            log.log(Level.WARNING,
                    "Storing the API key UNENCRYPTED at {0} — DPAPI is Windows-only", file);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write credential.json", e);
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
        return false;
    }
}
