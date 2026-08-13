# Report 20 — task-2.12: close `DashboardController` cross-tenant IDOR

## 1. Identification
- **Report number:** 20
- **Task ID:** task-2.12
- **Predecessor task:** task-2.11 (report 19)

## 2. Objective
Stop `GET /dashboard/status` from trusting a client-supplied `restaurantId` query parameter; derive
the tenant from the authenticated JWT via `TenantContextHolder` instead.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/controller/DashboardController.java`
- `backend/src/test/java/com/vanter/ember/session/controller/DashboardControllerTest.java` (new)

## 4. What Changed?

### `DashboardController`
`getLiveTableStatus` no longer declares `@RequestParam UUID restaurantId`. It now passes
`TenantContextHolder.requireTenantId()` to `DashboardService.getLiveStatus(...)`. The
`@RequestParam` and `java.util.UUID` imports were dropped and the `@Operation` summary now states the
endpoint is scoped to the authenticated tenant. `DashboardService` itself is unchanged — it keeps
taking the tenant as an argument, so it stays unit-testable without a bound `ThreadLocal`.

### `DashboardControllerTest` (new)
`@WebMvcTest(DashboardController.class)` with `@Import({SecurityConfig.class, CorsConfig.class})`,
mirroring `SessionControllerTest`'s slice setup. Four tests:
- the bound tenant is what reaches the service;
- a spoofed `?restaurantId=<other-tenant>` is ignored — the service is invoked with the bound tenant
  and never with the supplied one (the direct regression test for the IDOR);
- with no tenant bound the request is rejected and the service is never called (409, from the
  existing `GlobalExceptionHandler` mapping for `IllegalStateException`);
- an unauthenticated request is 401.

## 5. Why It Changed?
Any authenticated user could read another restaurant's live floor state — table numbers, occupancy,
active session ids, waiter ids and participant counts — simply by passing a different `restaurantId`
in the query string. Nothing downstream re-checked the caller's tenant:
`DiningTableRepository.findByRestaurantIdAndIsActiveTrueOrderByTableNumberAsc(...)` filters purely on
the value it is handed, and the returned session ids are usable against other session endpoints. The
parameter was authorization state supplied by the party being authorized, which is never sound.

Since task-2.11 the verified `rid` claim is bound per request, so the tenant is available server-side
and the parameter has no remaining purpose. Removing it (rather than validating it against the
context) eliminates the class of bug outright: there is no client input left to compare.

The frontend (`DashboardService.getDashboardData` in `frontend/src/lib/api.ts`) still sends
`restaurantId`; Spring silently ignores unmapped query parameters, so this is inert and the UI is
unaffected. Removing that dead parameter and its `restaurantId` plumbing is explicitly task-4.2's
scope, so it was left untouched here.

## 6. Verification
- `./mvnw test`: **299 tests, 0 failures, 0 errors, 0 skipped** (295 baseline + 4 new).
