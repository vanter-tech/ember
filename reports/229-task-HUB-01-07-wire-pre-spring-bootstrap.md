# Report 229 — HUB-01-07: Wire pre-Spring bootstrap into EmberApplication.main

## 1. Identification
- **Report:** 229
- **Task ID:** HUB-01-07
- **Predecessor Task:** HUB-01-06 (report 228)

## 2. Objective
Seventh task of the Ember Hub HUB-01 plan (`docs/superpowers/plans/2026-08-24-hub-01-bootstrap-and-licensing.md`): wire the license validation (Tasks 3–5) and portable Postgres bootstrap (Task 6) into `EmberApplication.main`, running before `SpringApplication.run` so Spring's own `DataSource` autoconfiguration never races an unready or unlicensed local Postgres, and add the `hub` Spring profile's datasource/server config.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/EmberApplication.java` (modified)
- `backend/src/main/resources/application-hub.yml` (new)

## 4. What Changed?
- `EmberApplication.main` now calls `isHubProfile()` (reads `SPRING_PROFILES_ACTIVE` env var, falling back to the `spring.profiles.active` system property) before `SpringApplication.run` — deliberately NOT read via Spring, since Spring hasn't started yet.
- When the `hub` profile is active, `bootstrapHub()` builds `HubProperties.fromEnvironment()`, then a `LicenseService` (loading the RSA public key, wiring `LicenseKeyParser`/`HardwareFingerprintService`/`HubStateStore`) and calls `validateOrActivate()`; then a `PortableDatabaseBootstrap` and calls `ensureRunning()`. Either failure path (`InvalidLicenseException`/`PortableDatabaseException`) calls `failToStart(message)` — prints an actionable Spanish message to stderr and `System.exit(1)`, never a raw stack trace.
- `application-hub.yml`: `spring.datasource.url` points at `localhost:${EMBER_HUB_POSTGRES_PORT:5432}/ember` (user/pass `ember`/`ember`, matching `PortableDatabaseBootstrap`'s `initdb -U ember`), `ddl-auto: validate` (Flyway still owns schema, per the existing convention), `server.port` from `${EMBER_HUB_SERVER_PORT:8080}`.
- Implementation taken verbatim from the plan (Task 7). Verified all 5 consumed classes' constructors/static methods against current code before writing — no drift from the plan's literal calls.

## 5. Why It Changed?
Spring's `DataSource` autoconfiguration connects to Postgres during context refresh, which runs before any `ApplicationRunner`/`CommandLineRunner` — too late to start portable Postgres or gate on a valid license from inside the normal Spring lifecycle. The only point early enough is `main`, before `SpringApplication.run` is even called.

## 6. Verification
- No new unit test for this task (per the plan): `main()` methods that call `System.exit` aren't practically unit-testable, and the real behavior (does Windows Postgres actually start?) can only be verified manually (HUB-01-11).
- `cd backend && ./mvnw test` — full suite: 803/803 PASS, 0 failures, 0 errors, same count as report 228 — confirms the default (non-hub) boot path is unaffected, since nothing runs under `SPRING_PROFILES_ACTIVE=hub` during the test suite.
