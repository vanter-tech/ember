# Report 259 — Cash Shift Expiry & Forced Daily Close

## 1. Identification
- **Report number:** 259
- **Task ID:** EMBER-FIX (cash shift expiry & forced daily close) — plan `docs/superpowers/plans/2026-08-28-cash-shift-expiry-forced-close.md`, tasks EMBER-FIX-01..12 squashed into this single atomic commit.
- **Predecessor task:** Report 258 (`hub-portable-minio-manual-verification`).

## 2. Objective
Give a `CashShift` a computed expiry anchored to the tenant's configured closing time (`closeTime` + 2h grace; 12h fallback when the day is closed, has no schedule entry, or carries a malformed `closeTime`), warn before and after it lapses, allow unlimited 1-hour prolongs, block **new** physical cash operations once overdue (digital payments and table service are never blocked), and walk the first user of a new business day through closing a stale shift. `overdue` is a derived boolean over an `OPEN` row — no new `CashShiftStatus` value, no scheduler, evaluated lazily on every read and every guarded write.

## 3. Modified Files

### Backend — new
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java`
- `backend/src/main/java/com/vanter/ember/cashregister/exception/CashShiftOverdueException.java`
- `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftProlonged.java`
- `backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql`
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftDeadlineServiceTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/model/CashShiftDeadlineFieldsTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java`
- `backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerOverdueTest.java`

### Backend — modified
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java`
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java`
- `backend/src/main/java/com/vanter/ember/cashregister/listener/CashRegisterWebSocketListener.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java` (positional `CashShiftResponse` fixtures + `settingService` stub)
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` (401 matrix row for `POST /cash-shifts/{id}/prolong`)

### Frontend — new
- `frontend/src/lib/cashShiftAlert.ts`
- `frontend/src/lib/cashShiftAlert.test.ts`
- `frontend/src/components/CashShiftSentinel.tsx`

### Frontend — modified
- `frontend/src/lib/api.ts` (`cashShiftService.prolong`)
- `frontend/src/lib/backend-types.ts` (5 hand-added `CashShiftResponse` fields — live backend OpenAPI is stale, matches platform/analytics precedent)
- `frontend/src/layouts/WaiterLayout.tsx`, `frontend/src/layouts/AdminLayout.tsx` (mount `<CashShiftSentinel/>`)
- `frontend/src/layouts/AdminLayout.test.tsx` (extend the `@/lib/api` mock with `cashShiftService` — regression from mounting the sentinel, caught by this task's full test run)
- `frontend/src/components/FloatingNav.tsx` (non-blocking logout warning when the cached current shift is `overdue`)
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx` (disable "Registrar movimiento" while overdue; `refetchInterval` bump)
- `frontend/src/pages/waiter/TableInformation.tsx` (physical-payment `onError` recognises `code: "CASH_SHIFT_OVERDUE"`)
- `frontend/src/locales/{es,en}/waiter.ts`, `frontend/src/locales/{es,en}/common.ts` (i18n keys, ES + EN)

### Meta
- `reports/259-feat-cash-shift-expiry-forced-close.md` (this file)
- `PROGRESS.md`

## 4. What Changed?

**Deadline math (`CashShiftDeadlineService`).** A pure, dependency-free `@Service`: `computeExpiresAt(openedAt, businessHours)` resolves the schedule entry for `openedAt`'s weekday, takes its `closeTime`, rolls the candidate to the next day when it is not after `openedAt`, and adds the 2h `GRACE`. A closed day, a missing schedule entry, a blank/malformed/`null` `closeTime` all degrade to `openedAt + 12h` (`CLOSED_DAY_FALLBACK`) and never throw — a bad settings value must not block payments. `isOverdue(shift, now)` is true only for an `OPEN` shift past its `effectiveDeadline()`. `prolong(shift, now)` adds 1h (`PROLONG_STEP`) to whichever is later of the current deadline or `now`.

