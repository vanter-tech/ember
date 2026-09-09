package com.vanter.emberagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the agent's on-disk locations. On Windows everything lives under
 * {@code %ProgramData%\EmberAgent} so it survives app updates (spec §2.5 layout); elsewhere it
 * falls back to {@code ~/.ember-agent}. Directories are created on first access.
 */
public final class AgentPaths {

    private AgentPaths() {}

    public static Path dataDir() {
        String programData = System.getenv("ProgramData");
        Path base = (programData != null && !programData.isBlank())
                ? Path.of(programData, "EmberAgent")
                : Path.of(System.getProperty("user.home"), ".ember-agent");
        return ensure(base);
    }

    public static Path credentialFile() {
        return dataDir().resolve("credential.bin");
    }

    public static Path plaintextCredentialFile() {
        return dataDir().resolve("credential.json");
    }

    public static Path stateFile() {
        return dataDir().resolve("agent-state.json");
    }

    public static Path logsDir() {
        return ensure(dataDir().resolve("logs"));
    }

    private static Path ensure(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + p, e);
        }
    }
}
