# Report 121 — Task EMB-CR-03: Cash Shift Controller

**Predecessor Task:** EMB-CR-02 cash shift service (report 119)

## Objective

Implement `CashShiftController` and its request DTOs, wired at `/cash-shifts` (no `/api` prefix, matching every other controller's real mapping under the global `/v1` context-path), plus controller tests and `SecurityAuditTest` rows, per Task 3 of `docs/superpowers/plans/2026-08-16-cash-register-shift-management.md`.

## Modified Files

- `backend/src/main/java/com/vanter/ember/cashregister/dto/{OpenShiftRequest,RecordMovementRequest,CloseShiftRequest}.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java` (new)
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` (modified — 7 new rows)

## What Changed

- Three validated request records.
- `CashShiftController`: `POST /open`, `GET /current`, `GET` (history, paginated), `GET /{id}` (detail), `POST /{id}/movements`, `POST /{id}/close`, `GET /daily-report`. Role gates match the design decision exactly: WAITER-only writes (`open`/`movements`/`close`), both roles for reads, ADMIN-only for the daily report.
- `open()` calls `cashShiftService.openShift(TenantContextHolder.requireTenantId(), resolveUserId(authentication), request.openingFloat())` — reflecting Task 2's signature change (tenant passed explicitly, not resolved inside the service).
- 7 new `SecurityAuditTest` rows for the new routes, using the real `/cash-shifts/...` paths (not the pre-existing, inconsistent `/api/...` prefix some older rows use — see this task's plan-text note on that).

## Self-Review Finding (fixed)

The controller test's first run failed `open_returnsCreatedForWaiter` with **409 instead of 201**. Root cause: `@WebMvcTest` + `@WithMockUser` mocks Spring Security's `Authentication` but never runs the real `jwtAuthFilter`, which is the only thing that normally calls `TenantContextHolder.setTenantId(...)`. `open()` calls `TenantContextHolder.requireTenantId()`, which threw `IllegalStateException` → mapped to 409 by `GlobalExceptionHandler`. Checked `AnalyticsControllerTest` for the established fix: it manually calls `TenantContextHolder.setTenantId(TENANT_ID)` inside each test that reaches the call, with an `@AfterEach TenantContextHolder.clear()`. Applied the same pattern here. This wasn't in the plan's test snippet — a gap in the plan text, not a coding mistake, and one worth flagging for the plan itself since any future `@WebMvcTest` for this controller needs the same fix.

## Verification

- `cd backend && ./mvnw test -Dtest=CashShiftControllerTest` — RED (compile failure) → implemented → first GREEN attempt failed with 409 (above) → fixed → GREEN: `Tests run: 5, Failures: 0, Errors: 0`.
- `cd backend && ./mvnw test -Dtest=SecurityAuditTest` — `Tests run: 42, Failures: 0, Errors: 0` (35 pre-existing + 7 new).
- `cd backend && ./mvnw test` (full suite): `Tests run: 622, Failures: 0, Errors: 0` — no regressions.
