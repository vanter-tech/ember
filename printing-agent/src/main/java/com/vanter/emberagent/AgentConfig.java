package com.vanter.emberagent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record AgentConfig(String backendBaseUrl, String apiKey) {

    public static AgentConfig load(Path propertiesFile) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propertiesFile)) {
            props.load(in);
        }
        String backendBaseUrl = require(props, "backend.base-url");
        String apiKey = require(props, "agent.api-key");
        return new AgentConfig(backendBaseUrl, apiKey);
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value;
    }
}
