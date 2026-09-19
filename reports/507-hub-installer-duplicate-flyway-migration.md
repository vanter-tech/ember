# Report 507

## 1. Identification
- **Report number:** 507
- **Task ID:** ad-hoc live bug — Ember Hub "Unable to start web server" after activating the license
- **Predecessor task:** report 506 (guest join loses CUSTOMER identity)

## 2. Objective
On a clean install (own PC and a friend's PC) Postgres and MinIO start, the license is accepted, but
the Hub's server card ends in an error showing only "Unable to start web server".

## 3. Modified Files
- `ember-hub/build-installer.ps1`

## 4. What Changed?
- The backend build step is now `mvnw -q -DskipTests clean package` (was `package`).
- After picking the jar, the script opens it and aborts if two entries under
  `BOOT-INF/classes/db/migration/` share a Flyway version number.

## 5. Why It Changed?
Running the sidecar by hand (`SPRING_PROFILES_ACTIVE=hub` + `hub.env`) showed the real cause behind
the generic message: `FlywayException: Found more than one migration with version 12`. The installed
`ember-hub.jar` (built 2026-09-18 17:57) contained both `V12__cash_shift_denomination_breakdown.sql`
and a stale `V12__widen_restaurants_status_check.sql`; in source the latter had been renamed to
`V13__...`, but the old file stayed in the untracked `backend/target/classes` because the build never
ran `clean`, and it was packaged. Every Hub installed from that `.exe` fails identically, regardless
of license, ports or machine. Ports were ruled out first (8080 free, no excluded TCP port ranges).

The guard catches the same class of problem if a stale jar ever slips in another way.

Known gap (not changed here): `DefaultHubOrchestrator.runStart` stores only `e.getMessage()`, so the
UI keeps showing the generic wrapper text instead of the root cause, and the sidecar's stdout is not
written to a file. Follow-up task if wanted.

Verification: `build-installer.ps1` parses cleanly (PowerShell parser, 0 errors); the duplicate check,
run against the broken installed jar, reports `V12`. The full installer build was not run here.
