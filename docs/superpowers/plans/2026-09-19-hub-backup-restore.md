# Ember Hub Backup & Restore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Per CLAUDE.md §7: one task per context, `/clear` between tasks.

**Goal:** From the Ember Hub dashboard the owner can back up (modal: USB recommended vs. this machine), upload a backup file and restore to that earlier state, with restaurant settings included.

**Architecture:** Plain-Java `hub/backup/` package living in the sidecar's *orchestrator layer* (NOT the Spring context, which is closed during a restore). A backup is a single `ember-backup-<ts>.zip` (`manifest.json` + `postgres.dump` + `minio/**`) built with the bundled `pg_dump`. Restore stops the app, (re)builds Postgres, runs `pg_restore`, swaps MinIO's dir, restarts. `HubControlServer` gets 6 loopback routes; a React `BackupCard` + `Modal` in `ember-hub/ui` drive them; the native file/folder dialogs come from the already-installed `@tauri-apps/plugin-dialog` (no Rust change).

**Tech Stack:** Java 17, JDK `HttpServer`, Jackson, JUnit 5/AssertJ, portable Postgres 16.6 (`pg_dump`/`pg_restore`/`dropdb`/`createdb`), React 19 + Vitest + Testing Library, pnpm.

**Spec:** `docs/superpowers/specs/2026-09-14-hub-backup-restore-design.md` (plus the 2026-09-19 additions below).

## Deviations from the spec (decided while planning, with reason)

