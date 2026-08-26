package com.vanter.ember.hub.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class HubStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path stateFile;

    public HubStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    public Optional<HubState> load() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(stateFile.toFile(), HubState.class));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + stateFile, e);
        }
    }

    public void save(HubState state) {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + stateFile, e);
        }
    }
}
