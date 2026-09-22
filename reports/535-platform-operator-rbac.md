# Report 535 — F-14: RBAC en `/platform/**`

**Predecessor:** report 534 (RESTAURANT-DEPLOYMENT-MODE).

## Objective
Cerrar el hallazgo de seguridad F-14: `/platform/**` no tenía control de rol — cualquier
operador de plataforma autenticado podía crear, suspender, borrar o cambiar el plan de
cualquier restaurante. `PlatformOperator` no tenía columna `role` y ningún controlador
declaraba `@PreAuthorize`.

## Modified Files
- `backend/src/main/resources/db/migration/V16__platform_operator_role.sql` (nuevo)
- `backend/src/main/java/com/vanter/ember/platform/model/PlatformOperatorRole.java` (nuevo)
- `backend/src/main/java/com/vanter/ember/platform/model/PlatformOperator.java`
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformOperatorDetailsService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformStatsController.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformAuditLogController.java`
- `backend/src/test/java/com/vanter/ember/platform/repository/PlatformOperatorRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformOperatorDetailsServiceTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformStatsControllerTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformAuditLogControllerTest.java`

## What Changed?
- `PlatformOperatorRole` enum (`SUPER_ADMIN`, `SUPPORT`); `PlatformOperator` gained an
  `@Enumerated(EnumType.STRING)` `role` column (`V16`, idempotent `DROP...IF EXISTS` + re-`ADD`
  `CHECK` constraint, same pattern as `V11`/`V13`). Existing rows backfilled to `SUPER_ADMIN`
  — preserves every current operator's access exactly as it was before this migration.
- `PlatformOperatorDetailsService` now grants `ROLE_<operator.role>` instead of a hardcoded
  `PLATFORM_ADMIN` authority for every operator.
- `@PreAuthorize("hasRole('SUPER_ADMIN')")` on every mutating endpoint in
  `PlatformRestaurantController` (create, delete, restore, hub-license issue, status/mode/plan
  updates); `@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SUPPORT')")` on read endpoints
  (`getAll`, `getById`, `PlatformStatsController.get`, `PlatformAuditLogController.getAll`).
  Login and self-service password change stay open to any authenticated operator (unchanged).
- Test fixtures updated to set `role` where the JPA `NOT NULL` constraint or
  `PlatformOperatorDetailsService`'s `.name()` call requires it. `PlatformRestaurantController`,
  `PlatformStatsController`, and `PlatformAuditLogController`'s `@WebMvcTest` slices each gained
  a small `@TestConfiguration @EnableMethodSecurity` nested class (needed to activate
  `@PreAuthorize` in the slice without pulling in the tenant `SecurityConfig`'s bean graph) and
  new tests proving a `SUPPORT` operator gets `403` on every mutating endpoint and `200` on every
  read endpoint, plus one new `PlatformOperatorDetailsServiceTest` case for the `SUPPORT` grant.

## Why It Changed?
F-14 (`AUDIT_BLUEPRINT.md`) was left open by design because there was only one operator
"role" in practice; it becomes a real gap the moment a second platform account (support, a
partner) exists, since today that account would have the same destructive power as a
super-admin with no way to scope it down. This closes the gap with the minimum viable
role split (`SUPER_ADMIN` full access, `SUPPORT` read-only) and, critically, proves it with
tests that exercise the real `@PreAuthorize` interceptor — not just the annotation's presence.

## Verification
- `cd backend && ./mvnw test -Dtest='com.vanter.ember.platform.**'` — all green, including the
  new SUPPORT/SUPER_ADMIN authorization tests.
- `cd backend && ./mvnw test` — **1489/1489** excluding 2 pre-existing, unrelated failures
  (`PortableDatabaseBootstrapTest`/`PortableMinioBootstrapTest#isPortInUse_falseWhenPortIsFree`)
  confirmed failing identically on `main` before this change (verified via `git stash`) — an
  environment-specific port-59999 check on this machine, not caused by this task.

## Out of scope
- No frontend/Console change: there is no operator-management UI today (new operators are
  inserted by hand in the DB); exposing `role` in the login response / Console is a follow-up
  if that UI gets built.
- `F-15` (Hub activation returns `adminPasswordHash`) and `F-21` (Hub bootstrap credentials in
  clear) remain open — separate tasks per the prioritized list.
