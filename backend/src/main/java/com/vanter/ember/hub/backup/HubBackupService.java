package com.vanter.ember.hub.backup;

import com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap;
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.HubOrchestrator;
import com.vanter.ember.hub.control.ServicePhase;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Lives in the sidecar's orchestrator layer (plain Java), never inside the Spring context: the
 * context is closed during a restore. One lock serialises backups and restores.
 */
public class HubBackupService implements HubBackup {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss");
    private static final Pattern REGULAR_BACKUP =
            Pattern.compile("^ember-backup-\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}\\.zip$");
    private static final Duration INTERVAL = Duration.ofHours(24);
    private static final Duration ERROR_BACKOFF = Duration.ofHours(1);

    private final HubProperties properties;
    private final HubOrchestrator orchestrator;
    private final BackupConfigStore store;
    private final PostgresTools tools;
    private final Supplier<String> versionSupplier;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    public HubBackupService(HubProperties properties, HubOrchestrator orchestrator, BackupConfigStore store,
                            PostgresTools tools, Supplier<String> versionSupplier, Clock clock) {
        this.properties = properties;
        this.orchestrator = orchestrator;
        this.store = store;
        this.tools = tools;
        this.versionSupplier = versionSupplier;
        this.clock = clock;
    }

    // --- HubBackup ---------------------------------------------------------------

    @Override
    public BackupStatus status() {
        BackupConfig config = store.loadConfig();
        Instant lastSuccess = parse(store.loadLastSuccessAt());
        String next = lastSuccess == null ? null : lastSuccess.plus(INTERVAL).toString();
        return new BackupStatus(store.loadLastRun(), next, config.destDir(),
                store.defaultDestDir().toString(), config.retention());
    }

    @Override
    public BackupConfig getConfig() {
        return store.loadConfig();
    }

    @Override
    public BackupConfig setConfig(BackupConfig config) {
        if (config == null || config.destDir() == null || config.destDir().isBlank()) {
            throw new IllegalArgumentException("destDir es obligatorio.");
        }
        if (config.retention() < 1) {
            throw new IllegalArgumentException("retention debe ser al menos 1.");
        }
        store.saveConfig(config);
        return config;
    }

    @Override
    public BackupSnapshot backupNow(String destDirOrNull) {
        return runBackup(destDirOrNull, false);
    }

