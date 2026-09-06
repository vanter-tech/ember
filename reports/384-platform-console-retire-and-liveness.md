# Report 384 — feat(platform): retire suspended tenants + Hub liveness

## Identification
- **Report:** 384
- **Task:** platform-console effort pieces **B** (delete suspended restaurants) + **C** (Hub
  heartbeat visibility), executed from
  `docs/superpowers/plans/2026-09-06-platform-console-retire-and-liveness.md`.
- **Predecessor:** report 383 (fix: tenant-scope + last-admin guard on `updateRole` — piece A).
- **Branch:** `spec/platform-console-retire-liveness` off `main`. 9 implementation commits
  (`ffcbb5eb` … `fc636b9a`) + spec + plan.

## Objective
The `/platform` operator console could suspend/reactivate a tenant but not **retire** one, and
could not tell whether a customer's Hub was actually running (the cloud answered each heartbeat
but persisted nothing). Give operators a reversible soft-delete gated on `SUSPENDED`, and a
per-restaurant Hub liveness indicator.

## Modified Files

**Backend — created**
- `backend/src/main/resources/db/migration/V8__restaurant_soft_delete_and_hub_heartbeat.sql`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/HubStatus.java`
- Tests: `RestaurantRepositorySoftDeleteTest`, `HubActivationHeartbeatTest`, `HubStatusTest`

**Backend — modified**
- `restaurant/model/RestaurantStatus.java` (+`DELETED`), `restaurant/model/Restaurant.java`
  (+`deletedAt`/`deletedBy`), `restaurant/repository/RestaurantRepository.java`
  (+`findByStatusNot`)
- `licensing/model/HubActivation.java` (+`lastHeartbeatAt`/`lastHeartbeatIp`),
  `licensing/repository/HubActivationRepository.java` (+`recordHeartbeat`, `findByRestaurantIdIn`),
  `licensing/service/HubHeartbeatService.java`, `licensing/controller/HubHeartbeatController.java`
- `platform/model/dto/PlatformRestaurantSummaryResponse.java` /
  `PlatformRestaurantDetailResponse.java` (Hub fields)
- `platform/service/PlatformRestaurantService.java` (`delete`/`restore`, `updateStatus` guards,
  `getAll(Pageable, boolean)`, `HubActivationRepository` ctor param)
- `platform/controller/PlatformRestaurantController.java` (`DELETE`, `POST /{id}/restore`,
  `?includeDeleted`)
- Tests: `PlatformRestaurantServiceTest`, `PlatformRestaurantControllerTest`,
  `HubHeartbeatServiceTest`, `HubHeartbeatControllerTest`

**Frontend — modified/created**
- `frontend/src/lib/platformApi.ts`
- `frontend/src/pages/console/ConsoleRestaurants.tsx` (+ `ConsoleRestaurants.test.tsx`)
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx` (+ `ConsoleRestaurantDetail.test.tsx`)

## What Changed?

**B — soft-delete.** `RestaurantStatus` gains `DELETED`; `restaurants` gains
`deleted_at`/`deleted_by` (write-time operator snapshot, no FK — `PlatformAuditLog` convention).
`DELETED` is a subtype of "not `ACTIVE`", so `SecurityConfig`, `SessionService` and
`HubHeartbeatService` already block it — no new `!= ACTIVE` branch anywhere.
`PlatformRestaurantService.delete` requires the restaurant be `SUSPENDED` (else
`IllegalStateException` → 409), sets `DELETED` + the metadata, writes a `RESTAURANT_DELETED`
audit row; `restore` is the inverse (`DELETED` → `SUSPENDED`, nulls the metadata,
`RESTAURANT_RESTORED`). `updateStatus` now rejects `DELETED` as a target and rejects any call on
a currently-`DELETED` restaurant. `GET /platform/restaurants` gained `?includeDeleted`
(default false → `findByStatusNot(DELETED, …)`).

