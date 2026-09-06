# Platform console — retire suspended tenants + Hub liveness

**Date:** 2026-09-06
**Status:** design approved, pending implementation plan
**Scope:** pieces **B** and **C** of the platform-console improvement effort. Piece **A**
(`updateRole` tenant-scope) shipped separately (report 383 / PR #80). Piece **D** (visual
redesign of `/console` to match the tenant SaaS app) is a later, separate spec.

---

## 1. Problem

The `/platform` operator console can suspend and reactivate a tenant but cannot **retire** one:
a restaurant that churned stays in the directory forever, still counts in the list, and its
issued `license.key` keeps validating. Operators also have **no way to tell whether a customer's
Hub is actually running** — the cloud answers each license heartbeat but persists nothing, so
"is their Hub alive?" is unanswerable from the console.

## 2. Goals

- **B — Delete:** an operator can soft-delete a restaurant, but **only when it is `SUSPENDED`**.
  The delete is reversible (restore → `SUSPENDED`). No tenant data is physically removed.
- **C — Liveness:** the cloud records each Hub heartbeat's timestamp and source IP, and the
  console shows a per-restaurant Hub status (`ONLINE` / `STALE` / `OFFLINE` / `NEVER`) on both
  the restaurant list and the restaurant detail page.

## 3. Non-goals

- Visual redesign of the console (piece D).
- Heartbeat **history** — charts, a sparkline, a per-beat log. Only "last seen" is stored.
- Hub **app-version** telemetry — deferred; needs a `version` field added to the heartbeat
  request payload, which is a Hub-side change too.
- Automatic purge / hard-delete of long-retired tenants.
- Any change to how a Hub itself behaves on the customer LAN.

---

## 4. Design

### 4.1 Data model — one migration, `V8__restaurant_soft_delete_and_hub_heartbeat.sql`

**`restaurants`**

| Column | Type | Notes |
|---|---|---|
| `deleted_at` | `timestamptz NULL` | when it was retired |
| `deleted_by` | `uuid NULL` | platform operator id — a write-time snapshot, no FK (same convention as `platform_audit_log`) |

`RestaurantStatus` enum gains a fourth value: **`DELETED`**. Lifecycle:

```
ACTIVE  <->  SUSPENDED  --delete-->  DELETED
                  ^-------restore---------'
```

`DELETED` is a subtype of "not `ACTIVE`", so every existing gate already covers it:
`SecurityConfig`'s auth filter (`status != ACTIVE` → 403), `SessionService` join guard
(`status != ACTIVE`), `HubHeartbeatService` (`status == ACTIVE ? "OK" : "SUSPENDED"` — a deleted
tenant's Hub therefore keeps receiving `SUSPENDED` and stops operating after its courtesy grace,
exactly as intended). **No new `!= ACTIVE` branches are needed anywhere.**

**`hub_activations`**

| Column | Type | Notes |
|---|---|---|
| `last_heartbeat_at` | `timestamptz NULL` | updated on every verified heartbeat |
| `last_heartbeat_ip` | `varchar(45) NULL` | caller IP (IPv6 max length); best-effort |

**⚠ Flyway baseline caveat** (carried from `V7`): an environment whose `flyway_schema_history`
is a single BASELINE row at `version 15` (the local dev DB, and *likely prod*) will **skip
`V8`**. Before relying on this in such an environment, apply the columns by hand:

```sql
ALTER TABLE restaurants      ADD COLUMN deleted_at timestamptz, ADD COLUMN deleted_by uuid;
ALTER TABLE hub_activations  ADD COLUMN last_heartbeat_at timestamptz, ADD COLUMN last_heartbeat_ip varchar(45);
```

or `flyway repair` + a history insert. `V8` is correct as-is for a genuinely fresh DB.

### 4.2 Delete / restore — `PlatformRestaurantService` + `PlatformRestaurantController`

**`DELETE /platform/restaurants/{id}`** → `delete(UUID id, String operatorEmail)`:

1. Resolve operator (`BadCredentialsException` if unknown — same as siblings).
2. `restaurantRepository.findById(id)` → `ResourceNotFoundException` (404) if absent.
3. If `status != SUSPENDED` → `IllegalStateException` → **409 Conflict**, message
   *"El restaurante debe estar suspendido antes de eliminarlo."*
4. Set `status = DELETED`, `deleted_at = now()`, `deleted_by = operator.id`; save.
5. `PlatformAuditLog` row: `action = "RESTAURANT_DELETED"`, `oldValue = "SUSPENDED"`,
   `newValue = "DELETED"` — same transaction.
6. Respond **204 No Content**.

**`POST /platform/restaurants/{id}/restore`** → `restore(UUID id, String operatorEmail)`:

1–2. As above.
3. If `status != DELETED` → `IllegalStateException` → **409**, *"El restaurante no está
   eliminado."*