    @Override
    public List<BackupSnapshot> listSnapshots() {
        Path dest = Path.of(store.loadConfig().destDir());
        if (!Files.isDirectory(dest)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dest)) {
            return files
                    .filter(p -> p.getFileName().toString().startsWith("ember-backup-")
                            && p.getFileName().toString().endsWith(".zip"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .map(this::readSnapshotOrBroken)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public BackupSnapshot inspect(String path) throws BackupException {
        BackupSnapshot snapshot = readSnapshot(Path.of(path));
        String current = versionSupplier.get();
        if (HubVersion.isNewer(snapshot.appVersion(), current)) {
            throw new BackupException(BackupException.INCOMPATIBLE,
                    "Este respaldo es de una versión más reciente de Ember (" + snapshot.appVersion()
                            + "). Actualiza el Hub antes de restaurar.");
        }
        return snapshot;
    }

    @Override
    public void restore(String path, boolean skipSafetySnapshot) throws BackupException {
        lock.lock();
        try {
            Path zip = Path.of(path);
            inspect(zip.toString()); // validation + version gate: nothing below runs if this throws

            String safetyHint = "";
            if (!skipSafetySnapshot) {
                try {
                    BackupSnapshot safety = doBackup(Path.of(store.loadConfig().destDir()), true);
                    safetyHint = " Tus datos anteriores están guardados en " + safety.path() + ".";
                } catch (BackupException e) {
                    throw new BackupException(BackupException.SAFETY_FAILED,
                            "No se pudo crear la copia de seguridad previa: " + e.getMessage(), e);
                }
            }

            orchestrator.stopAndWait();
            PortableDatabaseBootstrap db = new PortableDatabaseBootstrap(
                    properties.dataDir(), properties.postgresBinDir(), properties.postgresPort());
            Path work = null;
            try {
                work = Files.createTempDirectory("ember-hub-restore");
                Path dump = work.resolve(BackupArchive.DUMP);
                BackupArchive.extractDump(zip, dump);
                ensurePostgresRunning(db);
                tools.dropAndCreate();
                tools.restore(dump);
                db.stop();
                swapMinio(zip);
            } catch (IOException | PortableDatabaseException e) {
                stopQuietly(db);
                throw new BackupException(BackupException.RESTORE_FAILED,
                        "La restauración falló: " + e.getMessage() + safetyHint, e);
            } finally {
                deleteRecursively(work);
            }
            orchestrator.start(new String[0]);
        } finally {
            lock.unlock();
        }
    }

    /**
     * If Postgres refuses to start (corrupt data dir — the case this feature exists for) the data
     * dir is moved aside, never deleted, and a fresh cluster is initialised. A port conflict is
     * NOT treated as corruption.
     */
    private void ensurePostgresRunning(PortableDatabaseBootstrap db) throws PortableDatabaseException, IOException {
        int port = properties.postgresPort();
        if (portInUse(port)) {
            throw new PortableDatabaseException("El puerto " + port
                    + " ya está en uso. Cierra la otra aplicación que lo usa e intenta de nuevo.");
        }
        try {
            db.ensureRunning();
        } catch (PortableDatabaseException first) {
            Path dataDir = properties.dataDir();
            Path aside = dataDir.resolveSibling(
                    dataDir.getFileName() + ".corrupt-" + FILE_TS.format(LocalDateTime.now(clock)));
            Files.move(dataDir, aside);
            db.ensureRunning();
        }
    }

    /** Extract next to the live dir first, then swap, so a crash never leaves a half-written dir live. */
    private void swapMinio(Path zip) throws IOException {
        Path minio = properties.minioDataDir();
        String name = minio.getFileName().toString();
        Path staging = minio.resolveSibling(name + ".restoring");
        Path old = minio.resolveSibling(name + ".replaced");
        deleteRecursively(staging);
        deleteRecursively(old);
        BackupArchive.extractMinio(zip, staging);
        if (Files.exists(minio)) {
            Files.move(minio, old);
        }
        Files.move(staging, minio);
        deleteRecursively(old);
    }

    private static void stopQuietly(PortableDatabaseBootstrap db) {
        try {
            db.stop();
        } catch (PortableDatabaseException ignored) {
            // nothing more to do: the caller is already reporting the original failure
        }
    }

    private static boolean portInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName("localhost"))) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    // --- scheduling ----------------------------------------------------------------

    /** Called by the scheduler every tick; a no-op unless a backup is actually due. */
    public void runScheduledIfDue() {
        if (orchestrator.snapshot().postgres() != ServicePhase.RUNNING) {
            return;
        }
        if (!isDue(clock.instant(), parse(store.loadLastSuccessAt()), store.loadLastRun())) {
            return;
        }
        runBackup(null, true);
    }

    static boolean isDue(Instant now, Instant lastSuccess, BackupSnapshot lastRun) {
        if (lastSuccess != null && now.isBefore(lastSuccess.plus(INTERVAL))) {
            return false;
        }
        if (lastRun != null && BackupSnapshot.ERROR.equals(lastRun.status())) {
            Instant failedAt = parse(lastRun.createdAt());
            if (failedAt != null && now.isBefore(failedAt.plus(ERROR_BACKOFF))) {
                return false;
            }
        }
        return true;
    }

    // --- backup ---------------------------------------------------------------------

    BackupSnapshot runBackup(String destDirOverride, boolean scheduled) {
        if (!lock.tryLock()) {
            return BackupSnapshot.error(now(), "Ya hay un respaldo o una restauración en curso.");
        }
        try {
            BackupConfig config = store.loadConfig();
            Path dest = Path.of(destDirOverride != null && !destDirOverride.isBlank()
                    ? destDirOverride : config.destDir());
            BackupSnapshot result;
            try {
                result = doBackup(dest, false);
                store.saveLastSuccessAt(result.createdAt());
                if (scheduled) {
                    prune(dest, config.retention());
                }
            } catch (BackupException e) {
                result = BackupSnapshot.error(now(), e.getMessage());
            } catch (RuntimeException e) {
                result = BackupSnapshot.error(now(), "Error inesperado durante el respaldo: " + e.getMessage());
            }
            store.saveLastRun(result);
            return result;
        } finally {
            lock.unlock();
        }
    }

    /** Caller must hold {@link #lock}. Writes {@code .tmp} then atomically renames. */
    BackupSnapshot doBackup(Path destDir, boolean preRestore) throws BackupException {
        try {
            Files.createDirectories(destDir);
        } catch (IOException e) {
            throw notWritable(destDir, e);
        }
        if (!Files.isWritable(destDir)) {
            throw notWritable(destDir, null);
        }
        String name = "ember-backup-" + FILE_TS.format(LocalDateTime.now(clock))
                + (preRestore ? "-pre-restore" : "") + ".zip";
        Path finalFile = destDir.resolve(name);
        Path tmpFile = destDir.resolve(name + ".tmp");
        Path work = null;
        try {
            work = Files.createTempDirectory("ember-hub-backup");
            Path dump = work.resolve(BackupArchive.DUMP);
            tools.dump(dump);
            String createdAt = now();
            String version = versionSupplier.get();
            String manifestVersion = version == null ? "unknown" : version;
            BackupArchive.create(tmpFile, dump, properties.minioDataDir(),
                    new BackupArchive.Manifest(manifestVersion, createdAt));
            Files.move(tmpFile, finalFile, StandardCopyOption.ATOMIC_MOVE);
            return new BackupSnapshot(name, finalFile.toString(), createdAt, Files.size(finalFile),
                    BackupSnapshot.OK, null, manifestVersion, preRestore);
        } catch (IOException e) {
            throw new BackupException(BackupException.FAILED, "No se pudo crear el respaldo: " + e.getMessage(), e);
        } finally {
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException ignored) {
                // best effort: an orphaned .tmp is inert, never mistaken for a snapshot
            }
            deleteRecursively(work);
        }
    }