**C — heartbeat liveness.** `hub_activations` gains `last_heartbeat_at`/`last_heartbeat_ip`.
`HubHeartbeatService.heartbeat(request, callerIp)` writes them on every verified beat via a
targeted `@Modifying(clearAutomatically = true)` `recordHeartbeat` UPDATE — **best-effort**: a
`DataAccessException` is logged and swallowed so a telemetry write failure never turns a valid
heartbeat into an error. `HubHeartbeatController` resolves the IP from `CF-Connecting-IP` →
first `X-Forwarded-For` hop → `getRemoteAddr()` (diagnostic only, no trusted-proxy check).
`HubStatus.from(lastHeartbeatAt, now)` derives `NEVER` (null) / `ONLINE` (≤15 min) / `STALE`
(≤24 h) / `OFFLINE`, surfaced on the summary DTO (list) and detail DTO (list + `hubActivatedAt`,
`lastHeartbeatAt`, `lastHeartbeatIp`); the service joins `HubActivation`
(`findByRestaurantIdIn` per page, `findByRestaurantId` for detail).

**Console UI (minimal — full redesign is piece D).** `ConsoleRestaurants`: a "Hub" column
(coloured dot + label) and a "Ver eliminados" checkbox; `DELETED` rows render muted with a
badge. `ConsoleRestaurantDetail`: a "Hub" panel, an "Eliminar restaurante" button (enabled only
when `SUSPENDED`, gated behind a type-the-slug confirm dialog) and, when `DELETED`, a "Restaurar
restaurante" button that replaces the status/license controls. `platformApi.ts` gains
`HubStatus`/`PlatformRestaurantStatus` types, the Hub fields, and
`deleteRestaurant`/`restoreRestaurant`/`getAll(page, size, includeDeleted)`.

## Why It Changed?
Soft-delete over hard cascade: bills/payments are financial records and the customer's Hub keeps
its own local DB, so nothing is safe to physically erase; a reversible status is enough because
every access path already fails closed on non-`ACTIVE`. Heartbeats every 5 min would flood the
audit log, so liveness is a derived status field, not audit rows. IP resolution is
best-effort-only because it is shown for diagnostics, never used for authorization.

## Plan deviations (applied inline during execution)
- Every new `@DataJpaTest` needs `@Import(TenantIdentifierResolver.class)` — `@DataJpaTest` scans
  all `@Entity` project-wide and a `@TenantId` entity elsewhere fails the Hibernate multi-tenant
  filter otherwise (matches `RestaurantRepositoryInsertWithIdTest`). Plan updated.
- `recordHeartbeat` needed `@Modifying(clearAutomatically = true)` so a `HubActivation` already
  managed in the same persistence context is not left stale after the bulk update.
- Kept `getAll(Pageable)` as a delegating 1-arg overload (→ `false`) so nothing broke between the
  service task and the controller task.
- `GlobalExceptionHandler` already maps `IllegalStateException` **and** `IllegalArgumentException`
  to 409 — no handler change needed (spec §7 item resolved).

## Verification
- `cd backend && ./mvnw test` — full suite green (1085 after Task 6; Tasks 7–9 are
  frontend-only).
- `cd frontend && pnpm run build` clean, `pnpm run lint` 0 errors (16 pre-existing warnings),
  `pnpm run test:run` **83/83** (26 files, +5 new console tests).

## Deployment note (carry to prod)
`V8` is skipped in any environment whose `flyway_schema_history` is a single BASELINE row at
`version 15` (local dev, likely prod). Before deploying there, run:
```sql
ALTER TABLE restaurants     ADD COLUMN deleted_at timestamptz, ADD COLUMN deleted_by uuid;
ALTER TABLE hub_activations ADD COLUMN last_heartbeat_at timestamptz, ADD COLUMN last_heartbeat_ip varchar(45);
```
or `flyway repair` + a history insert. `V8` is correct as-is for a genuinely fresh DB. No data
backfill — existing `hub_activations` rows read as `NEVER` until their Hub's next beat.
