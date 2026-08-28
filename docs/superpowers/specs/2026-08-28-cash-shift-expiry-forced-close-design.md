# Cash Shift Expiry & Forced Daily Close — Design

- **Date:** 2026-08-28
- **Status:** Approved for planning
- **Module:** `cashregister` (backend), waiter/admin shell (frontend)
- **Related:** `docs/superpowers/specs/2026-08-16-cash-register-shift-management-design.md`,
  `docs/superpowers/specs/2026-08-17-refunds-and-voids-design.md`

---

## 1. Summary

A `CashShift` currently has only `OPEN → CLOSED` with no time bound. In practice a till
is left open overnight — up to 30+ hours — because staff log out without closing it (a
single POS shows several waiter accounts through a shift, so logging out is routine).
Every payment and movement recorded while that stale shift is `OPEN` is attributed to it,
and `getDailyReport` groups by `closedAt` date, so a shift that spans days silently
contaminates one business day's arqueo and analytics with another day's activity.

This feature gives a shift a computed **expiry** anchored to the tenant's configured
closing time, warns before and after it lapses, lets staff extend it an unlimited number
of times, and — once expired — blocks *new* cash operations (physical payments, manual
movements, opening the next shift) while leaving table service untouched. A dedicated
"morning" modal handles the already-happened case: the first waiter/admin to load the app
against a shift whose business day is in the past is walked through closing it before any
customer is seated.

---

## 2. Goals

- A shift opened during normal trading expires at `configured closeTime + 2h`.
- Staff get a **pre-warning** 30 min before expiry and a **recurring reminder** every
  15 min after it.
- Staff can **prolong** the shift by 1h, unlimited times, recorded for audit.
- Once expired and not prolonged, **`registerPhysicalPayment` and `recordMovement`
  return 409**. Digital (customer-driven) payments and all table/session operations are
  unaffected.
- The first waiter/admin session of a new calendar day, facing a shift whose business day
  is in the past, sees a **blocking modal** that explains the contamination, shows the
  accumulated movements/payments, and drives close → open-today.
- Logout shows a **non-blocking** warning **only while the current shift is `overdue`**.

## 3. Non-goals

- **No per-payment / per-table reconciliation surgery.** We never re-parent a `Payment`
  or `CashMovement` between shifts. A stale shift is closed whole; whatever landed on it
  overnight stays on it.
- **No automatic close.** `closeShift` needs a physical `countedCash` to compute
  `variance`; a machine close would fake the arqueo. Expiry only nags + blocks.
- **No new admin settings.** The only configuration input is the existing
  `businessHours.schedule`. All timing values below are code constants.
- **No new `CashShiftStatus` value.** `overdue` is a derived boolean over an `OPEN` row.
- **No scheduler / background job.** Expiry is evaluated lazily on read and on write.

---

## 4. Constants

Defined once in `CashShiftDeadlineService` (backend). The frontend never re-derives them —
it consumes server-computed timestamps.

| Constant | Value | Meaning |
|---|---|---|
| `GRACE` | `Duration.ofHours(2)` | added to the day's `closeTime` to get `expiresAt` |
| `CLOSED_DAY_FALLBACK` | `Duration.ofHours(12)` | `expiresAt = openedAt + 12h` when the open day is `closed` or has no `closeTime` |
| `PROLONG_STEP` | `Duration.ofHours(1)` | each prolong pushes the deadline by this much |
| `PRE_WARNING` | `Duration.ofMinutes(30)` | frontend shows the pre-warning modal this early |
| `REMINDER_INTERVAL` | `Duration.ofMinutes(15)` | frontend re-shows a dismissed expiry/pre-warning modal after this long |

`PRE_WARNING` and `REMINDER_INTERVAL` are frontend-only constants; `GRACE`,
`CLOSED_DAY_FALLBACK`, `PROLONG_STEP` are backend-only.

---

## 5. Backend design

### 5.1 Entity — `CashShift`

Four new nullable columns (all written only while `OPEN`, frozen at close):

