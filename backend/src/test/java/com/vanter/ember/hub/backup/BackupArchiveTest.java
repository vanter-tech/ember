package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupArchiveTest {

    @TempDir Path tmp;

    @Test
    void createThenReadExtractRoundTrip() throws IOException {
        Path dump = Files.writeString(tmp.resolve("postgres.dump"), "DUMP");
        Path minio = Files.createDirectories(tmp.resolve("minio-src/bucket"));
        Files.writeString(minio.resolve("logo.png"), "PNG");
        Path zip = tmp.resolve("b.zip");

        BackupArchive.create(zip, dump, tmp.resolve("minio-src"),
                new BackupArchive.Manifest("0.2.6.1", "2026-09-19T10:00:00Z"));

        assertThat(BackupArchive.readManifest(zip))
                .isEqualTo(new BackupArchive.Manifest("0.2.6.1", "2026-09-19T10:00:00Z"));
        BackupArchive.extractDump(zip, tmp.resolve("out.dump"));
        assertThat(Files.readString(tmp.resolve("out.dump"))).isEqualTo("DUMP");
        BackupArchive.extractMinio(zip, tmp.resolve("minio-out"));
        assertThat(Files.readString(tmp.resolve("minio-out/bucket/logo.png"))).isEqualTo("PNG");
    }

    @Test
    void missingMinioDirIsFine() throws IOException {
        Path dump = Files.writeString(tmp.resolve("postgres.dump"), "DUMP");
        Path zip = tmp.resolve("b.zip");

        BackupArchive.create(zip, dump, tmp.resolve("does-not-exist"),
                new BackupArchive.Manifest("1", "t"));

        BackupArchive.extractMinio(zip, tmp.resolve("minio-out"));
        assertThat(Files.isDirectory(tmp.resolve("minio-out"))).isTrue();
    }

    @Test
    void readManifest_notAZip_throws() throws IOException {
        Path notZip = Files.writeString(tmp.resolve("x.zip"), "hola");

        assertThatThrownBy(() -> BackupArchive.readManifest(notZip)).isInstanceOf(IOException.class);
    }

    @Test
    void readManifest_zipWithoutManifest_throws() throws IOException {
        Path zip = tmp.resolve("empty.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("otra-cosa.txt"));
            out.closeEntry();
        }

        assertThatThrownBy(() -> BackupArchive.readManifest(zip)).isInstanceOf(IOException.class);
    }

    @Test
    void extractMinio_rejectsZipSlip() throws IOException {
        Path zip = tmp.resolve("evil.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("minio/../../escape.txt"));
            out.write("x".getBytes());
            out.closeEntry();
        }

        assertThatThrownBy(() -> BackupArchive.extractMinio(zip, tmp.resolve("minio-out")))
                .isInstanceOf(IOException.class);
        assertThat(Files.exists(tmp.resolve("escape.txt"))).isFalse();
    }

    @Test
    void createReportsIncreasingProgressEndingAtOneHundred() throws IOException {
        Path dump = Files.writeString(tmp.resolve("postgres.dump"), "DUMP");
        Path minio = Files.createDirectories(tmp.resolve("minio-src"));
        for (String n : List.of("a.png", "b.png", "c.png")) {
            Files.writeString(minio.resolve(n), n);
        }
        List<Integer> seen = new ArrayList<>();

        BackupArchive.create(tmp.resolve("b.zip"), dump, minio, new BackupArchive.Manifest("1", "t"), seen::add);

        assertThat(seen).isNotEmpty().isSorted();
        assertThat(seen.get(seen.size() - 1)).isEqualTo(100);
    }

    @Test
    void extractMinioReportsProgressEndingAtOneHundred() throws IOException {
        Path dump = Files.writeString(tmp.resolve("postgres.dump"), "DUMP");
        Path minio = Files.createDirectories(tmp.resolve("minio-src"));
        Files.writeString(minio.resolve("a.png"), "a");
        Files.writeString(minio.resolve("b.png"), "b");
        Path zip = tmp.resolve("b.zip");
        BackupArchive.create(zip, dump, minio, new BackupArchive.Manifest("1", "t"));
        List<Integer> seen = new ArrayList<>();

        BackupArchive.extractMinio(zip, tmp.resolve("out"), seen::add);

        assertThat(seen).isNotEmpty().isSorted();
        assertThat(seen.get(seen.size() - 1)).isEqualTo(100);
    }
}
