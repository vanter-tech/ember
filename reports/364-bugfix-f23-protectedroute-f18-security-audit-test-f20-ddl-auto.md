# Report 364 — bugfix: F-23 ProtectedRoute redirect, F-18 stale test routes, F-20 ddl-auto default

## 1. Identification
- **Report:** 364
- **Task:** bugfix batch — F-23, F-18 (partial), F-20 from `AUDIT_BLUEPRINT.md` (user-requested
  follow-up after reviewing the remaining open findings in `QA_SIMULATION_REPORT_v2.md`)
- **Predecessor:** report 363 (E-18 dialog descriptions)

## 2. Objective
Close three findings the user asked to fix directly (no product decision needed), out of the
still-open `AUDIT_BLUEPRINT.md` backlog: F-23 (`ProtectedRoute` doesn't redirect when `role` is
`undefined`), F-18 (`SecurityAuditTest` asserts against a nonexistent `/api/**` prefix and is
missing ~14 real routes), and F-20 (`ddl-auto` defaults to `update` outside `prod`, already flagged
as a queued follow-up in its own code comment).

Two other findings from the same review (F-15, F-21) were investigated but **not** changed — see
§6.

## 3. Modified Files
- `frontend/src/components/ProtectedRoute.tsx`
- `frontend/src/components/ProtectedRoute.test.tsx` (new)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `backend/src/main/resources/application.yml`
- `.env` (local, gitignored — not part of this commit)

## 4. What Changed?

### F-23 — `ProtectedRoute` blank/`<Outlet/>` when `role` is `undefined`
`if (role && !allowedRoles.includes(role))` never evaluates true when `role` is falsy, so a token
present with an undefined role (a partially rehydrated store) fell through to `<Outlet/>` instead
of being treated as unauthenticated. Split into two checks: `!role` now redirects to `/login`
(matching `AUDIT_BLUEPRINT.md` S8-01's expected behavior — treat it as unauthenticated, not as a
403), then the existing role-mismatch check runs only once `role` is confirmed present. New
`ProtectedRoute.test.tsx` (4 tests): no token → `/login`; token + undefined role → `/login`; token
+ wrong role → 403 message; token + matching role → outlet content.

### F-18 — stale `/api/**` prefix + missing routes in `SecurityAuditTest`
`sed 's#/api/#/#g'` removed the invalid prefix from all 48 affected rows — the app's context path
is `/v1/` (set at the server level, not visible to `MockMvc`), and real controller mappings are
bare (`/catalog/categories`, `/sessions/{id}`, etc., matching the ~20 rows that already had no
prefix). Also added the 14 routes `AUDIT_BLUEPRINT.md` F-18 named as missing from the matrix:
`/settings` (GET+PUT), `/dashboard/status`, `/identity/waiters`, `/menu`, `/admin/restaurant`,
`/sessions/{id}/transfer|waiter-items|cancel|leave|resume`, `/billing/bills/{id}/settle|splits/
redistribute`, `/printing/bills/{id}/receipt`. **Not done:** the bigger S2-11 addition (a second
matrix asserting `403` for every `@PreAuthorize`'d route hit with the wrong role) — out of scope
for this round, left as a follow-up.

### F-20 — `ddl-auto` default
`application.yml`: `ddl-auto: ${DDL_AUTO:update}` → `${DDL_AUTO:validate}`, matching what
`application-prod.properties`, `application-hub.yml`, and the test profile already do. Verified
live: booted the backend with `DDL_AUTO=validate` (implicit, i.e. the new default) against the
current dev Postgres — Flyway validated all 7 migrations and Hibernate's schema check passed
cleanly, confirming the dev DB is already in sync with the entities. A developer mid-schema-change
before writing its migration can still export `DDL_AUTO=update` locally. Also updated the local
(gitignored, not committed) `.env`'s `DDL_AUTO=update` → `validate` — that file is `env_file`'d into
`docker-compose.yml`'s `app` service, which runs the `prod` profile; leaving it at `update` there
would have silently overridden `application-prod.properties`'s own `validate` default.

## 5. Why It Changed?
- F-23: a real (if narrow) auth-bypass-adjacent bug — an inconsistent/partial auth state should
  never render protected content, and the codebase already treats "no token" this way; "token but
  no role" is the same failure mode.
- F-18: the existing test suite was giving false confidence — every row passed regardless of the
  path's correctness, because `anyRequest().authenticated()` 401s literally any unmapped path too.
  Fixing the paths (and adding the missing ones) makes the assertions mean something again, even
  though the S2-11 role-matrix half of the finding remains open.
- F-20: `ddl-auto=update` lets Hibernate silently alter a real developer's or (via the `.env` leak)
  a prod-profile container's schema instead of Flyway being the single source of truth — exactly
  the risk the existing code comment already called out as a queued fix.

## 6. Investigated, not changed: F-15 and F-21
Both trace to hardcoded secrets baked directly into the **Hub's own bootstrap code**, not just
config:
- F-15 (`HubActivationService.activate` returns `adminPasswordHash`): intentional today so the
  on-prem Hub can seed its local admin row. Fixing it properly means redesigning how the Hub
  bootstraps its first admin account (e.g. a self-generated temporary password) — the user asked
  for this to be redesigned rather than kept, but it needs its own dedicated plan (touches the
  activation contract, the Hub's own bootstrap sequence, and needs an upgrade/migration path for
  already-issued licenses), not a same-session patch.
- F-21 (`application-hub.yml`'s hardcoded `password: ember` / `secret-key: ember-hub-local`):
  traced to `PortableDatabaseBootstrap.java:79` (`Files.writeString(pwFile, "ember")` at `initdb`
  time) and `PortableMinioBootstrap.java:106` (`MINIO_ROOT_PASSWORD=ember-hub-local` hardcoded when
  spawning the process) — these are the actual credentials of the locally-running Postgres/MinIO
  instances, not just Spring config reading them. Making these random-per-install would need a
  migration path for **already-installed** Hub instances (their local Postgres already has "ember"
  as its real password baked into its own auth catalog) that I can't verify without an actual
  portable installer environment to test against. Flagged as its own follow-up rather than risking
  an untested change to a packaged product's bootstrap.

## 7. Verification
- Backend: `./mvnw test -Dtest=SecurityAuditTest` — 91/91 (was ~90, +14 new routes − 0 removed,
  net effect of dedup is negligible). Full suite `./mvnw test` — **1044/1044 BUILD SUCCESS** (was
  1030; +14 from the new `SecurityAuditTest` rows).
- Frontend: `pnpm run build` PASS (0 TS errors); `pnpm run lint` 0 errors (16 pre-existing
  warnings, none in touched files); `pnpm run test:run` — **77/77** (24 files, was 73/23; +4 new
  `ProtectedRoute` tests).
