# Report 400 — Hub v2 prep: residual hardening

## 1. Identification
- **Report number:** 400
- **Current Task:** clear the 3 residual items from the report-399 audit so Hub v2 (Tauri shell) can start
- **Predecessor Task:** report 399 — Hub seat hardening + public guest code-join

## 2. Objective
Close the three loose ends left after report 399:
1. Optimistic-lock failures on the seat endpoints still surfaced as HTTP 500.
2. `isHubBuild` was read at module load and inferred only from `BASE_URL` — fragile, untestable
   without module-graph resets, and about to break under a Tauri shell (base `/`).
3. Prod Flyway is not baselined; `V9` could meet a schema where its column already exists.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- `backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerTest.java`
- `backend/src/main/resources/db/migration/V9__user_guest_flag.sql`
- `frontend/src/lib/isHubBuild.ts`
- `frontend/src/vite-env.d.ts` (new)
- `frontend/.env.hub` (new)
- `frontend/package.json`
- `frontend/src/App.tsx`, `frontend/src/components/GlobalSearchResults.tsx`,
  `frontend/src/components/SettingsBar.tsx`, `frontend/src/pages/admin/Settings.tsx`,
  `frontend/src/pages/auth/Login.tsx`, `frontend/src/pages/waiter/TableInformation.tsx`,
  `frontend/src/pages/waiter/components/ParticipantsQrModal.tsx`
- `frontend/src/App.hubBuild.test.tsx`, `frontend/src/components/SettingsBar.hubBuild.test.tsx`,
  `frontend/src/pages/waiter/TableInformation.seats.test.tsx`,
  `frontend/src/pages/waiter/components/ParticipantsQrModal.test.tsx`
- `.github/workflows/lint.yml`
- `.gitignore`

## 4. What Changed?

### Item 1 — optimistic-lock → 409
`GlobalExceptionHandler` now maps `org.springframework.dao.OptimisticLockingFailureException`
(and its `ObjectOptimisticLockingFailureException` subclass) to a 409 ProblemDetail with a fixed
client-facing message and `code: "CONCURRENT_MODIFICATION"`. Before, a lost `@Version` check on a
concurrent seat edit fell through to the `Exception` catch-all → 500 with a traceId. The original
message is dropped because it leaks entity/table names. One slice test added.

### Item 2 — `isHubBuild` made lazy and explicit
- `isHubBuild` changed from a module-level `const` to `export const isHubBuild = () =>
  import.meta.env.VITE_HUB_BUILD === 'true' || import.meta.env.BASE_URL !== '/'`. The env flag is
  the canonical signal; the `BASE_URL` check stays as a fallback for the current
  `vite build --base=/app/` output until the Tauri shell replaces it.
- All ~10 call sites updated to `isHubBuild()`. `SettingsBar`'s module-scope `SETTINGS_NAV`
  became `buildSettingsNav()`, called once per render.
- `frontend/.env.hub` (`VITE_HUB_BUILD=true`) + `build:hub` now runs `vite build --base=/app/
  --mode hub`. Cross-platform, no new dependency.
- `frontend/src/vite-env.d.ts` declares `VITE_HUB_BUILD` on `ImportMetaEnv`.
- The 4 tests that mocked `isHubBuild` switched from a `get isHubBuild()` accessor to
  `isHubBuild: () => hubFlag.current`. `SettingsBar.hubBuild.test.tsx` dropped its
  `vi.resetModules()` + dynamic `import()` — a static import works now.
- `lint.yml` gains a `build-hub` job: `pnpm run build:hub` + `pnpm run test:run`, so CI verifies
  the Hub variant type-checks, bundles, and passes tests on every push — not just the Cloud build.

### Item 3 — `V9` made idempotent
`V9__user_guest_flag.sql` now `ALTER TABLE users ADD COLUMN IF NOT EXISTS guest ...`. `V9` is
unreleased on this branch and not recorded in any persistent `flyway_schema_history` (local dev
skips migrations ≤ 15; prod has not run it), so the checksum change breaks nothing. `V7`/`V8`
were left untouched — editing a migration already applied elsewhere would fail Flyway's
`validate-on-migrate`. **Not done here:** a real prod Flyway baseline — that needs an inspection
of prod's `flyway_schema_history` from Cloud Shell and is tracked as an ops task, not a code
change. It does not block Hub v2 (the on-prem Hub bootstraps its bundled Postgres from `V1`).

### Housekeeping
`.gitignore` also picks up the archify tooling entries staged earlier this session
(`skills-lock.json`, `docs/diagrams/`).

## 5. Why It Changed?
- **1:** `CLAUDE.md` priority #3 — race-condition safety in billing/seat paths. Report 399 added
  the `@Transactional` boundary but left the 500 on the lost race.
- **2:** Hub v2 is a Tauri/webview shell that loads from `tauri://localhost` with base `/`, so the
  `BASE_URL !== '/'` heuristic would misclassify it as a Cloud build. An explicit build flag +
  a CI job that exercises the Hub variant are prerequisites for that work.
- **3:** the 2026-09-06 prod outage came from a migration colliding with hand-applied DDL.
  Idempotent DDL on the one still-pending migration removes that failure mode cheaply; the full
  baseline is a separate, riskier ops task.

## System Health
- backend `./mvnw test` — **1196 / 1196**
- frontend `pnpm run build` (Cloud) + `pnpm run build:hub` (Hub) — both clean
- frontend `pnpm run lint` — 0 errors, 16 pre-existing warnings
- frontend `pnpm run test:run` — **118 / 118**
