# Report 228 — HUB-01-06: PortableDatabaseBootstrap

## 1. Identification
- **Report:** 228
- **Task ID:** HUB-01-06
- **Predecessor Task:** HUB-01-05 (report 227)

## 2. Objective
Sixth task of the Ember Hub HUB-01 plan (`docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`): a pre-Spring bootstrap component that ensures the portable, unpackaged local Postgres binaries are running before Spring's own `DataSource` ever tries to connect, covering the three boot-error scenarios from spec §4 (port already in use, first-run empty data directory via `initdb`, and a data directory `pg_ctl` refuses to start) with distinct, actionable Spanish error messages instead of a generic stack trace.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseException.java` (new)
- `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java` (new)
- `backend/src/test/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrapTest.java` (new)

## 4. What Changed?
- `PortableDatabaseException`: checked exception wrapping any failure to get the portable Postgres ready to accept connections.
- `PortableDatabaseBootstrap(dataDir, postgresBinDir, port)`: `ensureRunning()` — (1) throws if `port` is already bound (`isPortInUse`, a real `ServerSocket` bind/release probe); (2) if `dataDir/PG_VERSION` is absent, shells out to `initdb` with a throwaway one-time password file (`-U ember`, `-E UTF8`), deleting the password file in a `finally` regardless of outcome; (3) shells out to `pg_ctl start -w` against `dataDir` on `port`, and on non-zero exit appends the last 20 lines of `postgres.log` to the exception message. `initdb`/`pg_ctl` failures and `InterruptedException` are both wrapped into `PortableDatabaseException` with an actionable Spanish message; interrupts also restore the thread's interrupt flag.
- Test covers only `isPortInUse` (package-private, called with an explicitly free ephemeral port vs. a port occupied by a real bound `ServerSocket`) — the only piece of logic that doesn't require real `initdb`/`pg_ctl` binaries, which aren't bundled in this repo yet (that's HUB-03's job per the plan). The full `ensureRunning` flow against real binaries is deferred to HUB-01-11's manual verification on Windows with an actual portable Postgres distribution.
- Implementation taken verbatim from the plan (`docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`, Task 6).

## 5. Why It Changed?
Ember Hub (spec `docs/superpowers/specs/ember_hub.md`) runs the platform fully offline on a restaurant's own Windows PC, bundling its own portable Postgres rather than depending on a pre-installed server. `EmberApplication.main` (HUB-01-07, next task) needs a pre-Spring hook that guarantees Postgres is actually listening before Spring Boot's `DataSource` auto-configuration attempts its first connection — without this, a first-run empty data directory, a stale lock from a crashed previous run, or a port collision with another local service would surface as an opaque Spring `DataSource` connection-refused stack trace instead of a message an end user (not a developer) can act on.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=PortableDatabaseBootstrapTest` — confirmed FAIL (class didn't exist) before implementation, PASS (2/2) after.
- `cd backend && ./mvnw test` — full suite: 803/803 PASS, 0 failures, 0 errors (801 pre-existing + 2 new).
