package com.vanter.ember.hub.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseFileInstallerTest {

    @Test
    void install_copiesSourceToDestination_creatingParentDirs(@TempDir Path tmp) throws Exception {
        Path source = Files.writeString(tmp.resolve("chosen.key"), "LICENSE-BODY");
        Path destination = tmp.resolve("data").resolve("EmberHub").resolve("license.key");

        LicenseFileInstaller.install(source, destination);

        assertThat(destination).exists();
        assertThat(Files.readString(destination)).isEqualTo("LICENSE-BODY");
    }

    @Test
    void install_overwritesAnExistingDestination(@TempDir Path tmp) throws Exception {
        Path source = Files.writeString(tmp.resolve("new.key"), "NEW");
        Path destination = Files.writeString(tmp.resolve("license.key"), "OLD");

        LicenseFileInstaller.install(source, destination);

        assertThat(Files.readString(destination)).isEqualTo("NEW");
    }

    @Test
    void install_throwsNoSuchFileException_whenSourceMissing(@TempDir Path tmp) {
        Path source = tmp.resolve("does-not-exist.key");
        Path destination = tmp.resolve("license.key");

        assertThatExceptionOfType(NoSuchFileException.class)
                .isThrownBy(() -> LicenseFileInstaller.install(source, destination));
    }

    @Test
    void install_throwsIllegalArgument_whenSourceEqualsDestination(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("license.key"), "BODY");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenseFileInstaller.install(file, file));
    }
}