4. Set `status = SUSPENDED`, `deleted_at = null`, `deleted_by = null`; save. (Restores to
   `SUSPENDED`, never straight to `ACTIVE` — the operator re-activates deliberately afterward.)
5. Audit `action = "RESTAURANT_RESTORED"`, `oldValue = "DELETED"`, `newValue = "SUSPENDED"`.
6. Respond **200** with `PlatformRestaurantSummaryResponse`.

**`GET /platform/restaurants`** gains `?includeDeleted` (boolean, default `false`):

- `false` → `restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable)`
- `true` → existing `findAll(pageable)`

**`GET /platform/restaurants/{id}`** — unchanged; still returns a `DELETED` restaurant so the
detail page can render it and offer restore.

**`PATCH /platform/restaurants/{id}/status`** (existing) — reject `DELETED` as the target
(`IllegalArgumentException` → 400, *"Usa DELETE para eliminar un restaurante."*) and reject any
call when the current status is `DELETED` (409, *"Restaura el restaurante antes de cambiar su
estado."*). Suspended↔active stays exactly as today.

`GlobalExceptionHandler` already maps `IllegalStateException`? — **verify during
implementation.** If it does not currently map to 409, add that mapping (it is the natural code
for "wrong state for this transition") rather than reusing 400.

### 4.3 Heartbeat persistence — `HubHeartbeatService` + `HubHeartbeatController`

- `HubHeartbeatController.heartbeat` captures the caller IP and passes it to the service. IP
  resolution: **first hop of `X-Forwarded-For`** if present (prod path is Cloudflare → Caddy →
  app), else `HttpServletRequest#getRemoteAddr()`. Reuse the trusted-proxy resolution already in
  `AuthRateLimiterFilter` / `RateLimitProperties` if it is cleanly extractable; otherwise a
  small private helper in the controller is acceptable — this value is diagnostic only, never a
  security decision.
- `HubHeartbeatService.heartbeat(request, callerIp)`:
  - After the existing signature + fingerprint + restaurant checks succeed, and before building
    the response:
    `hubActivationRepository.recordHeartbeat(restaurantId, Instant.now(), callerIp)` — a
    `@Modifying @Query("update HubActivation h set h.lastHeartbeatAt = :ts, h.lastHeartbeatIp =
    :ip where h.restaurantId = :rid")` update. A targeted UPDATE, not an entity save, so it
    neither reloads nor version-bumps the row. One extra write per beat (~1 per tenant per 5
    min — negligible).
  - **Best-effort:** wrap the `recordHeartbeat` call so a `DataAccessException` is logged at
    `WARN` and swallowed — a telemetry-write failure must not turn a valid heartbeat into an
    error for the customer's Hub.
  - The method stays non-`@Transactional` (as today); the single UPDATE is its own
    autocommit.

### 4.4 Derived Hub status

`HubStatus` enum (new, in `com.vanter.ember.platform.model.dto` or `licensing.model`):

| Value | Condition (`now - last_heartbeat_at`) |
|---|---|
| `NEVER` | no `hub_activations` row, **or** `last_heartbeat_at IS NULL` |
| `ONLINE` | `< 15 minutes` |
| `STALE` | `15 minutes … 24 hours` |
| `OFFLINE` | `> 24 hours` |

Thresholds as named constants (`Duration ONLINE_WITHIN = ofMinutes(15)`,
`OFFLINE_AFTER = ofHours(24)`); the Hub beats every 5 min, so `ONLINE` tolerates two missed
beats. Computed in `PlatformRestaurantService` from `now()` and the activation row — never
stored.

**`PlatformRestaurantSummaryResponse`** gains: `hubStatus` (`HubStatus`).
**`PlatformRestaurantDetailResponse`** gains: `hubStatus`, `hubActivatedAt` (`Instant`),
`lastHeartbeatAt` (`Instant`), `lastHeartbeatIp` (`String`). All are `null`/`NEVER` for a
restaurant that never activated a Hub.

Because the heartbeat data lives on `HubActivation`, not `Restaurant`, the service methods build
these responses from **both** rows: `getAll` does one extra
`hubActivationRepository.findByRestaurantIdIn(ids)` per page and maps by `restaurantId`;
`getById` does a single `findByRestaurantId(id)`. The DTO `from(...)` factories gain an optional
`HubActivation` parameter (or a companion `withHub(...)`).

### 4.5 Console UI (minimal — piece D redesigns it wholesale)

**`frontend/src/lib/platformApi.ts`**
- `deleteRestaurant(id): Promise<void>` → `DELETE /platform/restaurants/{id}`
- `restoreRestaurant(id): Promise<PlatformRestaurantSummary>` → `POST …/{id}/restore`
- `getAll(page, includeDeleted = false)` → adds the query param
- Types gain `hubStatus` etc.

**`ConsoleRestaurants.tsx`**
- New **"Hub"** column: a colored dot + short label —
  `ONLINE` green ● / `STALE` amber ● / `OFFLINE` grey ● / `NEVER` "—".
- A **"Ver eliminados"** checkbox above the table; when checked, passes `includeDeleted=true`.
  `DELETED` rows render with reduced opacity and a `ELIMINADO` badge (extend
  `statusBadgeClass`).

**`ConsoleRestaurantDetail.tsx`**
- New **"Hub"** panel: activated-at, last heartbeat (relative — "hace 3 min" — plus absolute on
  hover/next line), source IP, and the status badge.
- **"Eliminar restaurante"** button:
  - Rendered only when `status === 'SUSPENDED'`; disabled otherwise with a hint
    ("Suspendé el restaurante primero").
  - Opens a confirm dialog that requires typing the restaurant **slug** to enable the
    destructive button (GitHub-style).
  - On success: invalidate `['platformRestaurant', id]`, `['platformAuditLog', id]`,
    `['platformRestaurants']`.
- When `status === 'DELETED'`: the suspend/activate toggle and "Emitir licencia Hub" button are
  hidden; a **"Restaurar restaurante"** button appears instead. The audit-log panel still shows,
  now including the `RESTAURANT_DELETED` / `RESTAURANT_RESTORED` entries.
- The console has **no i18n layer** today — all strings are inline Spanish. Match that; do not
  introduce i18n here (that is piece D's call).

---

## 5. Testing

**Backend — `PlatformRestaurantServiceTest` (Mockito):**
- `delete` happy path: `SUSPENDED` → sets `DELETED` + `deleted_at`/`deleted_by`, writes
  `RESTAURANT_DELETED` audit row.
- `delete` rejected when `status == ACTIVE` (and when already `DELETED`) → `IllegalStateException`,
  no save, no audit row.
- `restore` happy path: `DELETED` → `SUSPENDED`, nulls the columns, `RESTAURANT_RESTORED` audit.
- `restore` rejected when `status != DELETED`.
- `getAll(includeDeleted=false)` calls `findByStatusNot(DELETED, …)`; `true` calls `findAll`.
- `getAll` / `getById` populate `hubStatus` from the activation row; `NEVER` when there is none.
- `updateStatus` rejects `DELETED` as target and rejects being called on a `DELETED` restaurant.

**Backend — `HubHeartbeatServiceTest`:**
- On a valid heartbeat, `recordHeartbeat(restaurantId, <ts>, <ip>)` is invoked.
- On an invalid license / fingerprint mismatch, it is **not** invoked (the existing throws fire
  first).
- A `DataAccessException` from `recordHeartbeat` is swallowed — the method still returns the
  normal `HubHeartbeatResponse`.

**Backend — `HubStatus` derivation:** a small unit test over the four buckets + null input.

**Backend — `@DataJpaTest`:** `hubActivationRepository.recordHeartbeat` updates the right row and
only that row; `restaurantRepository.findByStatusNot(DELETED, …)` excludes deleted rows.

**Backend — controller slices:**
- `PlatformRestaurantControllerTest`: `DELETE` and `restore` require an authenticated operator;
  204 / 200 / 404 / 409 status codes; `?includeDeleted` is forwarded.
- `HubHeartbeatControllerTest`: `X-Forwarded-For` is parsed to the first hop; absent header
  falls back to remote addr.

**Frontend — Vitest:**
- `ConsoleRestaurantDetail`: delete button hidden unless `SUSPENDED`; confirm dialog gates on
  the slug; `DELETED` state swaps in "Restaurar" and hides the status/license controls.
- `ConsoleRestaurants`: Hub column renders the right dot per `hubStatus`; "Ver eliminados"
  toggles the query param and deleted rows render muted.

**Full suite:** `cd backend && ./mvnw test` and `cd frontend && pnpm run build` + `pnpm run lint`.

---

## 6. Rollout notes

- Ship `V8` and the code together. In a baselined environment, run the manual `ALTER TABLE`
  from §4.1 **before** deploying, or the `ddl-auto=validate` boot will fail.
- No data backfill: existing `hub_activations` rows get `last_heartbeat_at = NULL` and therefore
  render as `NEVER` until their Hub's next beat — correct.
- Existing restaurants keep their current status; nothing becomes `DELETED` automatically.

## 7. Open items for the implementation plan

- Confirm `GlobalExceptionHandler`'s mapping for `IllegalStateException` (add 409 if missing).
- Decide the exact home of the `HubStatus` enum + threshold constants (platform DTO package vs a
  shared licensing package) — both DTOs and the service reference it.
- Confirm whether `AuthRateLimiterFilter`'s proxy-IP logic is cleanly reusable or a local
  helper is simpler.
