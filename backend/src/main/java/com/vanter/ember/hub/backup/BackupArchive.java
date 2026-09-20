package com.vanter.ember.hub.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** The on-disk backup format: {@code manifest.json}, {@code postgres.dump}, {@code minio/**}. */
public final class BackupArchive {

    public record Manifest(String appVersion, String createdAt) {}

    static final String MANIFEST = "manifest.json";
    static final String DUMP = "postgres.dump";
    static final String MINIO_PREFIX = "minio/";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private BackupArchive() {}

    public static void create(Path zip, Path dumpFile, Path minioDir, Manifest manifest) throws IOException {
        create(zip, dumpFile, minioDir, manifest, p -> {});
    }

    /** {@code progress} receives 0..100, counted in entries (manifest + dump + one per MinIO file). */
    public static void create(Path zip, Path dumpFile, Path minioDir, Manifest manifest, IntConsumer progress)
            throws IOException {
        List<Path> mediaFiles = List.of();
        if (Files.isDirectory(minioDir)) {
            try (Stream<Path> files = Files.walk(minioDir)) {
                mediaFiles = files.filter(Files::isRegularFile).toList();
            }
        }
        int total = 2 + mediaFiles.size();
        int done = 0;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(MANIFEST));
            out.write(MAPPER.writeValueAsBytes(manifest));
            out.closeEntry();
            progress.accept(++done * 100 / total);

            out.putNextEntry(new ZipEntry(DUMP));
            Files.copy(dumpFile, out);
            out.closeEntry();
            progress.accept(++done * 100 / total);

            for (Path file : mediaFiles) {
                String rel = minioDir.relativize(file).toString().replace('\\', '/');
                out.putNextEntry(new ZipEntry(MINIO_PREFIX + rel));
                Files.copy(file, out);
                out.closeEntry();
                progress.accept(++done * 100 / total);
            }
        }
    }

    public static Manifest readManifest(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(MANIFEST);
            if (entry == null || zf.getEntry(DUMP) == null) {
                throw new IOException("El archivo no contiene manifest.json y postgres.dump.");
            }
            try (InputStream in = zf.getInputStream(entry)) {
                return MAPPER.readValue(in, Manifest.class);
            }
        }
    }

    public static void extractDump(Path zip, Path target) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry = zf.getEntry(DUMP);
            if (entry == null) {
                throw new IOException("El respaldo no contiene postgres.dump.");
            }
            try (InputStream in = zf.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Extracts every {@code minio/**} entry under {@code targetDir}; rejects path traversal. */
    public static void extractMinio(Path zip, Path targetDir) throws IOException {
        extractMinio(zip, targetDir, p -> {});
    }

    /** As above; {@code progress} receives 0..100, counted in extracted files. */
    public static void extractMinio(Path zip, Path targetDir, IntConsumer progress) throws IOException {
        Files.createDirectories(targetDir);
        Path root = targetDir.toAbsolutePath().normalize();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            int total = 0;
            Enumeration<? extends ZipEntry> counting = zf.entries();
            while (counting.hasMoreElements()) {
                if (isMediaFile(counting.nextElement())) {
                    total++;
                }
            }
            int done = 0;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!isMediaFile(entry)) {
                    continue;
                }
                Path out = root.resolve(entry.getName().substring(MINIO_PREFIX.length())).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Entrada de respaldo inválida: " + entry.getName());
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                progress.accept(++done * 100 / total);
            }
            if (total == 0) {
                progress.accept(100);
            }
        }
    }

    private static boolean isMediaFile(ZipEntry entry) {
        return !entry.isDirectory() && entry.getName().startsWith(MINIO_PREFIX);
    }
}
