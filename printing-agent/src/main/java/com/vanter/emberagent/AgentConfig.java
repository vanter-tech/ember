package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public record AgentConfig(String backendBaseUrl, String apiKey) {

    /**
     * Resolution order (spec §2.2): the credential store first (the normal path after pairing),
     * then a hand-written {@code agent.properties} (dev / support override), then empty — in which
     * case the dashboard opens the pairing dialog.
     */
    public static Optional<AgentConfig> resolve(CredentialStore store, Path propertiesFile) {
        Optional<AgentConfig> fromStore = store.load()
                .map(c -> new AgentConfig(c.backendBaseUrl(), c.apiKey()));
        if (fromStore.isPresent()) {
            return fromStore;
        }
        if (propertiesFile != null && Files.exists(propertiesFile)) {
            try {
                return Optional.of(loadProperties(propertiesFile));
            } catch (IOException e) {
                throw new IllegalStateException("agent.properties unreadable: " + e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    /**
     * Legacy entry point still used by {@code Main}; T4 rewires {@code Main} onto
     * {@link #resolve(CredentialStore, Path)} and this shim goes away.
     */
    public static AgentConfig load(Path propertiesFile) throws IOException {
        return loadProperties(propertiesFile);
    }

    private static AgentConfig loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return new AgentConfig(require(props, "backend.base-url"), require(props, "agent.api-key"));
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value;
    }
}
