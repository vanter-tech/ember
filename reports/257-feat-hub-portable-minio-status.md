# Report 257 — feat: portable MinIO bootstrap + status indicator for Ember Hub

## 1. Identification
- **Report number:** 257
- **Task ID:** `feat-hub-portable-minio` (post-HUB-01 gap, not part of a numbered plan — see `PROGRESS.md` "Ember Hub — post-HUB-01")
- **Predecessor Task:** report 256 (`task-10-hub-license-activation-verification`)

## 2. Objective
Close the gap flagged in `PROGRESS.md` on 2026-08-25: Ember Hub had no portable MinIO of its own,
so `MinioConfig.ensureBucketExists` silently failed on any customer PC without Docker/MinIO
running, breaking image uploads (restaurant logo, menu item photos) with no visible error. Add a
portable MinIO bootstrap mirroring the existing `PortableDatabaseBootstrap` (Postgres) pattern,
plus a status indicator in `HubDashboard`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableMinioBootstrap.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableMinioException.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/bootstrap/PortableMinioBootstrapTest.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`
- `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java`
- `backend/src/main/resources/application-hub.yml`
- `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`

## 4. What Changed?
- **`PortableMinioBootstrap`**: same lifecycle shape as `PortableDatabaseBootstrap` — port-in-use
  check, `ProcessBuilder` launch of `minio.exe server <dataDir> --address 127.0.0.1:<port>` with
  `MINIO_ROOT_USER=ember-hub` / `MINIO_ROOT_PASSWORD=ember-hub-local` / `MINIO_BROWSER=off`, all
  local-only (no web console, no non-loopback bind). Two behavioral differences from Postgres,
  both required by MinIO's own shape: (1) no blocking "wait until ready" start flag, so
  `ensureRunning()` polls `GET /minio/health/live` every 200ms up to a 15s timeout instead; (2) no
  on-disk pid file, so `stop()` tracks the live `Process` handle in memory and
  `destroy()`/`destroyForcibly()`s it, relying on the same port-in-use check to catch an orphaned
  process from a prior crash on the next start (same recovery path Postgres already has).
- **`HubProperties`**: added `minioDataDir` (`EMBER_HUB_MINIO_DATA_DIR`, default `./data/minio`),
  `minioBinDir` (`EMBER_HUB_MINIO_BIN_DIR`, default `./minio/bin`), `minioPort`
  (`EMBER_HUB_MINIO_PORT`, default `9000`) — same naming convention as the existing Postgres fields.
- **`HubBootstrapRunner`**: starts `PortableMinioBootstrap` right after Postgres in
  `startServices()`, stops it first (before Postgres) in `stopServicesQuietly()`.
- **`application-hub.yml`**: added `minio.url`/`access-key`/`secret-key`/`bucket` — previously
  absent, so hub profile inherited `application.yml`'s `MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY` env
  vars with no default, which were never set on a customer PC.
- **`HubDashboard`**: added a third `JLabel` ("MinIO: detenido/iniciando/en ejecución/deteniendo"),
  flipped at the same lifecycle points as the existing Postgres label. No new error-handling code
  path — a MinIO failure propagates through `HubBootstrapRunner.startServices()`'s existing
  `Exception` catch block in `startServicesInBackground()`, the same `JOptionPane` dialog Postgres
  failures already use.
- Two pre-existing test files needed updates for the `HubProperties` record's 3 new trailing
  positional fields (`HubPropertiesTest`, `HubProvisioningRunnerTest`) — no behavior change, just
  constructor call sites.

## 5. Why It Changed?
Brainstormed with the user: Ember Hub's on-premise/offline v1 was built assuming Postgres would be
bootstrapped portably (already done, HUB-01-06) but never did the same for MinIO — the
`ensureBucketExists` bean ran unconditionally and only logged a warning on failure, so this went
unnoticed through all of HUB-01-11's manual verification. The user's own philosophy for Hub (spec
§2.3: "mismo JAR, mismo motor, solo local") was extended from Postgres to MinIO rather than
swapping to local-disk storage, keeping `MinioClient`/`ImageUploadService` unchanged. The MinIO web
console and non-loopback binding were deliberately left out — the Hub dashboard is meant to stay
the single pane of glass for the restaurant owner, not one more URL/port to manage.

This is the first (A+B) of four gaps identified in the same brainstorming session; Windows service
auto-start (`sc.exe`), the `jpackage`/`jlink` installer, and the Tauri shell rewrite remain
deliberately out of scope for this task — see `PROGRESS.md`.

## 6. Verification
- `cd backend && ./mvnw test -Dtest="com.vanter.ember.hub.**"` → 26/26 PASS.
- `cd backend && ./mvnw test` (full suite) → 846/846 PASS, BUILD SUCCESS.
- Not manually verified end-to-end against a real `minio.exe` binary in this task (none bundled
  yet — same situation `PortableDatabaseBootstrap` was in before HUB-01-11's manual verification).
  Needs a real Windows run with an official MinIO binary before shipping, same as Postgres did.