**Schema & entity (`V5__cash_shift_expiry.sql`, `CashShift`).** Four nullable columns — `expires_at`, `prolonged_until`, `prolonged_by`, `prolong_count NOT NULL DEFAULT 0` — plus a backfill giving every shift `OPEN` at deploy time `opened_at + 12h` so the new guards and the sentinel have something to evaluate. `CashShift` gains the matching fields (`prolongCount` with `@Builder.Default`), a derived `effectiveDeadline()` (prolonged-until if present, else expires-at) and `businessDay()` (`openedAt.toLocalDate()`).

**Enforcement — two existing write paths, HTTP 409.** `CashShiftService.recordMovement` and `PaymentService.registerPhysicalPayment` each throw the new `CashShiftOverdueException` after their status/shift lookup and before any billing mutation when `deadlineService.isOverdue(...)` is true. `initiateDigitalPayment` / `confirmDigitalPayment` are untouched (asserted via `verifyNoInteractions(deadlineService)`). `GlobalExceptionHandler` maps the exception to a 409 `ProblemDetail` with a stable `code: "CASH_SHIFT_OVERDUE"` property so the frontend can tell it apart from the "tables still open" 409.

**Prolong.** `CashShiftService.prolongShift(shiftId, userId)` — pessimistic `findByIdForUpdate`, rejects non-`OPEN` with `IllegalStateException`, sets `prolongedUntil`/`prolongedBy`, increments `prolongCount`, publishes `CashShiftProlonged(tenantId, shiftId)`. `CashRegisterWebSocketListener` broadcasts that event to `/topic/cash-register/{tenantId}` mirroring the other three. New endpoint `POST /cash-shifts/{id}/prolong` (`@PreAuthorize("hasAnyRole('WAITER','ADMIN')")`) returns the updated `CashShiftResponse`.

**Response.** `CashShiftResponse` gains five trailing components — `expiresAt`, `effectiveDeadline`, `overdue`, `businessDay`, `prolongCount` — filled in `toResponse`, with `overdue` recomputed via `isOverdue(shift, now)` on every serialization (clock authority stays server-side).

**Frontend sentinel.** `deriveCashShiftAlert(shift, now)` is a pure state machine: `STALE` (businessDay before today's local date) → `OVERDUE` (`shift.overdue === true`) → `PRE_WARNING` (within 30 min of `effectiveDeadline`) → `IDLE`. `<CashShiftSentinel/>`, mounted in the waiter and admin shells, polls the existing `['cashShiftCurrent']` query every 60 s, ticks a local `now` every 30 s, and renders one of three `AlertDialog`s: pre-warning and overdue offer Prolong / Close / Later (Later snoozes for `REMINDER_INTERVAL_MS` = 15 min); the stale modal is non-dismissable, lists the stale shift's movement/payment counts from `cashShiftService.detail`, and only routes to Close. `FloatingNav` logout reads the cached current shift and, when `overdue`, shows a non-blocking confirm dialog before logging out. `CashRegister` disables the movement button while overdue; `TableInformation`'s physical-payment `onError` surfaces a specific message when it sees `code: "CASH_SHIFT_OVERDUE"`.

## 5. Why It Changed?
A till left `OPEN` overnight — staff log out without running the cierre — attributes the next day's payments and cash movements to the wrong business day, skewing `getDailyReport`'s per-day rollup and the blind arqueo. Auto-closing the shift at a fixed time would fabricate an arqueo nobody counted, so instead the system nags before the deadline, blocks *new* physical cash operations after it (digital and table service keep working so the floor is never frozen), lets staff buy time with unlimited 1h prolongs, and walks the first user of the new day through closing the stale shift explicitly. Expiry is derived on read/write rather than scheduled, so there is no background job and an admin editing business hours never leaves a stale precomputed state.

## Verification
- Backend: `cd backend && ./mvnw test` → **868/868 PASS**, BUILD SUCCESS.
- Frontend: `cd frontend && pnpm run build` → PASS (tsc clean + vite build).
- Frontend: `pnpm run test` → **41/41 PASS** (12 files).
- Frontend: `pnpm run lint` → 25 errors / 17 warnings, all pre-existing baseline, **zero new** in any file touched by this feature.
