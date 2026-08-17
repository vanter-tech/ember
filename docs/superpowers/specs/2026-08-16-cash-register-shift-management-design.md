# Cash Register & Daily Shift Management — Design Spec

**Date:** 2026-08-16
**Backlog prefix:** `EMB-CR`
**Status:** Approved, pending implementation plan

## 1. Purpose

Add a cash-register / shift-management module covering:
- **Apertura de Caja** — opening a shift with a starting cash float.
- **Movimientos Manuales** — manual cash in/out entries during a shift (float top-ups, drops to safe, petty cash).
- **Arqueo de Turnos** — blind cash-count reconciliation at shift close.
- **Corte Diario (Z-Report)** — a daily rollup of all shifts closed that business day, for Admin oversight.

This is a net-new subsystem: no `CashShift`-equivalent entity, no staff-attribution field on any money record, and no cash-till concept exists anywhere in the codebase today.

## 2. Scope decisions (confirmed with user)

1. **Single shared till per tenant.** At most one `CashShift` may be `OPEN` per tenant at a time. Any WAITER can open it; any WAITER can close it. No per-waiter concurrent tills.
2. **Cash payments require an open shift.** `PaymentService.registerPhysicalPayment` rejects (409) if no `CashShift` is `OPEN` for the tenant. DIGITAL payments are unaffected.
3. **Blind close, immediate reveal.** At close (arqueo), the waiter submits a counted-cash total without the expected system total being shown anywhere in the UI or request payload. The server computes `expected/counted/variance` and returns it in the close response, so the waiter sees the result immediately after submitting (not before).
4. **Role split.** WAITER opens/closes shifts and records movements. ADMIN gets a read-only oversight view over shift history plus the daily Z-report. ADMIN cannot open/close shifts or record movements.
5. **No separate `ZReport` entity.** A `CLOSED` shift's financial fields are computed once at close time and never rewritten — that row is the immutable record. "Corte Diario" is a derived read-only aggregation query over shifts `CLOSED` within a date range, not a persisted document. (If fiscally-sequential, non-reprintable Z-numbers are needed later, that's a targeted follow-up, not part of this scope.)

## 3. Backend design

### 3.1 Lifecycle