| Column | Type | Notes |
|---|---|---|
| `expires_at` | `TIMESTAMP` | set once at `openShift`; the base deadline before any prolong |
| `prolonged_until` | `TIMESTAMP` null | set/overwritten by each prolong; null until first prolong |
| `prolonged_by` | `VARCHAR` null | user id of the **last** prolong |
| `prolong_count` | `INT NOT NULL DEFAULT 0` | number of prolongs, for audit / admin visibility |

Derived, not persisted (helper methods on the entity or the service):

- `effectiveDeadline()` = `prolongedUntil != null ? prolongedUntil : expiresAt`.
- `isOverdue(now)` = `status == OPEN && now.isAfter(effectiveDeadline())`.
- `businessDay()` = `openedAt.toLocalDate()`.

### 5.2 Migration — `V5__cash_shift_expiry.sql`

```sql
ALTER TABLE cash_shifts
    ADD COLUMN expires_at       TIMESTAMP,
    ADD COLUMN prolonged_until  TIMESTAMP,
    ADD COLUMN prolonged_by     VARCHAR(255),
    ADD COLUMN prolong_count    INTEGER NOT NULL DEFAULT 0;

-- Backfill any shift that is currently OPEN so it has a finite deadline.
UPDATE cash_shifts
   SET expires_at = opened_at + INTERVAL '12 hours'
 WHERE status = 'OPEN' AND expires_at IS NULL;
```

CLOSED rows keep `expires_at IS NULL`; nothing reads it for a CLOSED shift.

### 5.3 `CashShiftDeadlineService` (new, pure)

Single responsibility: turn an `openedAt` + the tenant's `BusinessHoursSettings` into an
`expiresAt`. No repository access, no `LocalDateTime.now()` inside the calculation path
(the caller passes `now` / `openedAt`). Depends on `SettingsService` (or the already-loaded
`SettingsPayload`) to read `businessHours.schedule` and a `Clock` for the enforcement
helpers.

```
LocalDateTime computeExpiresAt(LocalDateTime openedAt, BusinessHoursSettings hours):
    DaySchedule day = schedule entry for openedAt.getDayOfWeek()   // may be absent
    if day == null || day.closed || day.closeTime is blank:
        return openedAt.plus(CLOSED_DAY_FALLBACK)                  // +12h
    LocalTime close = LocalTime.parse(day.closeTime)
    LocalDateTime candidate = openedAt.toLocalDate().atTime(close)
    // opened after today's close, or an overnight close time (e.g. 02:00): roll forward
    if !candidate.isAfter(openedAt):
        candidate = candidate.plusDays(1)
    return candidate.plus(GRACE)                                   // +2h

boolean isOverdue(CashShift shift, Instant/LocalDateTime now):
    return shift.status == OPEN && now.isAfter(shift.effectiveDeadline())

LocalDateTime prolong(CashShift shift, LocalDateTime now):
    LocalDateTime base = max(now, shift.effectiveDeadline())
    return base.plus(PROLONG_STEP)                                 // +1h
```

Malformed `closeTime` strings are treated as "blank" → 12h fallback (don't throw; a bad
settings value must not brick payments).

### 5.4 Enforcement points

| Call site | Change |
|---|---|
| `PaymentService.registerPhysicalPayment` | after `cashShiftRepository.findOpenForUpdate(...)`, if `deadlineService.isOverdue(shift, now)` → throw `CashShiftOverdueException` |
| `CashShiftService.recordMovement` | after the `status == OPEN` check, same `isOverdue` guard → `CashShiftOverdueException` |
| `CashShiftService.openShift` | **unchanged.** It already rejects a second `OPEN`. The stale shift must be closed first; the morning modal drives that. |
| `CashShiftService.closeShift` | **unchanged.** Still blocks on `activeTables > 0` (open sessions). Closing clears `overdue` implicitly (status → CLOSED). |