    private void prune(Path dest, int retention) {
        try (Stream<Path> files = Files.list(dest)) {
            List<Path> regular = files
                    .filter(p -> REGULAR_BACKUP.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            for (Path old : regular.stream().skip(retention).toList()) {
                Files.deleteIfExists(old);
            }
        } catch (IOException ignored) {
            // pruning is housekeeping; a failure must not turn a good backup into an error
        }
    }

    // --- helpers (package-private ones are reused by restore in Task 3) ----------------

    BackupSnapshot readSnapshot(Path file) throws BackupException {
        if (!Files.isRegularFile(file)) {
            throw new BackupException(BackupException.INVALID, "No se encontró el archivo de respaldo.");
        }
        try {
            BackupArchive.Manifest m = BackupArchive.readManifest(file);
            String name = file.getFileName().toString();
            return new BackupSnapshot(name, file.toString(), m.createdAt(), Files.size(file),
                    BackupSnapshot.OK, null, m.appVersion(), name.endsWith("-pre-restore.zip"));
        } catch (IOException e) {
            throw new BackupException(BackupException.INVALID, "El archivo no es un respaldo de Ember válido.", e);
        }
    }

    private BackupSnapshot readSnapshotOrBroken(Path file) {
        try {
            return readSnapshot(file);
        } catch (BackupException e) {
            long size = 0;
            try {
                size = Files.size(file);
            } catch (IOException ignored) {
                // size stays 0
            }
            return new BackupSnapshot(file.getFileName().toString(), file.toString(), null, size,
                    BackupSnapshot.ERROR, "Archivo dañado o no válido.", null, false);
        }
    }

    private BackupException notWritable(Path dir, Exception cause) {
        String msg = "No se pudo escribir en la carpeta de respaldo (" + dir
                + "). Si es una USB, revisa que esté conectada.";
        return cause == null
                ? new BackupException(BackupException.FAILED, msg)
                : new BackupException(BackupException.FAILED, msg, cause);
    }

    String now() {
        return clock.instant().toString();
    }

    static Instant parse(String iso) {
        try {
            return iso == null ? null : Instant.parse(iso);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