`CashShift.status`: `OPEN → CLOSED` (two states, mirrors `Bill`'s `OPEN → PAID` convention — no intermediate "reconciling" state; close is a single atomic request/response).

Concurrency: `CashShift` is Postgres/JPA and tenant-scoped, so it follows `Bill`'s **pessimistic locking** convention (`@Lock(PESSIMISTIC_WRITE)` on the shift row), not the Mongo-only `@Version` optimistic pattern. Open, close, append-movement, and attach-physical-payment are all treated as critical sections that lock the shift row.

### 3.2 Entities & migration (`V7__cash_shifts.sql`)

**`cash_shifts`**
| column | type | notes |
|---|---|---|
| `id` | `bigserial PK` | |
| `tenant_id` | `uuid` | `@TenantId`, auto-filtered like `bills`/`payments` |
| `shift_number` | `int` | per-tenant sequential, computed `max+1` inside the same locked open-transaction; human-friendly reference only, not a fiscal document number |
| `status` | `varchar(20)` | `OPEN \| CLOSED` |
| `opening_float` | `numeric(10,2)` | |
| `opened_by` | FK → `users(id)` | |
| `opened_at` | `timestamp` | |
| `closed_by` | FK → `users(id)`, nullable | |
| `closed_at` | `timestamp`, nullable | |
| `expected_cash` | `numeric(10,2)`, nullable | populated once, at close |
| `counted_cash` | `numeric(10,2)`, nullable | populated once, at close |
| `variance` | `numeric(10,2)`, nullable | `counted - expected`, populated once, at close |
| `total_cash_sales` | `numeric(10,2)`, nullable | populated once, at close |
| `total_digital_sales` | `numeric(10,2)`, nullable | populated once, at close |
| `total_cash_in` | `numeric(10,2)`, nullable | populated once, at close |
| `total_cash_out` | `numeric(10,2)`, nullable | populated once, at close |

Partial unique index: `CREATE UNIQUE INDEX uk_cash_shifts_tenant_open ON cash_shifts (tenant_id) WHERE status = 'OPEN';` — enforces the single-shared-till rule at the DB level, not just in application logic.

**`cash_movements`**
| column | type | notes |
|---|---|---|
| `id` | `bigserial PK` | |
| `tenant_id` | `uuid` | |
| `cash_shift_id` | FK → `cash_shifts(id)` | |
| `type` | `varchar(10)` | `CASH_IN \| CASH_OUT` |
| `amount` | `numeric(10,2)` | |
| `reason` | `varchar(255)` | required free text; no fixed taxonomy |
| `created_by` | FK → `users(id)` | |
| `created_at` | `timestamp` | |

**`payments` (extend existing table)** — closes the staff-attribution gap identified in the current codebase (no money record anywhere records who processed it):
- `cash_shift_id` — FK → `cash_shifts(id)`, nullable (set only for `PHYSICAL` payments)
- `processed_by` — FK → `users(id)`, nullable (set for both `PHYSICAL` and `DIGITAL`, resolved from the authenticated principal)

**Pre-migration verification step:** confirm the actual Postgres column type of `users.id` (entity field is Java `String` via `GenerationType.UUID` — could be mapped `uuid` or `varchar(36)`) before writing FK column types, so the new FKs match exactly.

`expected_cash` at close = `opening_float + Σcash_in − Σcash_out + Σ(confirmed PHYSICAL payments where cash_shift_id = this shift)`.

### 3.3 API surface

Existing controllers map under the global `/v1` context-path with **no `/api` prefix** (`@RequestMapping("/billing")` → `/v1/billing/...`). To stay consistent with every other module, this spec uses `@RequestMapping("/cash-shifts")` (→ `/v1/cash-shifts/...`) rather than introducing the only `/api`-prefixed module in the codebase.

| Method & path | Role | Purpose |
|---|---|---|
| `POST /cash-shifts/open` | WAITER | body `{openingFloat}` → creates `OPEN` shift; 409 if one is already open |
| `GET /cash-shifts/current` | WAITER, ADMIN | the tenant's open shift, or 404 — gates the cash-payment UI |
| `GET /cash-shifts` | WAITER (recent), ADMIN (full, filterable by date range) | history list |
| `GET /cash-shifts/{id}` | WAITER, ADMIN | detail incl. movements |
| `POST /cash-shifts/{id}/movements` | WAITER | body `{type, amount, reason}`; 409 if shift not `OPEN` |
| `POST /cash-shifts/{id}/close` | WAITER | body `{countedCash}` (blind — no expected total in the request); response includes `{expected, counted, variance}` |
| `GET /cash-shifts/{id}/report` | WAITER, ADMIN | replay a closed shift's breakdown |
| `GET /cash-shifts/daily-report?date=` | ADMIN | Corte Diario — rolls up all shifts `CLOSED` on that business day |

`PaymentService.registerPhysicalPayment` gains one new precondition: look up the tenant's current `OPEN` shift (locked); reject with 409 if none; else stamp `cashShiftId` + `processedBy` on the `Payment`.

### 3.4 Events & real-time

New domain events — `CashShiftOpened`, `CashShiftClosed`, `CashMovementRecorded` — published via `ApplicationEventPublisher`/`@EventListener`, per the existing Kafka-free convention (CLAUDE.md §1). A listener broadcasts to STOMP topic `/topic/cash-register/{tenantId}`, mirroring the existing `SessionWebSocketListener`/`KitchenWebSocketListener`/waiter-occupancy broadcast pattern.

## 4. Frontend design

### 4.1 Navigation

`FloatingNav.tsx` gets two new gated `Link`s (not OR'd — Admin oversees, does not operate):
- `role === 'WAITER'` → `/waiter/cash-register` (operate)
- `role === 'ADMIN'` → `/admin/cash-register` (oversee + Z-report)

Both use the same icon (e.g. `Banknote` from `lucide-react`) since a given user only ever sees one of the two links.

### 4.2 Waiter page — `pages/waiter/cashRegister/CashRegister.tsx`

- Current-shift status card (open/closed, opening float, elapsed time, running totals by method).
- Movements table + "add movement" dialog (`uiStore` `ModalType` addition, e.g. `'CASH_MOVEMENT'`), react-hook-form + zod, matching the `NewCategoryModal.tsx` template (Dialog → `useMutation` → invalidate → toast → reset → close).
- "Open Shift" action (dialog: opening float input) shown when no shift is open.
- "Close Shift (Arqueo)" — **two-step UI over one API call**: step 1 asks only for counted cash (expected total not rendered anywhere in the DOM, not just visually hidden); step 2 shows the server's `{expected, counted, variance}` response immediately.

### 4.3 Admin page — `pages/admin/cashRegister/CashRegister.tsx`

Same submodule convention as `analytics/`/`staff/` (`<Module>.tsx` + `components/`). Tabbed:
- **Shift history** — list/detail, read-only, date-range filterable (ADMIN only; ties into the new `GET /cash-shifts` filter params).
- **Daily Z-Report** — reuses `SummaryCards`' KPI-card visual pattern (KPIs: total cash sales, total digital sales, total variance, total movements in/out), sourced from `GET /cash-shifts/daily-report`.

### 4.4 State & data layer

- New `cashShiftService` object in `lib/api.ts`, following the existing service-object convention (plain async functions over the shared `api` axios instance).
- TanStack Query keys: `['cashShiftCurrent']`, `['cashShiftMovements', id]`, `['cashShiftHistory', filters]`, `['cashShiftDailyReport', date]`.
- `websocket.ts` gains `subscribeToCashRegister(tenantId)`, mirroring `subscribeToWaiter` — invalidates the above keys on any `/topic/cash-register/{tenantId}` message.
- Types consumed as `components['schemas'][...]` aliases from `backend-types.ts` once the backend schema exists and is regenerated; if frontend work proceeds before a live backend is available, hand-write matching local interfaces as a temporary stand-in (documented follow-up: regenerate for real once the backend is running, same caveat already tracked in `PROGRESS.md` for prior modules).

### 4.5 New shared utilities / UI gaps

- **`formatCurrency()`** — no shared currency-formatting utility exists anywhere in the frontend today (every call site does ad hoc `$${value.toFixed(2)}`). Add one (`lib/format.ts`, `Intl.NumberFormat`-based) and use it in both new pages. Do not retrofit existing call sites as part of this module — that's an unrelated cleanup.
- shadcn components to add via the CLI: `Tabs` (admin page), `Select` (movement type), `AlertDialog` (confirm close). None of these exist in `components/ui/` today.

## 5. Explicit non-goals

- No per-waiter concurrent tills.
- No fiscally-sequential/legally-numbered Z-report documents.
- No retrofit of existing `.toFixed(2)` currency call sites outside the new module.
- No admin-side shift open/close/movement capability.
