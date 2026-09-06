package com.vanter.ember.hub.dashboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies a customer-chosen {@code license.key} into the Hub's configured license path
 * (see {@link com.vanter.ember.hub.config.HubProperties#licenseFile()}). Plain file I/O,
 * pulled out of {@link HubDashboard} so it can be unit-tested without Swing.
 */
public final class LicenseFileInstaller {

    private LicenseFileInstaller() {}

    public static void install(Path source, Path destination) throws IOException {
        if (!Files.exists(source)) {
            throw new NoSuchFileException(source.toString());
        }
        Path from = source.toAbsolutePath().normalize();
        Path to = destination.toAbsolutePath().normalize();
        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "El archivo seleccionado ya es el license.key en uso: " + to);
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