1. **Single `.zip` file per backup, not a folder** — the user wants to *upload a file* to restore, and a file is trivially copied to a USB.
2. **Service is plain Java in the orchestrator layer, scheduler is a `ScheduledExecutorService`, not Spring `@Scheduled`** — the Spring context is closed during restore; a bean inside it would kill itself.
3. **No Rust command** — `open({ directory: true })` from `@tauri-apps/plugin-dialog` (already used for `license.key`, `dialog:default` already granted) covers the folder picker.
4. **Schedule = "every 24 h since the last successful backup, checked every 15 min"**, not "daily at a fixed hour" — restaurant PCs are often off at night, a fixed 03:00 would never fire. Skipped while Postgres is not RUNNING.
5. **Manual and pre-restore backups are never auto-pruned**; only scheduled runs prune (a manual backup to a USB must not delete the USB's older files).
6. **Restore with a broken Postgres data dir** (the main "se corrompió" case): the data dir is moved aside to `<dir>.corrupt-<ts>` and a fresh cluster is initialised before `pg_restore`. If the pre-restore safety snapshot cannot be made, restore stops with `BACKUP_SAFETY_FAILED` and the UI offers "continuar sin copia previa".
7. **One `BackupException(code, message)`** replaces the spec's `BackupIncompatibleException`. Codes: `BACKUP_INCOMPATIBLE`, `BACKUP_SAFETY_FAILED`, `BACKUP_INVALID`, `BACKUP_FAILED`, `BACKUP_RESTORE_FAILED`.
8. **Two extra routes**: `GET /api/backup/list`, `POST /api/backup/inspect` (validates an uploaded file before the confirm dialog). Status also returns `defaultDestDir` ("Esta máquina").
9. **Settings are covered by `pg_dump`** (restaurant settings live in Postgres); no separate mechanism. `hub-state.json`/license stay excluded (hardware-bound).

## Global Constraints

- Backend: `cd backend && ./mvnw test` (never `mvn`); target Java 17; no new dependency.
- Hub UI: `pnpm` only (never npm) in `ember-hub/ui`; verify with `pnpm test` and `pnpm run build`.
- Spanish user-facing copy, matching existing Hub UI.
- **Do NOT use Kafka.** Do not touch F-21 (hardcoded Postgres/MinIO creds).
- Commits (CLAUDE.md §4): one commit per task, `git add <specific paths>` only (never `-A`/`.`), conventional lowercase message, **no `Co-Authored-By`/AI signature** (user memory + CLAUDE.md override any default attribution). Each task also adds `reports/NNN-…md` (Identification / Objective / Modified Files / What Changed? / Why It Changed?) and updates `PROGRESS.md` (health + checkboxes). Report numbers below (509–514) are the expected next numbers; use the next free one at commit time.
- Work on a new branch off `main`: `git switch -c feat/hub-backup-restore main`.
- Real-Postgres tests are guarded by `Assumptions.assumeTrue` on `../ember-hub/.vendor-cache/postgres/bin/pg_dump.exe` (skipped where the vendor cache is absent).

## File Structure

Backend (`backend/src/main/java/com/vanter/ember/hub/backup/`, new):
- `BackupConfig.java`, `BackupSnapshot.java`, `BackupStatus.java`, `BackupException.java` — data + error type.
- `HubVersion.java` — numeric version compare + current version.
- `BackupConfigStore.java` — JSON persistence (`hub-backup.json`, outside Postgres).
- `BackupArchive.java` — zip create / manifest read / zip-slip-safe extract.
- `PostgresTools.java` — process wrapper for `pg_dump`/`dropdb`/`createdb`/`pg_restore`.
- `HubBackup.java` — interface the control server talks to.
- `HubBackupService.java` — backup / list / inspect / restore / status / scheduled-if-due.
- `BackupScheduler.java` — 15-min tick.

Modified: `hub/control/HubOrchestrator.java` + `DefaultHubOrchestrator.java` (`stopAndWait`), `hub/control/HubControlServer.java` (6 routes), `EmberApplication.java` (wiring), test `HubControlServerTest.java`.

Frontend (`ember-hub/ui/src/`): `lib/types.ts`, `lib/api.ts`, `components/Modal.tsx`, `components/BackupCard.tsx` (+`.test.tsx`), `components/Dashboard.tsx`; docs `ember-hub/VERIFY.md`.

---

### Task 1: Pure building blocks — models, `HubVersion`, `BackupConfigStore`, `BackupArchive`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/backup/{BackupConfig,BackupSnapshot,BackupStatus,BackupException,HubVersion,BackupConfigStore,BackupArchive}.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/backup/{HubVersionTest,BackupConfigStoreTest,BackupArchiveTest}.java`

**Interfaces:**
- Produces:
  - `record BackupConfig(String destDir, int retention)`; `BackupConfig.DEFAULT_RETENTION = 7`.
  - `record BackupSnapshot(String id, String path, String createdAt, long sizeBytes, String status, String errorMessage, String appVersion, boolean preRestoreSafety)` with constants `OK`, `ERROR` and `static BackupSnapshot error(String createdAt, String message)`. `createdAt` is an ISO-8601 string (the control server's plain `ObjectMapper` has no JavaTime module).
  - `record BackupStatus(BackupSnapshot lastRun, String nextScheduledRun, String destDir, String defaultDestDir, int retention)`.
  - `class BackupException extends Exception` with `code()`; constants `INCOMPATIBLE, SAFETY_FAILED, INVALID, FAILED, RESTORE_FAILED` (values are the `BACKUP_*` strings above).
  - `HubVersion.isNewer(String candidate, String current)` (false when either is null/unparseable); `HubVersion.current()` → `String` or `null`.
  - `BackupConfigStore(Path file, Path defaultDestDir)`: `BackupConfig loadConfig()`, `saveConfig(BackupConfig)`, `BackupSnapshot loadLastRun()`, `saveLastRun(BackupSnapshot)`, `String loadLastSuccessAt()`, `saveLastSuccessAt(String)`, `Path defaultDestDir()`.
  - `BackupArchive.Manifest(String appVersion, String createdAt)`; `static void create(Path zip, Path dumpFile, Path minioDir, Manifest m) throws IOException`; `static Manifest readManifest(Path zip) throws IOException`; `static void extractDump(Path zip, Path target) throws IOException`; `static void extractMinio(Path zip, Path targetDir) throws IOException`.

- [ ] **Step 1: Write the failing tests**

`HubVersionTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HubVersionTest {

    @Test
    void newerPatchIsNewer() {
        assertThat(HubVersion.isNewer("0.2.7", "0.2.6")).isTrue();
    }

    @Test
    void fourthComponentCounts() {
        assertThat(HubVersion.isNewer("0.2.6.1", "0.2.6")).isTrue();
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6.1")).isFalse();
    }

    @Test
    void numericNotLexicographic() {
        assertThat(HubVersion.isNewer("0.10.0", "0.9.0")).isTrue();
    }

    @Test
    void equalIsNotNewer() {
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6")).isFalse();
        assertThat(HubVersion.isNewer("0.2.6", "0.2.6.0")).isFalse();
    }

    @Test
    void unknownOrUnparseableNeverBlocks() {
        assertThat(HubVersion.isNewer(null, "0.2.6")).isFalse();
        assertThat(HubVersion.isNewer("0.2.6", null)).isFalse();
        assertThat(HubVersion.isNewer("abc", "0.2.6")).isFalse();
    }
}
```

`BackupConfigStoreTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupConfigStoreTest {

    @TempDir Path tmp;

    private BackupConfigStore store() {
        return new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups"));
    }

    @Test
    void missingFile_returnsDefaults() {
        BackupConfig config = store().loadConfig();

        assertThat(config.destDir()).isEqualTo(tmp.resolve("backups").toString());
        assertThat(config.retention()).isEqualTo(7);
        assertThat(store().loadLastRun()).isNull();
        assertThat(store().loadLastSuccessAt()).isNull();
    }

    @Test
    void roundTripsConfigAndLastRunIndependently() {
        BackupConfigStore store = store();
        store.saveConfig(new BackupConfig("E:\\respaldos", 3));
        BackupSnapshot run = new BackupSnapshot("a.zip", "E:\\respaldos\\a.zip", "2026-09-19T10:00:00Z",
                10, BackupSnapshot.OK, null, "0.2.6.1", false);
        store.saveLastRun(run);
        store.saveLastSuccessAt("2026-09-19T10:00:00Z");

        BackupConfigStore reloaded = store();
        assertThat(reloaded.loadConfig()).isEqualTo(new BackupConfig("E:\\respaldos", 3));
        assertThat(reloaded.loadLastRun()).isEqualTo(run);
        assertThat(reloaded.loadLastSuccessAt()).isEqualTo("2026-09-19T10:00:00Z");
    }

    @Test
    void savingConfigKeepsLastRun() {
        BackupConfigStore store = store();
        store.saveLastSuccessAt("2026-09-19T10:00:00Z");
        store.saveConfig(new BackupConfig("E:\\x", 5));

        assertThat(store().loadLastSuccessAt()).isEqualTo("2026-09-19T10:00:00Z");
    }
}
```

`BackupArchiveTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest="HubVersionTest,BackupConfigStoreTest,BackupArchiveTest"`
Expected: compilation FAIL (`cannot find symbol` for `HubVersion`, `BackupConfigStore`, `BackupArchive`, `BackupConfig`, `BackupSnapshot`).

- [ ] **Step 3: Write the implementation**

`BackupConfig.java`:
```java
package com.vanter.ember.hub.backup;

/** {@code destDir} is where scheduled backups are written; a manual backup may target another folder. */
public record BackupConfig(String destDir, int retention) {
    public static final int DEFAULT_RETENTION = 7;
}
```

`BackupSnapshot.java`:
```java
package com.vanter.ember.hub.backup;

/** One backup file (or one failed attempt). {@code createdAt} is ISO-8601 text on purpose. */
public record BackupSnapshot(
        String id,
        String path,
        String createdAt,
        long sizeBytes,
        String status,
        String errorMessage,
        String appVersion,
        boolean preRestoreSafety) {

    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    public static BackupSnapshot error(String createdAt, String message) {
        return new BackupSnapshot(null, null, createdAt, 0, ERROR, message, null, false);
    }
}
```

`BackupStatus.java`:
```java
package com.vanter.ember.hub.backup;

/** {@code defaultDestDir} is the "Esta máquina" folder; {@code destDir} is the scheduled target. */
public record BackupStatus(
        BackupSnapshot lastRun,
        String nextScheduledRun,
        String destDir,
        String defaultDestDir,
        int retention) {}
```

`BackupException.java`:
```java
package com.vanter.ember.hub.backup;

public class BackupException extends Exception {

    public static final String INCOMPATIBLE = "BACKUP_INCOMPATIBLE";
    public static final String SAFETY_FAILED = "BACKUP_SAFETY_FAILED";
    public static final String INVALID = "BACKUP_INVALID";
    public static final String FAILED = "BACKUP_FAILED";
    public static final String RESTORE_FAILED = "BACKUP_RESTORE_FAILED";

    private final String code;

    public BackupException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BackupException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
```

`HubVersion.java`:
```java
package com.vanter.ember.hub.backup;

import com.vanter.ember.EmberApplication;

/** Dependency-free {@code MAJOR.MINOR.PATCH[.HOTFIX]} comparison (no semver lib in the codebase). */
public final class HubVersion {

    private HubVersion() {}

    /** The running jar's {@code Implementation-Version}; {@code null} outside a packaged jar. */
    public static String current() {
        return EmberApplication.class.getPackage().getImplementationVersion();
    }

    /** True only when both parse and {@code candidate} is strictly greater. Unknown never blocks. */
    public static boolean isNewer(String candidate, String current) {
        int[] a = parse(candidate);
        int[] b = parse(current);
        if (a == null || b == null) {
            return false;
        }
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x > y;
            }
        }
        return false;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String[] parts = version.trim().split("\\.");
        int[] out = new int[parts.length];
        try {
            for (int i = 0; i < parts.length; i++) {
                out[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        return out;
    }
}
```

`BackupConfigStore.java`:
```java
package com.vanter.ember.hub.backup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists backup config + last-run info as a small JSON file next to {@code hub-state.json} —
 * deliberately NOT in Postgres: a restore replaces the whole database and must never be able to
 * erase the backup system's own state.
 */
public final class BackupConfigStore {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Persisted(BackupConfig config, BackupSnapshot lastRun, String lastSuccessAt) {}

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path file;
    private final Path defaultDestDir;

    public BackupConfigStore(Path file, Path defaultDestDir) {
        this.file = file;
        this.defaultDestDir = defaultDestDir;
    }

    public Path defaultDestDir() {
        return defaultDestDir;
    }

    public synchronized BackupConfig loadConfig() {
        BackupConfig saved = read().config();
        if (saved == null || saved.destDir() == null || saved.destDir().isBlank()) {
            return new BackupConfig(defaultDestDir.toString(),
                    saved == null || saved.retention() < 1 ? BackupConfig.DEFAULT_RETENTION : saved.retention());
        }
        return saved;
    }

    public synchronized void saveConfig(BackupConfig config) {
        Persisted p = read();
        write(new Persisted(config, p.lastRun(), p.lastSuccessAt()));
    }

    public synchronized BackupSnapshot loadLastRun() {
        return read().lastRun();
    }

    public synchronized void saveLastRun(BackupSnapshot lastRun) {
        Persisted p = read();
        write(new Persisted(p.config(), lastRun, p.lastSuccessAt()));
    }

    public synchronized String loadLastSuccessAt() {
        return read().lastSuccessAt();
    }

    public synchronized void saveLastSuccessAt(String isoInstant) {
        Persisted p = read();
        write(new Persisted(p.config(), p.lastRun(), isoInstant));
    }

    private Persisted read() {
        if (!Files.exists(file)) {
            return new Persisted(null, null, null);
        }
        try {
            return MAPPER.readValue(file.toFile(), Persisted.class);
        } catch (IOException e) {
            return new Persisted(null, null, null); // corrupt/unreadable file → fall back to defaults
        }
    }

    private void write(Persisted p) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(file.toFile(), p);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + file, e);
        }
    }
}
```

`BackupArchive.java`:
```java
package com.vanter.ember.hub.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
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
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry(MANIFEST));
            out.write(MAPPER.writeValueAsBytes(manifest));
            out.closeEntry();

            out.putNextEntry(new ZipEntry(DUMP));
            Files.copy(dumpFile, out);
            out.closeEntry();

            if (Files.isDirectory(minioDir)) {
                try (Stream<Path> files = Files.walk(minioDir)) {
                    for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                        String rel = minioDir.relativize(file).toString().replace('\\', '/');
                        out.putNextEntry(new ZipEntry(MINIO_PREFIX + rel));
                        Files.copy(file, out);
                        out.closeEntry();
                    }
                }
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
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    /** Extracts every {@code minio/**} entry under {@code targetDir}; rejects path traversal. */
    public static void extractMinio(Path zip, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        Path root = targetDir.toAbsolutePath().normalize();
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().startsWith(MINIO_PREFIX)) {
                    continue;
                }
                Path out = root.resolve(entry.getName().substring(MINIO_PREFIX.length())).normalize();
                if (!out.startsWith(root)) {
                    throw new IOException("Entrada de respaldo inválida: " + entry.getName());
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zf.getInputStream(entry)) {
                    Files.copy(in, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest="HubVersionTest,BackupConfigStoreTest,BackupArchiveTest"`
Expected: PASS (13 tests). If a Jackson record error appears for `Persisted`/`Manifest`, confirm `jackson-databind` ≥ 2.15 (Spring Boot 3.5 ships 2.19 — it should work with no change).

- [ ] **Step 5: Report + commit**

Write `reports/509-task-hub-backup-1-building-blocks.md`, update `PROGRESS.md` (Current Active Task = HUB-BACKUP-RESTORE plan Task 1 done; add checkbox list under Task Queue Status as `### HUB-BACKUP-RESTORE` with Tasks 1–6 unchecked, tick Task 1).
```bash
git add backend/src/main/java/com/vanter/ember/hub/backup backend/src/test/java/com/vanter/ember/hub/backup PROGRESS.md reports/509-task-hub-backup-1-building-blocks.md docs/superpowers/plans/2026-09-19-hub-backup-restore.md
git commit -m "feat(hub): backup archive format, config store and version compare"
```


### Task 2: `PostgresTools` + `HubBackupService` (backup, list, inspect, status, prune, scheduled-if-due)

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/backup/{PostgresTools,HubBackup,HubBackupService}.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/backup/{FakeHubOrchestrator,HubBackupServiceTest}.java`

**Interfaces:**
- Consumes (Task 1): `BackupConfig`, `BackupSnapshot`, `BackupStatus`, `BackupException`, `HubVersion.isNewer`, `BackupConfigStore`, `BackupArchive`. Existing: `HubOrchestrator.snapshot().postgres()` (`ServicePhase`), `HubProperties` (`minioDataDir()`, `dataDir()`, `postgresBinDir()`, `postgresPort()`).
- Produces:
  - `class PostgresTools(Path binDir, int port)` (non-final; methods overridable in tests): `void dump(Path outFile) throws IOException`, `void dropAndCreate() throws IOException`, `void restore(Path dumpFile) throws IOException`.
  - `interface HubBackup`: `BackupStatus status()`, `BackupConfig getConfig()`, `BackupConfig setConfig(BackupConfig)` (throws `IllegalArgumentException` on invalid), `BackupSnapshot backupNow(String destDirOrNull)` (never throws), `List<BackupSnapshot> listSnapshots()`, `BackupSnapshot inspect(String path) throws BackupException` (validates + version-compat).
  - `HubBackupService(HubProperties, HubOrchestrator, BackupConfigStore, PostgresTools, Supplier<String> version, Clock)` implements `HubBackup`; plus `void runScheduledIfDue()`, package-private `BackupSnapshot runBackup(String destDirOrNull, boolean scheduled)`, `BackupSnapshot doBackup(Path destDir, boolean preRestore) throws BackupException` (caller holds the lock), `BackupSnapshot readSnapshot(Path)`, `static void deleteRecursively(Path)`, `static boolean isDue(Instant now, Instant lastSuccess, BackupSnapshot lastRun)`.
  - Task 3 adds `restore(...)` to both.

- [ ] **Step 1: Write the failing tests**

`FakeHubOrchestrator.java`:
```java
package com.vanter.ember.hub.backup;

import com.vanter.ember.hub.control.HubOrchestrator;
import com.vanter.ember.hub.control.ServicePhase;
import java.nio.file.Path;

/** Test double shared by the backup tests. */
final class FakeHubOrchestrator implements HubOrchestrator {
    ServicePhase postgres = ServicePhase.RUNNING;

    @Override public void start(String[] launchArgs) {}
    @Override public void stop() {}
    @Override public void installLicense(Path source) {}
    @Override public void removeLicense() {}

    @Override
    public HubStatusSnapshot snapshot() {
        return new HubStatusSnapshot(postgres, null, ServicePhase.STOPPED, null, ServicePhase.STOPPED, null,
                new LicenseSnapshot(LicenseSnapshot.NONE, null, null), 8080);
    }
}
```

`HubBackupServiceTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.ServicePhase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HubBackupServiceTest {

    static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TempDir Path tmp;
    MutableClock clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
    FakeHubOrchestrator orchestrator = new FakeHubOrchestrator();
    BackupConfigStore store;
    HubBackupService service;
    Path minio;
    boolean dumpFails;
    int dumpCalls;
    String[] version = {"0.2.6.1"};

    @BeforeEach
    void setUp() throws IOException {
        minio = Files.createDirectories(tmp.resolve("minio"));
        Files.writeString(minio.resolve("a.txt"), "A");
        store = new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups"));
        PostgresTools tools = new PostgresTools(tmp.resolve("no-bin"), 1) {
            @Override
            public void dump(Path out) throws IOException {
                dumpCalls++;
                if (dumpFails) {
                    throw new IOException("sin conexión");
                }
                Files.writeString(out, "DUMP");
            }
        };
        HubProperties props = new HubProperties(tmp.resolve("pg-data"), tmp.resolve("no-bin"),
                tmp.resolve("license.key"), tmp.resolve("pub.der"), tmp.resolve("hub-state.json"),
                5432, 8080, "", minio, tmp.resolve("minio-bin"), 9000);
        service = new HubBackupService(props, orchestrator, store, tools, () -> version[0], clock);
    }

    @Test
    void backupNow_writesZipWithManifestAndRecordsRun() throws Exception {
        BackupSnapshot snap = service.backupNow(null);

        assertThat(snap.status()).isEqualTo(BackupSnapshot.OK);
        assertThat(snap.id()).isEqualTo("ember-backup-2026-09-19T10-00-00.zip");
        assertThat(Path.of(snap.path())).exists();
        assertThat(BackupArchive.readManifest(Path.of(snap.path())).appVersion()).isEqualTo("0.2.6.1");
        assertThat(store.loadLastRun()).isEqualTo(snap);
        assertThat(service.status().nextScheduledRun()).isEqualTo("2026-09-20T10:00:00Z");
        assertThat(service.status().defaultDestDir()).isEqualTo(tmp.resolve("backups").toString());
    }

    @Test
    void backupNow_customDestDir_usesIt() {
        Path usb = tmp.resolve("usb");

        BackupSnapshot snap = service.backupNow(usb.toString());

        assertThat(Path.of(snap.path()).getParent()).isEqualTo(usb);
    }

    @Test
    void backupNow_unwritableDest_returnsErrorAndNeverThrows() throws Exception {
        Path file = Files.writeString(tmp.resolve("afile"), "x");

        BackupSnapshot snap = service.backupNow(file.resolve("sub").toString());

        assertThat(snap.status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(snap.errorMessage()).contains("No se pudo escribir");
        assertThat(store.loadLastRun().status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(store.loadLastSuccessAt()).isNull();
    }

    @Test
    void backupNow_dumpFails_returnsErrorAndLeavesNoFiles() throws Exception {
        dumpFails = true;

        BackupSnapshot snap = service.backupNow(null);

        assertThat(snap.status()).isEqualTo(BackupSnapshot.ERROR);
        assertThat(snap.errorMessage()).contains("sin conexión");
        try (Stream<Path> s = Files.list(tmp.resolve("backups"))) {
            assertThat(s.count()).isZero();
        }
    }

    @Test
    void scheduledRun_prunesBeyondRetention_keepingPreRestoreSnapshots() throws Exception {
        Path dest = Files.createDirectories(tmp.resolve("backups"));
        store.saveConfig(new BackupConfig(dest.toString(), 2));
        for (String d : List.of("11", "12", "13", "14")) {
            Files.writeString(dest.resolve("ember-backup-2026-09-" + d + "T10-00-00.zip"), "old");
        }
        Files.writeString(dest.resolve("ember-backup-2026-09-01T10-00-00-pre-restore.zip"), "safe");

        service.runBackup(null, true);

        assertThat(dest.resolve("ember-backup-2026-09-19T10-00-00.zip")).exists();
        assertThat(dest.resolve("ember-backup-2026-09-14T10-00-00.zip")).exists();
        assertThat(dest.resolve("ember-backup-2026-09-13T10-00-00.zip")).doesNotExist();
        assertThat(dest.resolve("ember-backup-2026-09-11T10-00-00.zip")).doesNotExist();
        assertThat(dest.resolve("ember-backup-2026-09-01T10-00-00-pre-restore.zip")).exists();
    }

    @Test
    void manualBackup_neverPrunes() throws Exception {
        Path dest = Files.createDirectories(tmp.resolve("backups"));
        store.saveConfig(new BackupConfig(dest.toString(), 1));
        Files.writeString(dest.resolve("ember-backup-2026-09-11T10-00-00.zip"), "old");

        service.backupNow(null);

        assertThat(dest.resolve("ember-backup-2026-09-11T10-00-00.zip")).exists();
    }

    @Test
    void listSnapshots_newestFirst_andFlagsBrokenFile() throws Exception {
        service.backupNow(null);
        clock.advance(Duration.ofDays(1));
        service.backupNow(null);
        Files.writeString(tmp.resolve("backups/ember-backup-2026-01-01T00-00-00.zip"), "garbage");

        List<BackupSnapshot> list = service.listSnapshots();

        assertThat(list).extracting(BackupSnapshot::id).containsExactly(
                "ember-backup-2026-09-20T10-00-00.zip",
                "ember-backup-2026-09-19T10-00-00.zip",
                "ember-backup-2026-01-01T00-00-00.zip");
        assertThat(list.get(2).status()).isEqualTo(BackupSnapshot.ERROR);
    }

    @Test
    void inspect_validFile_returnsSnapshot() throws Exception {
        BackupSnapshot made = service.backupNow(null);

        BackupSnapshot inspected = service.inspect(made.path());

        assertThat(inspected.appVersion()).isEqualTo("0.2.6.1");
        assertThat(inspected.createdAt()).isEqualTo(made.createdAt());
    }

    @Test
    void inspect_notABackup_throwsInvalid() throws Exception {
        Path junk = Files.writeString(tmp.resolve("junk.zip"), "hola");

        assertThatThrownBy(() -> service.inspect(junk.toString()))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.code()).isEqualTo(BackupException.INVALID));
    }

    @Test
    void inspect_newerThanRunningHub_throwsIncompatible() {
        version[0] = "0.9.0";
        BackupSnapshot made = service.backupNow(null);
        version[0] = "0.2.6.1";

        assertThatThrownBy(() -> service.inspect(made.path()))
                .isInstanceOfSatisfying(BackupException.class, e -> {
                    assertThat(e.code()).isEqualTo(BackupException.INCOMPATIBLE);
                    assertThat(e.getMessage()).contains("0.9.0");
                });
    }

    @Test
    void scheduledIfDue_skipsWhenPostgresNotRunning() {
        orchestrator.postgres = ServicePhase.STOPPED;

        service.runScheduledIfDue();

        assertThat(dumpCalls).isZero();
    }

    @Test
    void scheduledIfDue_runsOnceThenWaits24hSinceLastSuccess() {
        service.runScheduledIfDue();
        clock.advance(Duration.ofHours(1));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(1);

        clock.advance(Duration.ofHours(24));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(2);
    }

    @Test
    void scheduledIfDue_afterFailure_backsOffOneHour() {
        dumpFails = true;
        service.runScheduledIfDue();
        clock.advance(Duration.ofMinutes(30));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(1);

        clock.advance(Duration.ofMinutes(31));
        service.runScheduledIfDue();
        assertThat(dumpCalls).isEqualTo(2);
    }

    @Test
    void setConfig_rejectsInvalidValues() {
        assertThatThrownBy(() -> service.setConfig(new BackupConfig(" ", 7)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.setConfig(new BackupConfig("E:\\x", 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(service.setConfig(new BackupConfig("E:\\x", 3))).isEqualTo(new BackupConfig("E:\\x", 3));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=HubBackupServiceTest`
Expected: compilation FAIL (`PostgresTools`, `HubBackupService` not found).

- [ ] **Step 3: Write `PostgresTools.java` and `HubBackup.java`**

`PostgresTools.java`:
```java
package com.vanter.ember.hub.backup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Thin process wrapper over the {@code pg_dump}/{@code dropdb}/{@code createdb}/{@code pg_restore}
 * binaries the portable Postgres already ships. Same user/host/auth assumptions as
 * {@code PortableDatabaseBootstrap} (user {@code ember}, database {@code ember}, loopback, trust).
 * Non-final so tests can stub the process calls.
 */
public class PostgresTools {

    private static final String USER = "ember";
    private static final String HOST = "127.0.0.1";
    private static final String DB = "ember";

    private final Path binDir;
    private final int port;

    public PostgresTools(Path binDir, int port) {
        this.binDir = binDir;
        this.port = port;
    }

    public void dump(Path outFile) throws IOException {
        run("pg_dump", "-Fc", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-f", outFile.toString(), DB);
    }

    public void dropAndCreate() throws IOException {
        run("dropdb", "--force", "--if-exists", "-U", USER, "-h", HOST, "-p", String.valueOf(port), DB);
        run("createdb", "-U", USER, "-h", HOST, "-p", String.valueOf(port), DB);
    }

    public void restore(Path dumpFile) throws IOException {
        run("pg_restore", "--no-owner", "-U", USER, "-h", HOST, "-p", String.valueOf(port),
                "-d", DB, dumpFile.toString());
    }

    private void run(String tool, String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(binDir.resolve(tool).toString());
        cmd.addAll(Arrays.asList(args));
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(tool + " fue interrumpido.", e);
        }
        if (exit != 0) {
            throw new IOException(tool + " falló (código " + exit + "): " + output.trim());
        }
    }
}
```

`HubBackup.java`:
```java
package com.vanter.ember.hub.backup;

import java.util.List;

/** What {@code HubControlServer} needs; {@link HubBackupService} is the real implementation. */
public interface HubBackup {

    BackupStatus status();

    BackupConfig getConfig();

    /** @throws IllegalArgumentException when {@code destDir} is blank or {@code retention} is below 1 */
    BackupConfig setConfig(BackupConfig config);

    /** Never throws: a failure comes back as an {@code ERROR} snapshot. Null/blank = configured folder. */
    BackupSnapshot backupNow(String destDirOrNull);

    List<BackupSnapshot> listSnapshots();

    /** Validates a backup file (any path) and its version compatibility, without touching any data. */
    BackupSnapshot inspect(String path) throws BackupException;
}
```

- [ ] **Step 4: Write `HubBackupService.java`**

```java
package com.vanter.ember.hub.backup;

import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.control.HubOrchestrator;
import com.vanter.ember.hub.control.ServicePhase;
import java.io.IOException;
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

    // --- scheduling ----------------------------------------------------------------

    /** Called by {@link BackupScheduler} every tick; a no-op unless a backup is actually due. */
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest="HubBackupServiceTest,HubVersionTest,BackupConfigStoreTest,BackupArchiveTest"`
Expected: PASS. (`listSnapshots` ordering relies on file-name sort = chronological; the broken `2026-01-01` file sorts last.)

- [ ] **Step 6: Report + commit**

`reports/510-task-hub-backup-2-backup-service.md`, tick Task 2 in `PROGRESS.md`.
```bash
git add backend/src/main/java/com/vanter/ember/hub/backup backend/src/test/java/com/vanter/ember/hub/backup PROGRESS.md reports/510-task-hub-backup-2-backup-service.md
git commit -m "feat(hub): backup service with atomic zip write, pruning and scheduled-if-due"
```

### Task 3: Orchestrator `stopAndWait()` + `HubBackupService.restore` (+ real-Postgres round trip)

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/HubOrchestrator.java`, `.../control/DefaultHubOrchestrator.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/backup/{HubBackup,HubBackupService}.java`
- Modify (tests): `.../hub/control/HubControlServerTest.java` (fake), `.../hub/control/DefaultHubOrchestratorTest.java`, `.../hub/backup/{FakeHubOrchestrator,HubBackupServiceTest}.java`
- Create (test): `backend/src/test/java/com/vanter/ember/hub/backup/HubBackupRestoreIntegrationTest.java`

**Interfaces:**
- Consumes (Task 2): `HubBackupService.{inspect, doBackup, deleteRecursively, lock, now}`, `PostgresTools`, `BackupArchive.{extractDump, extractMinio}`. Existing: `PortableDatabaseBootstrap(Path dataDir, Path binDir, int port)` with `ensureRunning()`/`stop()` (both `throws PortableDatabaseException`, a checked exception) and `HubOrchestrator.start(String[])`.
- Produces:
  - `HubOrchestrator.stopAndWait()` — stops Spring server, MinIO **and** Postgres synchronously; afterwards `snapshot()` reports all three `STOPPED`, so `start(...)` works again.
  - `HubBackup.restore(String path, boolean skipSafetySnapshot) throws BackupException` (long-running, blocking).

- [ ] **Step 1: Add `stopAndWait` to the orchestrator (test first)**

Add to `DefaultHubOrchestratorTest.java` (uses its existing `propertiesWithStateFile` helper; add `import static org.junit.jupiter.api.Assertions.assertEquals;` is already present):
```java
    @Test
    void stopAndWait_whenNothingRunning_endsWithEverythingStopped() {
        DefaultHubOrchestrator orchestrator =
                new DefaultHubOrchestrator(propertiesWithStateFile(tempDir.resolve("hub-state.json")));

        orchestrator.stopAndWait();

        HubOrchestrator.HubStatusSnapshot s = orchestrator.snapshot();
        assertEquals(ServicePhase.STOPPED, s.server());
        assertEquals(ServicePhase.STOPPED, s.postgres());
        assertEquals(ServicePhase.STOPPED, s.minio());
    }
```
Run: `cd backend && ./mvnw test -Dtest=DefaultHubOrchestratorTest` → Expected: compile FAIL (`stopAndWait` undefined).

In `HubOrchestrator.java`, after `void stop();` add:
```java
    /**
     * Same as {@link #stop()} but blocks until the Spring server, MinIO and Postgres are fully
     * stopped. Used by restore, which must own the data directories.
     */
    void stopAndWait();
```
In `DefaultHubOrchestrator.java`, after the `runStop()` method add:
```java
    @Override
    public synchronized void stopAndWait() {
        serverPhase = ServicePhase.STOPPING;
        postgresPhase = ServicePhase.STOPPING;
        minioPhase = ServicePhase.STOPPING;
        runStop();
    }
```
In `HubControlServerTest.java`'s `FakeOrchestrator` add:
```java
        @Override
        public void stopAndWait() {
            stopCalled = true;
        }
```
Replace `FakeHubOrchestrator.java` with:
```java
package com.vanter.ember.hub.backup;

import com.vanter.ember.hub.control.HubOrchestrator;
import com.vanter.ember.hub.control.ServicePhase;
import java.nio.file.Path;

/** Test double shared by the backup tests. */
final class FakeHubOrchestrator implements HubOrchestrator {
    ServicePhase postgres = ServicePhase.RUNNING;
    int startCalls;
    int stopAndWaitCalls;
    Runnable onStopAndWait = () -> {};

    @Override public void start(String[] launchArgs) { startCalls++; }
    @Override public void stop() {}

    @Override
    public void stopAndWait() {
        stopAndWaitCalls++;
        onStopAndWait.run();
    }

    @Override public void installLicense(Path source) {}
    @Override public void removeLicense() {}

    @Override
    public HubStatusSnapshot snapshot() {
        return new HubStatusSnapshot(postgres, null, ServicePhase.STOPPED, null, ServicePhase.STOPPED, null,
                new LicenseSnapshot(LicenseSnapshot.NONE, null, null), 8080);
    }
}
```
Run: `cd backend && ./mvnw test -Dtest="DefaultHubOrchestratorTest,HubControlServerTest,HubBackupServiceTest"` → Expected: PASS.

- [ ] **Step 2: Write the failing restore-guard tests**

In `HubBackupServiceTest.java`, replace the last test + closing brace
```java
        assertThat(service.setConfig(new BackupConfig("E:\\x", 3))).isEqualTo(new BackupConfig("E:\\x", 3));
    }
}
```
with
```java
        assertThat(service.setConfig(new BackupConfig("E:\\x", 3))).isEqualTo(new BackupConfig("E:\\x", 3));
    }

    @Test
    void restore_notABackup_throwsInvalidAndStopsNothing() throws Exception {
        Path junk = Files.writeString(tmp.resolve("junk.zip"), "hola");

        assertThatThrownBy(() -> service.restore(junk.toString(), false))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.code()).isEqualTo(BackupException.INVALID));
        assertThat(orchestrator.stopAndWaitCalls).isZero();
    }

    @Test
    void restore_incompatibleVersion_touchesNothing() {
        version[0] = "0.9.0";
        BackupSnapshot made = service.backupNow(null);
        version[0] = "0.2.6.1";

        assertThatThrownBy(() -> service.restore(made.path(), false))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.code()).isEqualTo(BackupException.INCOMPATIBLE));
        assertThat(orchestrator.stopAndWaitCalls).isZero();
        assertThat(orchestrator.startCalls).isZero();
    }

    @Test
    void restore_safetySnapshotFails_blocksBeforeStoppingAnything() {
        BackupSnapshot made = service.backupNow(null);
        dumpFails = true;

        assertThatThrownBy(() -> service.restore(made.path(), false))
                .isInstanceOfSatisfying(BackupException.class,
                        e -> assertThat(e.code()).isEqualTo(BackupException.SAFETY_FAILED));
        assertThat(orchestrator.stopAndWaitCalls).isZero();
    }
}
```
Run: `cd backend && ./mvnw test -Dtest=HubBackupServiceTest` → Expected: compile FAIL (`restore` undefined on `HubBackupService`).

- [ ] **Step 3: Implement `restore`**

In `HubBackup.java` add before the closing brace:
```java

    /**
     * Restores {@code path} over the live data. Order: validate + version gate (nothing touched if it
     * fails) → pre-restore safety snapshot (unless {@code skipSafetySnapshot}) → stop everything →
     * rebuild Postgres if unusable → {@code dropdb}/{@code createdb}/{@code pg_restore} → swap MinIO
     * files → start again. Blocks until done.
     */
    void restore(String path, boolean skipSafetySnapshot) throws BackupException;
```
In `HubBackupService.java` add imports:
```java
import com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap;
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import java.net.InetAddress;
import java.net.ServerSocket;
```
and add these methods (after `inspect`):
```java
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
```
Run: `cd backend && ./mvnw test -Dtest="HubBackupServiceTest,DefaultHubOrchestratorTest,HubControlServerTest"` → Expected: PASS.

- [ ] **Step 4: Real-Postgres round trip test**

`HubBackupRestoreIntegrationTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap;
import com.vanter.ember.hub.config.HubProperties;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the real bundled pg_dump/pg_restore; skipped when the vendor cache is absent. */
class HubBackupRestoreIntegrationTest {

    private static final Path PG_BIN =
            Path.of("../ember-hub/.vendor-cache/postgres/bin").toAbsolutePath().normalize();

    @TempDir Path tmp;
    int port;
    Path dataDir;
    Path minio;
    PortableDatabaseBootstrap db;
    FakeHubOrchestrator orchestrator = new FakeHubOrchestrator();
    HubBackupService service;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Files.exists(PG_BIN.resolve("pg_dump.exe")) && Files.exists(PG_BIN.resolve("pg_restore.exe")),
                "portable Postgres not vendored");
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        dataDir = tmp.resolve("data/postgres");
        minio = Files.createDirectories(tmp.resolve("data/minio"));
        db = new PortableDatabaseBootstrap(dataDir, PG_BIN, port);
        db.ensureRunning();
        orchestrator.onStopAndWait = () -> {
            try {
                db.stop(); // what the real orchestrator's stopAndWait does to Postgres
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
        HubProperties props = new HubProperties(dataDir, PG_BIN, tmp.resolve("license.key"), tmp.resolve("pub.der"),
                tmp.resolve("hub-state.json"), port, 8080, "", minio, tmp.resolve("minio-bin"), 9000);
        service = new HubBackupService(props, orchestrator,
                new BackupConfigStore(tmp.resolve("hub-backup.json"), tmp.resolve("backups")),
                new PostgresTools(PG_BIN, port), () -> "0.2.6.1", Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) {
            db.stop();
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection("jdbc:postgresql://127.0.0.1:" + port + "/ember", "ember", "ember");
    }

    private String setting(String key) throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select v from settings_probe where k = '" + key + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    @Test
    void restore_bringsBackDatabaseRowsSettingsAndMinioFiles() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create table settings_probe(k text primary key, v text)");
            st.execute("insert into settings_probe values ('tax', '15')");
        }
        Files.writeString(minio.resolve("logo.png"), "V1");

        BackupSnapshot snap = service.backupNow(null);
        assertThat(snap.status()).isEqualTo(BackupSnapshot.OK);

        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("update settings_probe set v = '99' where k = 'tax'");
            st.execute("insert into settings_probe values ('extra', 'x')");
        }
        Files.writeString(minio.resolve("logo.png"), "V2");
        Files.writeString(minio.resolve("extra.png"), "E");

        service.restore(snap.path(), false);

        assertThat(orchestrator.stopAndWaitCalls).isEqualTo(1);
        assertThat(orchestrator.startCalls).isEqualTo(1);
        db.ensureRunning(); // restore left Postgres stopped; the real orchestrator.start() does this
        assertThat(setting("tax")).isEqualTo("15");
        assertThat(setting("extra")).isNull();
        assertThat(Files.readString(minio.resolve("logo.png"))).isEqualTo("V1");
        assertThat(minio.resolve("extra.png")).doesNotExist();
        try (Stream<Path> s = Files.list(tmp.resolve("backups"))) {
            assertThat(s.map(p -> p.getFileName().toString()))
                    .anyMatch(n -> n.endsWith("-pre-restore.zip"));
        }
    }

    @Test
    void restore_withCorruptDataDir_movesItAsideAndStillRestores() throws Exception {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("create table settings_probe(k text primary key, v text)");
            st.execute("insert into settings_probe values ('tax', '15')");
        }
        BackupSnapshot snap = service.backupNow(null);
        db.stop();
        Files.writeString(dataDir.resolve("PG_VERSION"), "99"); // pg_ctl refuses to start this cluster

        service.restore(snap.path(), true); // Postgres can't be dumped, so skip the safety snapshot

        db.ensureRunning();
        assertThat(setting("tax")).isEqualTo("15");
        try (Stream<Path> s = Files.list(dataDir.getParent())) {
            assertThat(s.map(p -> p.getFileName().toString())).anyMatch(n -> n.startsWith("postgres.corrupt-"));
        }
    }
}
```
Run: `cd backend && ./mvnw test -Dtest=HubBackupRestoreIntegrationTest`
Expected: PASS (2 tests) on a machine with `ember-hub/.vendor-cache`; otherwise 2 skipped. If `pg_dump.exe` is missing from `bin` while the cache exists, run `ember-hub/fetch-vendor-binaries.ps1` first.

- [ ] **Step 5: Full hub tests**

Run: `cd backend && ./mvnw test -Dtest="com.vanter.ember.hub.**.*Test"`
Expected: PASS, no regressions.

- [ ] **Step 6: Report + commit**

`reports/511-task-hub-backup-3-restore.md`, tick Task 3 in `PROGRESS.md`.
```bash
git add backend/src/main/java/com/vanter/ember/hub backend/src/test/java/com/vanter/ember/hub PROGRESS.md reports/511-task-hub-backup-3-restore.md
git commit -m "feat(hub): restore from backup with safety snapshot and corrupt-data-dir recovery"
```

### Task 4: `BackupScheduler` + `HubControlServer` routes + sidecar wiring

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/backup/BackupScheduler.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/control/HubControlServer.java`, `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/backup/BackupSchedulerTest.java`, modify `backend/src/test/java/com/vanter/ember/hub/control/HubControlServerTest.java`

**Interfaces:**
- Consumes (Tasks 1–3): `HubBackup` (`status/getConfig/setConfig/backupNow/listSnapshots/inspect/restore`), `HubBackupService.runScheduledIfDue()`, `BackupException.code()` + constants, `BackupConfigStore`, `PostgresTools`, `HubVersion.current()`.
- Produces — wire contract used by the frontend (Task 5). All under `http://127.0.0.1:<port>`, JSON:
  - `GET  /api/backup/status` → `BackupStatus`
  - `GET  /api/backup/config` → `BackupConfig`; `POST` `{destDir, retention}` → `BackupConfig` (400 `{error}` if invalid)
  - `POST /api/backup/now` `{destDir?}` (body optional) → `BackupSnapshot` (HTTP 200 even when `status == "ERROR"`)
  - `GET  /api/backup/list` → `BackupSnapshot[]`
  - `POST /api/backup/inspect` `{path}` → `BackupSnapshot`; errors `{error, code}`
  - `POST /api/backup/restore` `{path, skipSafetySnapshot}` → `StatusDto` (same as `/api/status`); errors `{error, code}`
  - Error code → HTTP status: `BACKUP_INCOMPATIBLE`/`BACKUP_SAFETY_FAILED` → 409, `BACKUP_INVALID` → 400, anything else → 500.

- [ ] **Step 1: Write the failing tests**

`BackupSchedulerTest.java`:
```java
package com.vanter.ember.hub.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BackupSchedulerTest {

    @Test
    void tick_swallowsExceptionsSoTheScheduleSurvives() {
        AtomicInteger calls = new AtomicInteger();
        BackupScheduler scheduler = new BackupScheduler(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        });

        scheduler.tick();
        scheduler.tick();

        assertThat(calls.get()).isEqualTo(2);
    }
}
```

Modify `HubControlServerTest.java`:

(a) imports — add:
```java
import static org.junit.jupiter.api.Assertions.assertNull;
import com.vanter.ember.hub.backup.BackupConfig;
import com.vanter.ember.hub.backup.BackupException;
import com.vanter.ember.hub.backup.BackupSnapshot;
import com.vanter.ember.hub.backup.BackupStatus;
import com.vanter.ember.hub.backup.HubBackup;
import java.util.List;
```
(b) field: replace `    private FakeOrchestrator orchestrator;` with
```java
    private FakeOrchestrator orchestrator;
    private FakeBackup backup;
```
(c) in `start()` replace
```java
        orchestrator = new FakeOrchestrator();
        server = new HubControlServer(orchestrator);
```
with
```java
        orchestrator = new FakeOrchestrator();
        backup = new FakeBackup();
        server = new HubControlServer(orchestrator, backup);
```
(d) insert these tests right before `    private HttpResponse<String> get(String path)`:
```java
    @Test
    void backupStatus_returnsBackupStatus() throws Exception {
        JsonNode body = mapper.readTree(get("/api/backup/status").body());

        assertEquals("C:\\backups", body.get("destDir").asText());
        assertEquals("C:\\backups", body.get("defaultDestDir").asText());
        assertEquals(7, body.get("retention").asInt());
    }

    @Test
    void backupConfig_post_savesAndReturnsIt() throws Exception {
        HttpResponse<String> res = post("/api/backup/config", "{\"destDir\":\"E:\\\\usb\",\"retention\":3}");

        assertEquals(200, res.statusCode());
        assertEquals("E:\\usb", backup.savedConfig.destDir());
        assertEquals(3, backup.savedConfig.retention());
    }

    @Test
    void backupConfig_invalid_returns400() throws Exception {
        backup.setConfigFailure = new IllegalArgumentException("retention debe ser al menos 1.");

        HttpResponse<String> res = post("/api/backup/config", "{\"destDir\":\"E:\\\\usb\",\"retention\":0}");

        assertEquals(400, res.statusCode());
        assertEquals("retention debe ser al menos 1.", mapper.readTree(res.body()).get("error").asText());
    }

    @Test
    void backupNow_passesDestDir_andEmptyBodyMeansConfiguredFolder() throws Exception {
        assertEquals(200, post("/api/backup/now", "{\"destDir\":\"E:\\\\usb\"}").statusCode());
        assertEquals("E:\\usb", backup.nowDestDir);

        assertEquals(200, post("/api/backup/now", "").statusCode());
        assertNull(backup.nowDestDir);
    }

    @Test
    void backupList_returnsSnapshotsArray() throws Exception {
        JsonNode body = mapper.readTree(get("/api/backup/list").body());

        assertTrue(body.isArray());
        assertEquals("ember-backup-x.zip", body.get(0).get("id").asText());
    }

    @Test
    void backupInspect_missingPath_returns400() throws Exception {
        assertEquals(400, post("/api/backup/inspect", "{}").statusCode());
    }

    @Test
    void backupInspect_incompatible_returns409WithCode() throws Exception {
        backup.inspectFailure = new BackupException(BackupException.INCOMPATIBLE, "versión más reciente");

        HttpResponse<String> res = post("/api/backup/inspect", "{\"path\":\"C:\\\\x.zip\"}");

        assertEquals(409, res.statusCode());
        assertEquals("BACKUP_INCOMPATIBLE", mapper.readTree(res.body()).get("code").asText());
    }

    @Test
    void backupRestore_success_passesPathAndFlag() throws Exception {
        HttpResponse<String> res =
                post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\",\"skipSafetySnapshot\":true}");

        assertEquals(200, res.statusCode());
        assertEquals("C:\\x.zip", backup.restoredPath);
        assertTrue(backup.restoredSkipSafety);
    }

    @Test
    void backupRestore_errorsMapToHttpStatuses() throws Exception {
        backup.restoreFailure = new BackupException(BackupException.SAFETY_FAILED, "sin copia previa");
        assertEquals(409, post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}").statusCode());

        backup.restoreFailure = new BackupException(BackupException.INVALID, "no es un respaldo");
        assertEquals(400, post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}").statusCode());

        backup.restoreFailure = new BackupException(BackupException.RESTORE_FAILED, "falló");
        HttpResponse<String> res = post("/api/backup/restore", "{\"path\":\"C:\\\\x.zip\"}");
        assertEquals(500, res.statusCode());
        assertEquals("BACKUP_RESTORE_FAILED", mapper.readTree(res.body()).get("code").asText());
    }

```
(e) append `FakeBackup` — replace the file's final
```java
        @Override
        public HubStatusSnapshot snapshot() {
            return snapshot;
        }
    }
}
```
with
```java
        @Override
        public HubStatusSnapshot snapshot() {
            return snapshot;
        }
    }

    private static final class FakeBackup implements HubBackup {
        BackupConfig savedConfig;
        IllegalArgumentException setConfigFailure;
        String nowDestDir;
        BackupException inspectFailure;
        BackupException restoreFailure;
        String restoredPath;
        boolean restoredSkipSafety;
        final BackupSnapshot snapshot = new BackupSnapshot("ember-backup-x.zip", "C:\\backups\\ember-backup-x.zip",
                "2026-09-19T10:00:00Z", 10, BackupSnapshot.OK, null, "0.2.6.1", false);

        @Override
        public BackupStatus status() {
            return new BackupStatus(null, null, "C:\\backups", "C:\\backups", 7);
        }

        @Override
        public BackupConfig getConfig() {
            return new BackupConfig("C:\\backups", 7);
        }

        @Override
        public BackupConfig setConfig(BackupConfig config) {
            if (setConfigFailure != null) {
                throw setConfigFailure;
            }
            savedConfig = config;
            return config;
        }

        @Override
        public BackupSnapshot backupNow(String destDirOrNull) {
            nowDestDir = destDirOrNull;
            return snapshot;
        }

        @Override
        public List<BackupSnapshot> listSnapshots() {
            return List.of(snapshot);
        }

        @Override
        public BackupSnapshot inspect(String path) throws BackupException {
            if (inspectFailure != null) {
                throw inspectFailure;
            }
            return snapshot;
        }

        @Override
        public void restore(String path, boolean skipSafetySnapshot) throws BackupException {
            if (restoreFailure != null) {
                throw restoreFailure;
            }
            restoredPath = path;
            restoredSkipSafety = skipSafetySnapshot;
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest="BackupSchedulerTest,HubControlServerTest"`
Expected: compile FAIL (`BackupScheduler` missing; `HubControlServer(orchestrator, backup)` constructor missing).

- [ ] **Step 3: Implement `BackupScheduler`**

```java
package com.vanter.ember.hub.backup;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wakes up every 15 minutes and asks the backup service whether a backup is due (24 h since the
 * last success, Postgres running). A fixed time of day would never fire on a PC that is switched
 * off overnight. Plain executor, not Spring {@code @Scheduled}: the Spring context is closed
 * during a restore and must not own this.
 */
public final class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);
    private static final long INITIAL_DELAY_SECONDS = 120;
    private static final long TICK_SECONDS = 15 * 60;

    private final Runnable task;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ember-hub-backup-scheduler");
        t.setDaemon(true);
        return t;
    });

    public BackupScheduler(Runnable task) {
        this.task = task;
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::tick, INITIAL_DELAY_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        executor.shutdownNow();
    }

    /** An exception escaping a scheduled task silently cancels all future runs — so never let one. */
    void tick() {
        try {
            task.run();
        } catch (Throwable t) {
            log.error("El respaldo programado falló de forma inesperada", t);
        }
    }
}
```

- [ ] **Step 4: Implement the control-server routes**

In `HubControlServer.java`:

(a) imports — add:
```java
import com.vanter.ember.hub.backup.BackupConfig;
import com.vanter.ember.hub.backup.BackupException;
import com.vanter.ember.hub.backup.HubBackup;
```
(b) fields/constructor — replace
```java
    private final HubOrchestrator orchestrator;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer httpServer;

    public HubControlServer(HubOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }
```
with
```java
    private final HubOrchestrator orchestrator;
    private final HubBackup backup;
    private final ObjectMapper mapper = new ObjectMapper();

    private HttpServer httpServer;

    public HubControlServer(HubOrchestrator orchestrator, HubBackup backup) {
        this.orchestrator = orchestrator;
        this.backup = backup;
    }
```
(c) in `start()`, after the `/api/license` context line add:
```java
        httpServer.createContext("/api/backup/status", this::handleBackupStatus).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/config", this::handleBackupConfig).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/now", this::handleBackupNow).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/list", this::handleBackupList).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/inspect", this::handleBackupInspect).getFilters().add(CORS_FILTER);
        httpServer.createContext("/api/backup/restore", this::handleBackupRestore).getFilters().add(CORS_FILTER);
```
(d) add handlers before the `// --- wire helpers` section:
```java
    // --- backup handlers -----------------------------------------------------------

    private void handleBackupStatus(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, backup.status());
    }

    private void handleBackupConfig(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equals(method)) {
            sendJson(exchange, 200, backup.getConfig());
            return;
        }
        if (!"POST".equals(method)) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        ConfigRequest req = mapper.readValue(exchange.getRequestBody(), ConfigRequest.class);
        try {
            int retention = req.retention() == null ? backup.getConfig().retention() : req.retention();
            sendJson(exchange, 200, backup.setConfig(new BackupConfig(req.destDir(), retention)));
        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400, Map.of("error", e.getMessage()));
        }
    }

    private void handleBackupNow(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        byte[] body = exchange.getRequestBody().readAllBytes();
        NowRequest req = body.length == 0 ? new NowRequest(null) : mapper.readValue(body, NowRequest.class);
        sendJson(exchange, 200, backup.backupNow(req.destDir()));
    }

    private void handleBackupList(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        sendJson(exchange, 200, backup.listSnapshots());
    }

    private void handleBackupInspect(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        PathRequest req = mapper.readValue(exchange.getRequestBody(), PathRequest.class);
        if (req.path() == null || req.path().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "path es obligatorio."));
            return;
        }
        try {
            sendJson(exchange, 200, backup.inspect(req.path()));
        } catch (BackupException e) {
            sendBackupError(exchange, e);
        }
    }

    private void handleBackupRestore(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendJson(exchange, 405, Map.of("error", "method not allowed"));
            return;
        }
        RestoreRequest req = mapper.readValue(exchange.getRequestBody(), RestoreRequest.class);
        if (req.path() == null || req.path().isBlank()) {
            sendJson(exchange, 400, Map.of("error", "path es obligatorio."));
            return;
        }
        try {
            backup.restore(req.path(), req.skipSafetySnapshot());
            sendJson(exchange, 200, StatusDto.from(orchestrator.snapshot()));
        } catch (BackupException e) {
            sendBackupError(exchange, e);
        }
    }

    private void sendBackupError(HttpExchange exchange, BackupException e) throws IOException {
        int status = switch (e.code()) {
            case BackupException.INCOMPATIBLE, BackupException.SAFETY_FAILED -> 409;
            case BackupException.INVALID -> 400;
            default -> 500;
        };
        sendJson(exchange, status, Map.of("error", e.getMessage(), "code", e.code()));
    }
```
(e) add request records next to `LicenseRequest`:
```java
    private record ConfigRequest(String destDir, Integer retention) {}

    private record NowRequest(String destDir) {}

    private record PathRequest(String path) {}

    private record RestoreRequest(String path, boolean skipSafetySnapshot) {}
```

- [ ] **Step 5: Wire the sidecar**

In `EmberApplication.java`, add imports:
```java
import com.vanter.ember.hub.backup.BackupConfigStore;
import com.vanter.ember.hub.backup.BackupScheduler;
import com.vanter.ember.hub.backup.HubBackupService;
import com.vanter.ember.hub.backup.HubVersion;
import com.vanter.ember.hub.backup.PostgresTools;
import java.nio.file.Path;
import java.time.Clock;
```
and replace in `runHubSidecar`
```java
        HubControlServer controlServer = new HubControlServer(orchestrator);
        int port = controlServer.start();
```
with
```java
        // Backup lives next to hub-state.json (%ProgramData%\EmberHub in a packaged install); the
        // "Esta máquina" destination is the backups\ folder the Tauri shell already creates there.
        Path stateFile = properties.stateFile().toAbsolutePath();
        HubBackupService backupService = new HubBackupService(
                properties, orchestrator,
                new BackupConfigStore(stateFile.resolveSibling("hub-backup.json"), stateFile.resolveSibling("backups")),
                new PostgresTools(properties.postgresBinDir(), properties.postgresPort()),
                HubVersion::current, Clock.systemDefaultZone());
        BackupScheduler backupScheduler = new BackupScheduler(backupService::runScheduledIfDue);
        backupScheduler.start();

        HubControlServer controlServer = new HubControlServer(orchestrator, backupService);
        int port = controlServer.start();
```
and in the shutdown hook replace `orchestrator.stop();` with `backupScheduler.stop();\n            orchestrator.stop();`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest="BackupSchedulerTest,HubControlServerTest"` → Expected: PASS.
Then the full suite (CLAUDE.md zero-tolerance): `cd backend && ./mvnw test` → Expected: all green (baseline 1314 + the new tests; the 2 real-Postgres tests may be skipped).

- [ ] **Step 7: Report + commit**

`reports/512-task-hub-backup-4-control-routes.md`, tick Task 4 in `PROGRESS.md`.
```bash
git add backend/src/main/java/com/vanter/ember backend/src/test/java/com/vanter/ember/hub PROGRESS.md reports/512-task-hub-backup-4-control-routes.md
git commit -m "feat(hub): backup scheduler and control-server backup routes"
```

### Task 5: Hub UI — types, API client, `Modal`, `BackupCard` (+ tests)

**Files:**
- Modify: `ember-hub/ui/src/lib/types.ts`, `ember-hub/ui/src/lib/api.ts`
- Create: `ember-hub/ui/src/components/Modal.tsx`, `ember-hub/ui/src/components/BackupCard.tsx`
- Test: `ember-hub/ui/src/components/BackupCard.test.tsx`

**Interfaces:**
- Consumes (Task 4 wire contract): the 6 `/api/backup/*` routes. Existing: `Card` (`icon`, `title`, `badge`, `children`), `Badge` (`variant`), `Button` (`variant: 'primary' | 'outline'`), `lucide-react`'s `Archive` icon.
- Produces:
  - `types.ts`: `BackupSnapshot`, `BackupConfig`, `BackupStatus` (mirror the backend records; `createdAt`/`nextScheduledRun` are ISO strings or `null`).
  - `api.ts`: `class ApiError extends Error { code?: string }` (thrown by every call now, `code` = backend `code` field), `getBackupStatus()`, `listBackups()`, `setBackupConfig(destDir: string, retention?: number)`, `backupNow(destDir?: string)`, `inspectBackup(path: string)`, `restoreBackup(path: string, skipSafetySnapshot?: boolean)` (resolves with `HubStatus`).
  - `<BackupCard pickFolder pickBackupFile onBusyChange? onRestored? />` where `pickFolder`/`pickBackupFile: () => Promise<string | null>` are injected (Task 6 supplies the Tauri dialogs; tests supply mocks).

- [ ] **Step 1: Write the failing test**

`ember-hub/ui/src/components/BackupCard.test.tsx`:
```tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';

vi.mock('../lib/api', () => ({
  getBackupStatus: vi.fn(),
  listBackups: vi.fn(),
  setBackupConfig: vi.fn(),
  backupNow: vi.fn(),
  inspectBackup: vi.fn(),
  restoreBackup: vi.fn()
}));

import * as api from '../lib/api';
import BackupCard from './BackupCard';

const mocked = vi.mocked(api);

const snap = {
  id: 'ember-backup-2026-09-19T10-00-00.zip',
  path: 'C:\\backups\\ember-backup-2026-09-19T10-00-00.zip',
  createdAt: '2026-09-19T10:00:00Z',
  sizeBytes: 2 * 1024 * 1024,
  status: 'OK' as const,
  errorMessage: null,
  appVersion: '0.2.6.1',
  preRestoreSafety: false
};

const status = {
  lastRun: snap,
  nextScheduledRun: '2026-09-20T10:00:00Z',
  destDir: 'C:\\backups',
  defaultDestDir: 'C:\\backups',
  retention: 7
};

function apiError(message: string, code: string) {
  return Object.assign(new Error(message), { code });
}

function setup() {
  const props = {
    pickFolder: vi.fn().mockResolvedValue('E:\\usb'),
    pickBackupFile: vi.fn().mockResolvedValue('D:\\subido.zip'),
    onBusyChange: vi.fn(),
    onRestored: vi.fn()
  };
  render(<BackupCard {...props} />);
  return props;
}

beforeEach(() => {
  vi.clearAllMocks();
  mocked.getBackupStatus.mockResolvedValue(status);
  mocked.listBackups.mockResolvedValue([snap]);
  mocked.backupNow.mockResolvedValue(snap);
  mocked.inspectBackup.mockResolvedValue(snap);
  mocked.restoreBackup.mockResolvedValue({} as never);
  mocked.setBackupConfig.mockResolvedValue({ destDir: 'E:\\usb', retention: 7 });
});

describe('BackupCard', () => {
  it('shows the automatic folder and lists existing backups', async () => {
    setup();

    expect(await screen.findByText('C:\\backups')).toBeTruthy();
    expect(await screen.findByRole('button', { name: 'Restaurar' })).toBeTruthy();
  });

  it('shows the last failed run as an error banner', async () => {
    mocked.getBackupStatus.mockResolvedValue({
      ...status,
      lastRun: { ...snap, status: 'ERROR', errorMessage: 'No se pudo escribir en la carpeta' }
    });
    setup();

    expect(await screen.findByText('No se pudo escribir en la carpeta')).toBeTruthy();
  });

  it('opens a modal recommending a USB drive over this machine', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));

    expect(await screen.findByText('Recomendado')).toBeTruthy();
    expect(screen.getByRole('button', { name: /USB o disco externo/ })).toBeTruthy();
    expect(screen.getByRole('button', { name: /Esta máquina/ })).toBeTruthy();
  });

  it('backs up to this machine and reminds the owner to copy it to a USB', async () => {
    setup();
    await screen.findByText('C:\\backups'); // status (incl. defaultDestDir) must be loaded first
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    await waitFor(() => expect(mocked.backupNow).toHaveBeenCalledWith('C:\\backups'));
    expect(await screen.findByText(/Copia el archivo a una USB/)).toBeTruthy();
  });

  it('backs up to the folder picked for the USB option', async () => {
    const props = setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /USB o disco externo/ }));

    await waitFor(() => expect(mocked.backupNow).toHaveBeenCalledWith('E:\\usb'));
    expect(props.pickFolder).toHaveBeenCalled();
  });

  it('does nothing when the folder picker is cancelled', async () => {
    const props = setup();
    props.pickFolder.mockResolvedValue(null);
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /USB o disco externo/ }));

    await waitFor(() => expect(props.pickFolder).toHaveBeenCalled());
    expect(mocked.backupNow).not.toHaveBeenCalled();
  });

  it('shows the backend error when a backup fails', async () => {
    mocked.backupNow.mockResolvedValue({ ...snap, status: 'ERROR', errorMessage: 'USB desconectada' });
    setup();
    await screen.findByText('C:\\backups');
    fireEvent.click(await screen.findByRole('button', { name: 'Respaldar ahora' }));
    fireEvent.click(await screen.findByRole('button', { name: /Esta máquina/ }));

    expect(await screen.findByText('USB desconectada')).toBeTruthy();
  });

  it('restores an uploaded file only after an explicit confirmation', async () => {
    const props = setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));

    expect(await screen.findByText(/reemplaza todos los datos actuales/i)).toBeTruthy();
    expect(mocked.inspectBackup).toHaveBeenCalledWith('D:\\subido.zip');
    expect(mocked.restoreBackup).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Sí, restaurar' }));

    await waitFor(() => expect(mocked.restoreBackup).toHaveBeenCalledWith('D:\\subido.zip', false));
    await waitFor(() => expect(props.onRestored).toHaveBeenCalled());
    expect(props.onBusyChange).toHaveBeenCalledWith(true);
    expect(props.onBusyChange).toHaveBeenLastCalledWith(false);
  });

  it('restores from a row of the list', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar' }));

    await waitFor(() => expect(mocked.inspectBackup).toHaveBeenCalledWith(snap.path));
    expect(await screen.findByRole('button', { name: 'Sí, restaurar' })).toBeTruthy();
  });

  it('shows the incompatibility message and never opens the confirmation', async () => {
    mocked.inspectBackup.mockRejectedValue(
      apiError('Este respaldo es de una versión más reciente de Ember (9.0.0).', 'BACKUP_INCOMPATIBLE')
    );
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));

    expect(await screen.findByText(/versión más reciente/)).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Sí, restaurar' })).toBeNull();
  });

  it('offers to continue without a safety copy when it cannot be made', async () => {
    mocked.restoreBackup.mockRejectedValueOnce(
      apiError('No se pudo crear la copia de seguridad previa', 'BACKUP_SAFETY_FAILED')
    );
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar desde archivo…' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Sí, restaurar' }));

    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar sin copia previa' }));

    await waitFor(() => expect(mocked.restoreBackup).toHaveBeenLastCalledWith('D:\\subido.zip', true));
  });

  it('changes the automatic backup folder', async () => {
    setup();
    fireEvent.click(await screen.findByRole('button', { name: 'Cambiar carpeta automática…' }));

    await waitFor(() => expect(mocked.setBackupConfig).toHaveBeenCalledWith('E:\\usb'));
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd ember-hub/ui && pnpm test -- BackupCard`
Expected: FAIL (`Failed to resolve import "./BackupCard"`).

- [ ] **Step 3: Types and API client**

Append to `ember-hub/ui/src/lib/types.ts`:
```ts

export interface BackupSnapshot {
  id: string | null;
  path: string | null;
  createdAt: string | null;
  sizeBytes: number;
  status: 'OK' | 'ERROR';
  errorMessage: string | null;
  appVersion: string | null;
  preRestoreSafety: boolean;
}

export interface BackupConfig {
  destDir: string;
  retention: number;
}

export interface BackupStatus {
  lastRun: BackupSnapshot | null;
  nextScheduledRun: string | null;
  destDir: string;
  defaultDestDir: string;
  retention: number;
}
```

In `ember-hub/ui/src/lib/api.ts` change the import line to
```ts
import type { BackupConfig, BackupSnapshot, BackupStatus, HubStatus } from './types';
```
replace `asJson` with
```ts
/** Carries the backend's machine-readable `code` (e.g. `BACKUP_INCOMPATIBLE`) next to the message. */
export class ApiError extends Error {
  code?: string;

  constructor(message: string, code?: string) {
    super(message);
    this.code = code;
  }
}

async function asJson<T>(res: Response): Promise<T> {
  const data = await res.json();
  if (!res.ok) {
    throw new ApiError(data.error ?? `request failed (${res.status})`, data.code);
  }
  return data as T;
}
```
and append at the end of the file:
```ts

// --- backup / restore ---------------------------------------------------------------

function jsonPost(body: unknown): RequestInit {
  return { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) };
}

export async function getBackupStatus(): Promise<BackupStatus> {
  return asJson<BackupStatus>(await fetch(`${await base()}/api/backup/status`));
}

export async function listBackups(): Promise<BackupSnapshot[]> {
  return asJson<BackupSnapshot[]>(await fetch(`${await base()}/api/backup/list`));
}

export async function setBackupConfig(destDir: string, retention?: number): Promise<BackupConfig> {
  return asJson<BackupConfig>(await fetch(`${await base()}/api/backup/config`, jsonPost({ destDir, retention })));
}

/** Resolves (HTTP 200) even when the backup failed — check `status === 'ERROR'` on the result. */
export async function backupNow(destDir?: string): Promise<BackupSnapshot> {
  return asJson<BackupSnapshot>(await fetch(`${await base()}/api/backup/now`, jsonPost({ destDir })));
}

export async function inspectBackup(path: string): Promise<BackupSnapshot> {
  return asJson<BackupSnapshot>(await fetch(`${await base()}/api/backup/inspect`, jsonPost({ path })));
}

/** Blocks until the restore finishes (can take minutes for big media folders). */
export async function restoreBackup(path: string, skipSafetySnapshot = false): Promise<HubStatus> {
  return asJson<HubStatus>(await fetch(`${await base()}/api/backup/restore`, jsonPost({ path, skipSafetySnapshot })));
}
```

- [ ] **Step 4: `Modal.tsx`**

```tsx
import type { ReactNode } from 'react';

/** Minimal in-window modal (no browser `confirm()`/`alert()`); the parent controls when it renders. */
export default function Modal({
  title,
  children,
  footer
}: {
  title: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="w-full max-w-md rounded-3xl border border-border bg-background shadow-lg p-5 flex flex-col gap-4"
      >
        <h2 className="font-semibold text-lg">{title}</h2>
        <div className="text-sm flex flex-col gap-3">{children}</div>
        {footer && <div className="flex flex-wrap justify-end gap-2">{footer}</div>}
      </div>
    </div>
  );
}
```

- [ ] **Step 5: `BackupCard.tsx`**

```tsx
import { useEffect, useState } from 'react';
import { Archive } from 'lucide-react';
import Card from './Card';
import Badge from './Badge';
import Button from './Button';
import Modal from './Modal';
import {
  backupNow,
  getBackupStatus,
  inspectBackup,
  listBackups,
  restoreBackup,
  setBackupConfig
} from '../lib/api';
import type { BackupSnapshot, BackupStatus } from '../lib/types';

type ModalState =
  | null
  | { kind: 'destination' }
  | { kind: 'confirmRestore'; snapshot: BackupSnapshot }
  | { kind: 'safetyFailed'; path: string; message: string };

function formatDate(iso: string | null): string {
  return iso ? new Date(iso).toLocaleString('es') : 'fecha desconocida';
}

function formatSize(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

export default function BackupCard({
  pickFolder,
  pickBackupFile,
  onBusyChange,
  onRestored
}: {
  pickFolder: () => Promise<string | null>;
  pickBackupFile: () => Promise<string | null>;
  onBusyChange?: (busy: boolean) => void;
  onRestored?: () => void;
}) {
  const [status, setStatus] = useState<BackupStatus | null>(null);
  const [snapshots, setSnapshots] = useState<BackupSnapshot[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [working, setWorking] = useState<'backup' | 'restore' | null>(null);
  const [modal, setModal] = useState<ModalState>(null);

  async function load() {
    try {
      const [s, l] = await Promise.all([getBackupStatus(), listBackups()]);
      setStatus(s);
      setSnapshots(l);
    } catch {
      // transient — the next action (or a remount) reloads it
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function runBackup(destDir: string) {
    setModal(null);
    setWorking('backup');
    setError(null);
    setNotice(null);
    try {
      const snap = await backupNow(destDir);
      if (snap.status === 'ERROR') {
        setError(snap.errorMessage ?? 'El respaldo falló.');
      } else if (destDir === status?.defaultDestDir) {
        setNotice(
          'Respaldo guardado en esta máquina. Copia el archivo a una USB: si el disco de esta PC falla, este respaldo se pierde con él.'
        );
      } else {
        setNotice('Respaldo guardado.');
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setWorking(null);
      await load();
    }
  }

  async function onChooseUsb() {
    const dir = await pickFolder();
    if (dir) {
      await runBackup(dir);
    }
  }

  async function onChangeAutoFolder() {
    const dir = await pickFolder();
    if (!dir) return;
    setError(null);
    try {
      await setBackupConfig(dir);
      await load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function beginRestore(path: string) {
    setError(null);
    setNotice(null);
    try {
      setModal({ kind: 'confirmRestore', snapshot: await inspectBackup(path) });
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  async function onRestoreFromFile() {
    const path = await pickBackupFile();
    if (path) {
      await beginRestore(path);
    }
  }

  async function confirmRestore(path: string, skipSafety: boolean) {
    setModal(null);
    setWorking('restore');
    onBusyChange?.(true);
    try {
      await restoreBackup(path, skipSafety);
      setNotice('Restauración completada. Ember Hub se está reiniciando con los datos del respaldo.');
      onRestored?.();
    } catch (e) {
      if ((e as { code?: string }).code === 'BACKUP_SAFETY_FAILED') {
        setModal({ kind: 'safetyFailed', path, message: errorMessage(e) });
      } else {
        setError(errorMessage(e));
      }
    } finally {
      setWorking(null);
      onBusyChange?.(false);
      await load();
    }
  }

  const lastFailed = status?.lastRun?.status === 'ERROR';

  return (
    <Card
      icon={Archive}
      title="Respaldos"
      badge={
        status?.lastRun ? (
          <Badge variant={lastFailed ? 'danger' : 'success'}>{lastFailed ? 'Último falló' : 'Al día'}</Badge>
        ) : (
          <Badge>Sin respaldos</Badge>
        )
      }
    >
      {status && (
        <dl className="text-sm text-muted-foreground mb-3 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
          <dt>Carpeta automática</dt>
          <dd className="text-foreground break-all">{status.destDir}</dd>
          <dt>Próximo automático</dt>
          <dd className="text-foreground">{status.nextScheduledRun ? formatDate(status.nextScheduledRun) : 'pendiente'}</dd>
        </dl>
      )}

      {lastFailed && status?.lastRun?.errorMessage && (
        <p className="rounded-2xl bg-primary text-primary-foreground text-sm font-medium px-4 py-3 mb-3">
          {status.lastRun.errorMessage}
        </p>
      )}
      {error && (
        <p className="rounded-2xl bg-primary text-primary-foreground text-sm font-medium px-4 py-3 mb-3">{error}</p>
      )}
      {notice && <p className="text-sm text-emerald-700 mb-3">{notice}</p>}
      {working === 'restore' && (
        <p className="text-sm font-medium mb-3">Restaurando… no cierres Ember Hub hasta que termine.</p>
      )}

      <div className="flex flex-wrap gap-2">
        <Button variant="primary" className="w-fit" disabled={working !== null} onClick={() => setModal({ kind: 'destination' })}>
          Respaldar ahora
        </Button>
        <Button variant="outline" className="w-fit" disabled={working !== null} onClick={onRestoreFromFile}>
          Restaurar desde archivo…
        </Button>
        <Button variant="outline" className="w-fit" disabled={working !== null} onClick={onChangeAutoFolder}>
          Cambiar carpeta automática…
        </Button>
      </div>

      {snapshots.length > 0 && (
        <ul className="mt-4 flex flex-col divide-y divide-border">
          {snapshots.map((s) => (
            <li key={s.id ?? s.path} className="py-2 flex items-center gap-3 text-sm">
              <div className="min-w-0 flex-1">
                <p className="truncate">{formatDate(s.createdAt)}</p>
                <p className="text-xs text-muted-foreground truncate">
                  {formatSize(s.sizeBytes)}
                  {s.appVersion ? ` · v${s.appVersion}` : ''}
                </p>
              </div>
              {s.preRestoreSafety && <Badge variant="warning">Antes de restaurar</Badge>}
              {s.status === 'ERROR' ? (
                <Badge variant="danger">Dañado</Badge>
              ) : (
                <Button
                  variant="outline"
                  disabled={working !== null || !s.path}
                  onClick={() => s.path && beginRestore(s.path)}
                >
                  Restaurar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}

      {modal?.kind === 'destination' && (
        <Modal title="¿Dónde guardar el respaldo?" footer={<Button onClick={() => setModal(null)}>Cancelar</Button>}>
          <button
            type="button"
            className="text-left rounded-2xl border border-primary p-3 hover:bg-muted cursor-pointer"
            onClick={onChooseUsb}
          >
            <span className="flex items-center gap-2 font-medium">
              USB o disco externo <Badge variant="success">Recomendado</Badge>
            </span>
            <span className="block text-muted-foreground">
              Si el disco de esta PC falla, tus datos siguen a salvo.
            </span>
          </button>
          <button
            type="button"
            className="text-left rounded-2xl border border-border p-3 hover:bg-muted cursor-pointer"
            onClick={() => status && runBackup(status.defaultDestDir)}
          >
            <span className="font-medium">Esta máquina</span>
            <span className="block text-muted-foreground break-all">{status?.defaultDestDir}</span>
          </button>
        </Modal>
      )}

      {modal?.kind === 'confirmRestore' && (
        <Modal
          title="Restaurar respaldo"
          footer={
            <>
              <Button onClick={() => setModal(null)}>Cancelar</Button>
              <Button variant="primary" onClick={() => modal.snapshot.path && confirmRestore(modal.snapshot.path, false)}>
                Sí, restaurar
              </Button>
            </>
          }
        >
          <p>
            Respaldo del <strong>{formatDate(modal.snapshot.createdAt)}</strong>
            {modal.snapshot.appVersion ? ` (Ember ${modal.snapshot.appVersion})` : ''}.
          </p>
          <p>
            Esto reemplaza todos los datos actuales (pedidos, cuentas, configuración e imágenes) por los del
            respaldo. Antes se guardará una copia de los datos actuales.
          </p>
        </Modal>
      )}

      {modal?.kind === 'safetyFailed' && (
        <Modal
          title="No se pudo guardar la copia previa"
          footer={
            <>
              <Button onClick={() => setModal(null)}>Cancelar</Button>
              <Button variant="primary" onClick={() => confirmRestore(modal.path, true)}>
                Restaurar sin copia previa
              </Button>
            </>
          }
        >
          <p>{modal.message}</p>
          <p>Si continúas, los datos actuales se perderán y no podrás volver atrás.</p>
        </Modal>
      )}
    </Card>
  );
}
```

- [ ] **Step 6: Run the tests and the build**

Run: `cd ember-hub/ui && pnpm test` → Expected: PASS (all suites, including the 12 new `BackupCard` tests).
Run: `cd ember-hub/ui && pnpm run build` → Expected: build succeeds with no TypeScript errors.
If a `getByRole('button', { name: 'Restaurar' })` query reports "multiple elements", a second button is exactly named `Restaurar` — keep the card-level labels (`Restaurar desde archivo…`, `Sí, restaurar`, `Restaurar sin copia previa`) as written; do not rename them to plain `Restaurar`.

- [ ] **Step 7: Report + commit**

`reports/513-task-hub-backup-5-ui-card.md`, tick Task 5 in `PROGRESS.md`.
```bash
git add ember-hub/ui/src/lib/types.ts ember-hub/ui/src/lib/api.ts ember-hub/ui/src/components/Modal.tsx ember-hub/ui/src/components/BackupCard.tsx ember-hub/ui/src/components/BackupCard.test.tsx PROGRESS.md reports/513-task-hub-backup-5-ui-card.md
git commit -m "feat(hub-ui): backup card with usb-recommended modal and restore-from-file"
```

### Task 6: Mount the card in the dashboard, manual checklist, full verification

**Files:**
- Modify: `ember-hub/ui/src/components/Dashboard.tsx`, `ember-hub/VERIFY.md`, `PROGRESS.md`

**Interfaces:**
- Consumes (Task 5): `<BackupCard pickFolder pickBackupFile onBusyChange onRestored />`. Existing in `Dashboard.tsx`: `status`, `setBusy`, `refresh`, dynamic `import('@tauri-apps/plugin-dialog')` (same pattern as `onSelectLicense`).
- Produces: the feature reachable from the Hub window (no new exports).

- [ ] **Step 1: Wire `BackupCard` into `Dashboard.tsx`**

Add the import next to `LicenseCard`:
```tsx
import BackupCard from './BackupCard';
```
Insert these two helpers immediately before `  async function onOpenBrowser() {`:
```tsx
  async function pickBackupFolder(): Promise<string | null> {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const dir = await open({ directory: true, title: 'Elige la carpeta de respaldo' });
    return typeof dir === 'string' ? dir : null;
  }

  async function pickBackupFile(): Promise<string | null> {
    const { open } = await import('@tauri-apps/plugin-dialog');
    const file = await open({ filters: [{ name: 'Respaldo de Ember', extensions: ['zip'] }] });
    return typeof file === 'string' ? file : null;
  }

```
Replace
```tsx
          <LicenseCard license={status.license} onSelectLicense={onSelectLicense} onRemoveLicense={onRemoveLicense} />
```
with
```tsx
          <LicenseCard license={status.license} onSelectLicense={onSelectLicense} onRemoveLicense={onRemoveLicense} />
          <BackupCard
            pickFolder={pickBackupFolder}
            pickBackupFile={pickBackupFile}
            onBusyChange={setBusy}
            onRestored={refresh}
          />
```
(`setBusy(true)` during a restore disables Iniciar/Detener/Reiniciar in the header so nobody stops services mid-restore. The card sits inside `{status && …}` and its routes never need Postgres, so it stays usable when Postgres won't start — the corruption case.)

- [ ] **Step 2: Build + test the UI**

Run: `cd ember-hub/ui && pnpm test` → Expected: all suites PASS.
Run: `cd ember-hub/ui && pnpm run build` → Expected: succeeds, no TypeScript errors.

- [ ] **Step 3: Add the manual checks to `ember-hub/VERIFY.md`**

Replace
```
---

## Result

**Go / No-Go:** ____
```
with
```
- [ ] **14. Backup modal + "Esta máquina".**
  Dashboard → **Respaldos** → **Respaldar ahora**. A modal offers **USB o disco externo
  (Recomendado)** and **Esta máquina**. Pick **Esta máquina**: `ember-backup-<fecha>.zip` appears in
  `%ProgramData%\EmberHub\backups\` and in the card's list, with the "copia el archivo a una USB" notice.
  Open the zip: it has `manifest.json` (its `appVersion` is a real version, **not** `unknown`),
  `postgres.dump` and `minio/`.

- [ ] **15. Backup to a USB, and a missing USB.**
  **Respaldar ahora → USB o disco externo** opens the native folder picker; the zip lands on that
  drive. Unplug it and repeat: the card shows a red banner "No se pudo escribir en la carpeta de
  respaldo… Si es una USB, revisa que esté conectada." and nothing crashes.

- [ ] **16. Settings + images round trip via an uploaded file.**
  In the app change a restaurant Setting (e.g. tax rate) and upload a branding logo → **Respaldar
  ahora**. Change the setting again and replace the logo. Copy the zip to the Desktop →
  **Restaurar desde archivo…**, pick it, confirm. The Hub restarts by itself; the *original* setting
  and logo are back, and `backups\` now also holds an `…-pre-restore.zip`.

- [ ] **17. Recover from a corrupted database.**
  Stop the Hub. Overwrite `%ProgramData%\EmberHub\data\postgres\PG_VERSION` with `99`. Start: the
  PostgreSQL card shows an error. **Restaurar desde archivo…** with a good zip → "No se pudo guardar
  la copia previa" → **Restaurar sin copia previa**. The Hub comes up with the backup's data, and
  `%ProgramData%\EmberHub\data\postgres.corrupt-<fecha>\` exists next to the new `postgres\`.

- [ ] **18. Newer-version guard.**
  Edit a copy of a backup's `manifest.json` so `appVersion` is `99.0.0`, re-zip, pick it: the card
  shows "Este respaldo es de una versión más reciente de Ember (99.0.0)…", no confirmation opens, no
  data changes.

- [ ] **19. Automatic backup.**
  On a Hub with no backups leave it running ~20 min: a backup appears in the automatic folder and
  "Próximo automático" shows ~24 h later. **Cambiar carpeta automática…** points future automatic
  backups at a USB.

---

## Result

**Go / No-Go:** ____
```

- [ ] **Step 4: Full backend + frontend verification (CLAUDE.md zero-tolerance)**

Run: `cd backend && ./mvnw test` → Expected: all green (baseline 1314 + ~40 new backup tests).
Run: `cd frontend && pnpm run build` → Expected: unchanged and clean (this feature does not touch it).
Run: `cd ember-hub/ui && pnpm test && pnpm run build` → Expected: green.

- [ ] **Step 5: Update `PROGRESS.md`, write the report, commit**

In `PROGRESS.md`: set *Last Completed Task* to report 514 (HUB-BACKUP-RESTORE), refresh *System Health* with the new test counts, tick every HUB-BACKUP-RESTORE checkbox, and in **Open / deferred** replace the `HUB-BACKUP-RESTORE` line with a one-line "done, code-complete; manual `VERIFY.md` items 14–19 and an installer rebuild (`ember-hub/build-installer.ps1` — the backend jar changed, the Tauri shell did not) still pending". Keep the file under 180 lines.
Write `reports/514-task-hub-backup-6-dashboard-wiring.md`.
```bash
git add ember-hub/ui/src/components/Dashboard.tsx ember-hub/VERIFY.md PROGRESS.md reports/514-task-hub-backup-6-dashboard-wiring.md
git commit -m "feat(hub-ui): mount backup card in the dashboard and add manual verification steps"
```
Then remind the user to run `/clear`, rebuild the Hub installer and run `VERIFY.md` items 14–19 on a real install.

---

## Self-Review

**Spec coverage** (`2026-09-14-hub-backup-restore-design.md` + the 2026-09-19 asks):

| Requirement | Task |
|---|---|
| Manual "Respaldar ahora" + automatic schedule | 2 (`runBackup`, `runScheduledIfDue`), 4 (`BackupScheduler`), 5 (button) |
| Modal: USB recommended vs. this machine (user, 2026-09-19) | 5 (`BackupCard` destination modal, `defaultDestDir`) |
| Upload the file from the dashboard to restore (user) | 5 (`pickBackupFile` → `inspectBackup` → confirm), 6 (native picker) |
| Settings saved (user) | Deviation 9 — inside `pg_dump`; proven by the settings round-trip in Task 3's test and `VERIFY.md` #16 |
| Postgres + MinIO backed up; license/`state.json` excluded | 1 (`BackupArchive`), 2 (`doBackup`) |
| Retention 7, pre-restore never pruned | 2 (`prune`, only on scheduled runs) |
| Version compatibility block | 1 (`HubVersion`), 2 (`inspect`), 3 (restore gate), 5 (message) |
| Crash-safe `.tmp` → atomic rename | 2 (`doBackup`) |
| Pre-restore safety snapshot | 3 (`restore`) + `SAFETY_FAILED` continue path |
| Never throws out of the scheduled job | 2 (`runBackup` catches), 4 (`tick` catches) |
| Config outside Postgres | 1 (`BackupConfigStore` → `hub-backup.json`) |
| HubControlServer routes | 4 |
| No new dependency / no Flyway | all tasks |
| Manual (non-automatable) checks | 6 (`VERIFY.md` 14–19) |

**Placeholder scan:** none — every code step carries full code; report/commit steps name exact paths.

**Type consistency:** `BackupSnapshot` (8 fields) / `BackupStatus` (5 fields, incl. `defaultDestDir`) are identical in Tasks 1, 2, 4 (`FakeBackup`) and 5 (`types.ts`); `HubBackup` gains `restore` in Task 3 and `FakeBackup` in Task 4 implements it; `HubOrchestrator.stopAndWait` is added to both fakes in Task 3; error codes match between `BackupException` constants, the control-server status mapping and the frontend's `BACKUP_SAFETY_FAILED` check.

**Known gaps (deliberate):** restore does not roll back automatically if `pg_restore` fails midway (spec §3.3 step 7: the `-pre-restore` file is the recovery path, its location is in the error message); no cloud upload; MinIO is copied live during a backup (a half-written object is possible under heavy upload — acceptable per the spec); the Hub-local printing workstream (`HUB-LOCAL-PRINTING`) is a separate plan.