New exception `CashShiftOverdueException extends RuntimeException`, mapped to **HTTP 409**
in the module's `@RestControllerAdvice` (mirror the existing `IllegalStateException →
409` handling for "shift already open" / "tables still open"). Body carries a stable
`code: "CASH_SHIFT_OVERDUE"` so the frontend can distinguish it from the open-tables 409.

Digital payments (`confirmDigitalPayment` / gateway webhook path) are **not** guarded —
they are customer-initiated and reconciled by time window, not `cashShiftId`.

### 5.5 New endpoint — prolong

```
POST /cash-shifts/{id}/prolong           @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
→ 200 CashShiftResponse
```

`CashShiftService.prolongShift(Long id, String userId)`:

1. `findByIdForUpdate(id)` (pessimistic, same as movement/close).
2. If `status != OPEN` → `IllegalStateException` (409).
3. `shift.setProlongedUntil(deadlineService.prolong(shift, now()))`,
   `shift.setProlongedBy(userId)`, `shift.setProlongCount(shift.getProlongCount() + 1)`.
4. Save, `eventPublisher.publishEvent(new CashShiftProlonged(tenantId, id))`, return
   `toResponse`.

No cap — a prolong always succeeds on an `OPEN` shift. The forcing function for closing
is operational (`openShift` still refuses a second `OPEN`; the morning modal; the logout
warning), not a hard limit.

### 5.6 DTO — `CashShiftResponse`

Append fields (record component order preserved for existing callers appending at end):

| Field | Type | Source |
|---|---|---|
| `expiresAt` | `LocalDateTime` | `shift.expiresAt` |
| `effectiveDeadline` | `LocalDateTime` | `shift.effectiveDeadline()` |
| `overdue` | `boolean` | `deadlineService.isOverdue(shift, now)` at serialization time |
| `businessDay` | `LocalDate` | `shift.openedAt.toLocalDate()` |
| `prolongCount` | `int` | `shift.prolongCount` |

`toResponse` in `CashShiftService` gains the `deadlineService` dependency to fill
`overdue`. For CLOSED shifts `expiresAt`/`effectiveDeadline` may be null and `overdue` is
false — the frontend only acts on the `current` (OPEN) shift.

### 5.7 Event

`CashShiftProlonged(UUID tenantId, Long shiftId)` — new record alongside
`CashShiftOpened/Closed/CashMovementRecorded`. No listener is required now; it exists for
audit symmetry and future analytics. (If the repo has no "unused event" precedent, a
thin listener that logs at INFO is acceptable; do not invent an analytics sink.)

---

## 6. Frontend design

### 6.1 `<CashShiftSentinel/>` — new component

Mounted once in **`WaiterLayout`** and **`AdminLayout`**, as a sibling of `<FloatingNav/>`
(renders nothing structural; only portals modals). Not mounted in Kitchen/Customer.

- Uses the existing `['cashShiftCurrent']` query but with `refetchInterval: 60_000` and
  `refetchOnWindowFocus: true`. (Bump the interval on the shared query key so
  `CashRegister.tsx` benefits too.)
- Derives UI state from the server payload + a 30 s local `tick` (so the modal appears
  without waiting for the next refetch):

```
shift == null                              → idle, render nothing
shift.businessDay < todayLocalDate         → STALE     (blocking)
shift.overdue === true                     → OVERDUE
now >= effectiveDeadline - PRE_WARNING     → PRE_WARNING
otherwise                                  → idle
```

`todayLocalDate` from the browser clock is acceptable here — worst case the STALE modal
appears a few minutes early/late around midnight; enforcement is server-side.

### 6.2 Modal states

**PRE_WARNING** — `AlertDialog`, dismissible.
- Copy: "La caja se cerrará a las {HH:mm}. Recuerda cerrarla o prolongarla."
- Actions: **Prolongar 1 h** (`POST /prolong`) · **Cerrar caja** (opens existing
  `CLOSE_SHIFT` modal via `openModal`) · **Ahora no** (dismiss; `tick` re-opens it after
  `REMINDER_INTERVAL`).

**OVERDUE** — `AlertDialog`, dismissible but re-opens every `REMINDER_INTERVAL`.
- Copy: "La caja venció el {HH:mm}. Los cobros en efectivo y los movimientos están
  bloqueados hasta que la cierres. La atención de mesas sigue disponible."
- Actions: **Prolongar 1 h** · **Cerrar caja**. No "Ahora no" primary; a small dismiss
  ✕ only.
- Side effect: while OVERDUE, `CashRegister.tsx` disables **Registrar movimiento** and the
  close-time payment actions are already server-guarded; the waiter payment UI
  (`RegisterPaymentModal` or equivalent) shows an inline error on the 409
  `CASH_SHIFT_OVERDUE` ("La caja venció — prolóngala o ciérrala para cobrar").

**STALE** — `Dialog` with no close affordance (`onOpenChange` no-op, no ✕), full-screen
overlay, blocks the app for waiter/admin.
- Header: "La caja del {businessDay, formatted} nunca se cerró."
- Body: short explanation that everything recorded since then is being attributed to that
  day; then the movements + payments list from `cashShiftService.detail(shift.id)`
  (reuse the tables already in `CashRegister.tsx`, read-only).
- Primary action: **Cerrar caja del {businessDay}** → opens `CLOSE_SHIFT`. On success the
  sentinel detects `shift == null` and shows a follow-up: **Abrir turno de hoy** →
  `OPEN_SHIFT` modal, prefilled with the just-counted cash as `openingFloat` (pass via the
  modal payload).
- If close returns the open-tables 409: show its message inline ("Cierra o cobra las
  mesas abiertas antes de cerrar la caja") — expected to be rare since this runs before
  service starts.

### 6.3 Logout warning

In `FloatingNav.handleLogout`: read the cached `['cashShiftCurrent']` value. If
`shift?.overdue === true`, open a confirm dialog:

- Copy: "La caja venció y sigue abierta. Debe cerrarse para la jornada de hoy.
  ¿Cerrar sesión de todos modos?"
- Actions: **Cerrar sesión** (proceed: `logout()` + `navigate('/login')`) · **Volver**.

Not blocking; not shown for merely-prolonged-but-not-overdue shifts (decision:
`overdue`-only).

### 6.4 API client — `frontend/src/lib/api.ts`

```
cashShiftService.prolong: (id: number) =>
    api.post<CashShiftResponse>(`/cash-shifts/${id}/prolong`).then(r => r.data)
