# Report 536 — F-21: random Hub credentials + real local auth enforcement

**Predecessor:** report 535 (PLATFORM-OPERATOR-RBAC / F-14).

## Objective
Close security finding F-21: the Hub's portable Postgres/MinIO were bootstrapped with hardcoded
credentials (`ember`/`ember`, `ember-hub-local`) baked into the code, identical across every
Hub install. Scope was expanded mid-task (user-approved) after discovering, via a real
`initdb` run, that local Postgres was additionally running under `trust` authentication —
meaning the password was never actually checked by anything, hardcoded or not.

## Modified Files
- `ember-hub/src-tauri/src/main.rs`
- `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableMinioBootstrap.java`
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/HubBootstrapRunner.java`
- `backend/src/main/java/com/vanter/ember/hub/backup/HubBackupService.java`
- `backend/src/main/java/com/vanter/ember/hub/backup/PostgresTools.java`
- `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- `backend/src/main/resources/application-hub.yml`
- `backend/src/test/java/com/vanter/ember/hub/backup/HubBackupRestoreIntegrationTest.java`
- `backend/src/test/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrapCredentialsIntegrationTest.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/bootstrap/PortableMinioBootstrapCredentialsIntegrationTest.java` (new)

## What Changed?
- **Rust (`ensure_hub_env`)**: generates `EMBER_HUB_POSTGRES_PASSWORD`/`EMBER_HUB_MINIO_SECRET_KEY`
  (24-byte CSPRNG hex, same `random_hex` used for `JWT_SECRET`) into `hub.env` on first run only
  — matches the existing JWT-secret pattern exactly. Content generation split into a pure
  `hub_env_contents` function so the secrets' presence/freshness is unit-testable without
  touching `%ProgramData%`.
- **`HubProperties`**: gained `postgresPassword`/`minioSecretKey` (read from the same env vars,
  default `"ember"`/`"ember-hub-local"` when absent — an install from before this change, or a
  manual local run). Canonical constructor now 15-arg; the two older shapes (11-arg, 13-arg) are
  kept as back-compat overloads defaulting to the historical credentials, so no existing call
  site needed to change.
- **`PortableDatabaseBootstrap`**: takes the password via a 4th constructor arg (3-arg overload
  defaults to `"ember"`), uses it for `initdb --pwfile`. **Also now passes `-A scram-sha-256` to
  `initdb`** — without it, Windows's `initdb` silently defaults local/loopback connections to
  `trust`, so the password above was never actually enforced. `createdb` (in
  `ensureApplicationDatabaseExists`, which now needs real auth) gets `PGPASSWORD` in its process
  environment.
- **`PortableMinioBootstrap`**: takes the secret key via a 4th constructor arg (3-arg overload
  defaults to `"ember-hub-local"`), passes it as `MINIO_ROOT_PASSWORD`. Unlike Postgres, MinIO
  re-reads its root credentials from the environment on every launch — proven empirically
  (see Verification) that restarting the *same* data directory under a *different* secret needs
  no migration.
- **`PostgresTools`**: takes the password via a 3rd constructor arg (2-arg overload defaults to
  `"ember"`), sets `PGPASSWORD` in the environment of every `pg_dump`/`dropdb`/`createdb`/
  `pg_restore` invocation (never as a CLI argument, which would leak it via the process list).
- **`application-hub.yml`**: `spring.datasource.password`/`minio.secret-key` now read
  `${EMBER_HUB_POSTGRES_PASSWORD:ember}`/`${EMBER_HUB_MINIO_SECRET_KEY:ember-hub-local}`.
- **`HubBackupService`**/`EmberApplication`: both `PortableDatabaseBootstrap`/`PostgresTools`
  construction sites now thread `properties.postgresPassword()` through.

## Why It Changed?
F-21 (`AUDIT_BLUEPRINT.md`) flagged the hardcoded credentials as a real bootstrap secret shared
identically across every Hub installation. Mid-task, running a real `initdb` (the vendored
Postgres 16 binaries under `ember-hub/.vendor-cache/`) showed it explicitly warns *"activando el
método de autentificación 'trust' para conexiones locales"* — Windows's `initdb` defaults to
`trust` when `-A`/`--auth-host` isn't passed, which this code never did. That means **any local
process on the same machine, regardless of Windows user, could already connect to the Hub's
Postgres with any password or none at all** — a materially more severe gap than "the known
password is hardcoded". Randomizing the password alone would not have closed it. Presented to
the user as a scope decision (bigger fix touches the already-shipped, tested backup/restore
subsystem, which explicitly documented its "trust" assumption); user chose to expand scope now
rather than leave it half-fixed.

## Verification
Real vendored Postgres 16 + MinIO binaries (`ember-hub/.vendor-cache/`) — this is genuine
end-to-end verification, not mocked:
- `PortableDatabaseBootstrapCredentialsIntegrationTest`: fresh `initdb` with a random password —
  connecting with that password succeeds; connecting with the old hardcoded `"ember"` now
  correctly **fails** (this assertion failed before the `-A scram-sha-256` fix, confirming the
  bug was real, and passes after it).
- `PortableMinioBootstrapCredentialsIntegrationTest`: fresh install only accepts its own secret;
  restarting the *same* data directory under a *different* secret works immediately (proves no
  MinIO migration is needed, unlike Postgres).
- `HubBackupRestoreIntegrationTest` (existing, updated to use a non-default password
  end-to-end): full real backup → mutate → restore round trip, **and** the corrupt-data-dir
  recovery path, both still pass with `scram-sha-256` enforced — the shipped backup/restore
  feature was not broken by this change.
- `cd backend && ./mvnw test`: **1492/1492** (1489 + 3 new tests), excluding the 2 pre-existing,
  unrelated `PortableDatabaseBootstrapTest`/`PortableMinioBootstrapTest#isPortInUse_falseWhenPortIsFree`
  failures (same environment port-59999 quirk noted in report 535).
- **Rust side (`cargo check`/`cargo test`) could not be run in this sandbox** — fails identically
  before and after this change (confirmed via both Bash and native PowerShell, and that
  `Ember Hub.exe` isn't locked by any running process) with `Acceso denegado` reading the bundled
  `dist/app-image/Ember Hub/...` files the Tauri build script watches — a pre-existing
  environment/permissions issue unrelated to this diff. The Rust change itself is small (two new
  `format!` args reusing the exact `random_hex` pattern already shipped for `JWT_SECRET`) and was
  reviewed carefully; it should be spot-checked with a real `cargo build` before the next Hub
  installer is cut.

## Out of scope (unchanged from the approved plan)
- Rotating credentials — or auth mode — for **already-installed** Hubs: their `hub.env` predates
  this change (so `ensure_hub_env` never touches it) and their Postgres data directory already
  has `trust` + the old password baked in from its own `initdb` run. Needs a dedicated migration
  (connect with the known legacy default, `ALTER ROLE`, rewrite `pg_hba.conf`) — explicitly not
  attempted here, matching the original audit's own caution that this "no se puede verificar sin
  un instalador portátil real."