```

Regenerate `backend-types.ts` (OpenAPI) so `CashShiftResponse` picks up the new fields.

### 6.5 i18n

New keys in `frontend/src/locales/{es,en}/waiter.ts` (sentinel + modals) and
`frontend/src/locales/{es,en}/common.ts` (logout warning). Follow the existing key-naming
style (`cashShift...`). Keep `PROGRESS.md` checkbox discipline for the i18n task.

---

## 7. Testing

### Backend
- **`CashShiftDeadlineServiceTest`** (pure, table-driven):
  - weekday with `closeTime 23:00`, opened 18:00 → candidate `23:00` same day (after
    `openedAt`, no roll), `expiresAt` = `01:00` the following day (`23:00 + 2h`).
  - opened 23:30 when `closeTime` 23:00 → rolls to next day `23:00 + 2h`.
  - overnight `closeTime 02:00`, opened 20:00 → candidate rolls forward, `+2h`.
  - day `closed` → `openedAt + 12h`.
  - missing schedule entry → `openedAt + 12h`.
  - malformed `closeTime` (`"25:99"`, `""`) → `openedAt + 12h`, no throw.
  - `prolong`: from before deadline → `deadline + 1h`; from after deadline → `now + 1h`.
- **`CashShiftServiceTest`**:
  - `openShift` stamps `expiresAt` from the deadline service.
  - `prolongShift` sets `prolongedUntil`, `prolongedBy`, increments `prolongCount`,
    publishes `CashShiftProlonged`; rejects when `status != OPEN`.
  - `recordMovement` throws `CashShiftOverdueException` when `isOverdue`, succeeds
    otherwise.
  - `toResponse` fills `overdue` / `businessDay` / `effectiveDeadline`.
- **`PaymentServiceTest`**:
  - `registerPhysicalPayment` throws `CashShiftOverdueException` (→409) when the open
    shift is overdue.
  - a digital-payment path is unaffected by an overdue shift.
- **Controller/advice test**: `POST /cash-shifts/{id}/prolong` happy path 200;
  `CashShiftOverdueException` maps to 409 with `code: CASH_SHIFT_OVERDUE`.
- Existing `E2EOrderFlowTest` and `CashShiftServiceTest` must stay green (new columns are
  nullable / defaulted; `toResponse` signature change is internal).

### Frontend
- **Sentinel state machine** unit test with a fake clock + fabricated
  `CashShiftResponse`: asserts `idle → PRE_WARNING → OVERDUE` transitions and
  `businessDay < today → STALE`, and that dismiss + `REMINDER_INTERVAL` re-opens.
- `pnpm run build` (tsc) and `pnpm run lint` clean.

---

## 8. Edge cases & decisions

- **Clock authority:** enforcement uses server time; the frontend only *presents* based on
  server-provided `effectiveDeadline`/`overdue`. Client-clock skew can shift when a modal
  appears, never whether a payment is allowed.
- **Timezone/DST:** `LocalDateTime` throughout, tenant-local, consistent with the rest of
  `cashregister`. No cross-zone handling added.
- **Multi-device:** every POS polls the same `current` shift; a prolong from one device
  clears the modal on the others within one 60 s refetch.
- **Prolong right at expiry:** `prolong` bases off `max(now, effectiveDeadline)` so rapid
  double-clicks don't stack pathologically (each adds 1h from now-ish).
- **Shift closed while a modal is open:** next refetch returns `null` (or a new shift) and
  the sentinel drops to idle / STALE-followup.
- **`overdue` recomputed per response:** because it's evaluated at serialization time, a
  client holding a stale `overdue: false` for up to 60 s is fine — the server still 409s
  the write.

## 9. Out of scope / possible follow-ups

- Admin analytics surfacing `prolongCount` / "shifts that expired" as a KPI.
- Emailing / notifying an admin when a shift goes overdue.
- Making `GRACE` / `PROLONG_STEP` tenant-configurable if real usage shows 2h/1h are wrong.
- Auto-splitting overnight table sessions across shifts.

## 10. File-touch list (for the plan)

**Backend**
- `backend/src/main/resources/db/migration/V5__cash_shift_expiry.sql` — new
- `backend/src/main/java/com/vanter/ember/cashregister/model/CashShift.java` — 4 columns + derived helpers
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftDeadlineService.java` — new
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java` — `openShift` stamps `expiresAt`; new `prolongShift`; `recordMovement` guard; `toResponse` fills new fields
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java` — `POST /{id}/prolong`
- `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftResponse.java` — 5 fields
- `backend/src/main/java/com/vanter/ember/cashregister/event/CashShiftProlonged.java` — new
- `backend/src/main/java/com/vanter/ember/cashregister/exception/CashShiftOverdueException.java` — new (+ advice mapping, wherever the module maps its 409s)
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java` — overdue guard in `registerPhysicalPayment`
- tests: `CashShiftDeadlineServiceTest` (new), `CashShiftServiceTest`, `PaymentServiceTest`, controller/advice test

**Frontend**
- `frontend/src/components/CashShiftSentinel.tsx` — new
- `frontend/src/layouts/WaiterLayout.tsx`, `frontend/src/layouts/AdminLayout.tsx` — mount sentinel
- `frontend/src/components/FloatingNav.tsx` — logout warning
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx` — disable movement button when `overdue`; shared query `refetchInterval`
- payment modal (waiter) — inline handling of `CASH_SHIFT_OVERDUE` 409
- `frontend/src/lib/api.ts` — `cashShiftService.prolong`
- `frontend/src/lib/backend-types.ts` — regenerated
- `frontend/src/locales/{es,en}/waiter.ts`, `frontend/src/locales/{es,en}/common.ts` — keys
- test: sentinel state-machine spec
- `PROGRESS.md` — task queue + checkboxes
