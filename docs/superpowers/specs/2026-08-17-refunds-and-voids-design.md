# Refunds & Voids — Design Spec

**Date:** 2026-08-17
**Backlog prefix:** `EMB-RV`
**Status:** Approved, pending implementation plan

## 1. Purpose

Today the billing pipeline (`Bill{OPEN,PAID}`, `Payment{PENDING,CONFIRMED}`, `BillSplit.paid` boolean) has no reversal path in either direction:

- A wrongly-calculated `Bill` (bad items, wrong split method) cannot be cancelled once created — `BillingService.calculateBill` rejects a second call for the same session outright (`"Session already billed"`), and `Bill.total` is a frozen number with no line-item breakdown to edit.
- A `CONFIRMED` `Payment` (cash or digital) can never be reversed. There is no `REFUNDED` state anywhere, and `CashShiftService.closeShift` treats every `CONFIRMED` physical payment as ground truth for the shift's `expectedCash` — an uncorrectable bad payment permanently skews that shift's arqueo variance.

This spec adds both: **void** (cancel a `Bill` before any money moved) and **refund** (reverse a `CONFIRMED` `Payment`, full or partial, after money moved).

## 2. Scope decisions (confirmed with user)

1. **Both mechanisms, unified.** Void covers pre-payment correction; refund covers post-payment reversal. One design, two endpoints.
2. **A closed `CashShift` is never touched retroactively.** Its `expectedCash`/`countedCash`/`variance` stay exactly as printed at close, per the existing "immutable Z-record" contract. A physical refund's real cash-out is recorded against the **current open shift** instead, regardless of which shift the original payment belonged to.
3. **WAITER alone authorizes refunds and voids** — same trust level as the rest of billing (`calculateBill`/`splitBill`/`registerPhysicalPayment` are already WAITER-only). No ADMIN-approval step, no new role.
4. **Both full and partial refunds are supported**, chosen per-request depending on context (e.g. comping one item vs. reversing an entire mischarge).

## 3. Backend design

### 3.1 Core principle: append-only, never mutate history

`Payment` and `CashShift` are already treated as immutable facts once written (`CashShift`'s own Javadoc: "written exactly once, at close, and never revisited"). This design extends that convention rather than breaking it:

- A refund is a **new `Refund` row**, not a mutation of `Payment`. `Payment.status` never becomes `REFUNDED` — it stays `CONFIRMED` forever, an honest record of "this much was collected, on this date, by this person." Whether it was later refunded, and how much, is answered by summing its child `Refund` rows.
- A void is a **new terminal `Bill.status` value** (`VOIDED`), not a delete. The voided row stays as history; a fresh `Bill` is created for the same session once the correction is made.
- Every reversal is a first-class row with `who/when/why` (`refundedBy`/`voidedBy`, timestamp, mandatory `reason`) — the row itself is the audit trail. No separate audit module is introduced, mirroring how a `CLOSED` `CashShift` is already its own Z-record.

### 3.2 Entities & migration (`V8__refunds_and_voids.sql`)

**New table `refunds`**

| column | type | notes |
|---|---|---|
| `id` | `bigserial PK` | |
| `tenant_id` | `uuid` | `@TenantId`, auto-filtered like `payments`/`bills` |
| `payment_id` | FK → `payments(id)` | real JPA `@ManyToOne`, same as `Payment.bill` — `Payment` has no LAZY-association hazard, unlike `User` |
| `amount` | `numeric(10,2)` | ≤ payment's remaining refundable balance at write time |
| `reason` | `varchar(255)` | required free text, no fixed taxonomy — same convention as `cash_movements.reason` |
| `refunded_by` | FK → `users(id)`, but Java field is a plain `String` column (not `@ManyToOne`) | same pattern as `processed_by`/`opened_by` — avoids `User.restaurantId`'s LAZY association |
| `created_at` | `timestamp` | |

**`bills` (extend existing table)**

- `status` gains `VOIDED` alongside `OPEN`/`PAID`.
- New nullable columns: `voided_by` (plain `String`, same FK-but-not-JPA-relation pattern), `voided_at`, `void_reason`.
- Replace the existing unique constraint with a partial index so a voided bill frees its session for recalculation:
  `CREATE UNIQUE INDEX uk_bills_tenant_session_active ON bills (tenant_id, session_id) WHERE status != 'VOIDED';` — same technique already used for `uk_cash_shifts_tenant_open`.

**`bill_splits` (extend existing table)**

- Replace the `paid boolean` column with `status varchar(20)` (`UNPAID | PARTIALLY_PAID | PAID`), backfilled from the old boolean (`paid=true → PAID`, `paid=false → UNPAID`) before the old column is dropped.
- Recomputed at refund time from `netPaid = Σ(confirmed payments for this split) − Σ(refunds against those payments)` vs. `split.amount`.

**Existing code this migration requires touching (not just new files):**
- `BillingService.calculateBill`'s "already billed" guard must exclude `VOIDED` bills (`findBySessionIdAndStatusNot(sessionId, VOIDED)` instead of `findBySessionId`).
- `BillingService`/`PaymentService`'s `split.setPaid(true)` call sites move to `split.setStatus(BillSplitStatus.PAID)`.
- `SplitPaidMessage` (`{type, billId, participantName, paid: boolean}`) becomes `{type, billId, participantName, status: string}` — a real breaking change to a WS message shape, so backend and frontend land together.
- Frontend's two `.paid` boolean read sites (`TableInformation.tsx`, `Bill.tsx`) switch to reading `status`.

### 3.3 API surface

| Method & path | Role | Purpose |
|---|---|---|
| `POST /billing/bills/{id}/void` | WAITER | body `{reason}` → 409 if `status != OPEN` or any `CONFIRMED` payment exists against it; sets `VOIDED` |
| `GET /billing/bills/{id}/payments` | WAITER, ADMIN | lists `Payment`s for a bill, each with computed `refundedAmount`/`remaining` — the lookup surface a refund action is launched from |
| `POST /billing/payments/{id}/refund` | WAITER | body `{amount?, reason}` → `amount` omitted means "remaining balance in full"; 409 if `payment.status != CONFIRMED`, if `amount` exceeds remaining balance, or (physical only) if no `CashShift` is currently `OPEN` |
| `GET /billing/payments/{id}/refunds` | WAITER, ADMIN | a payment's refund history (who/when/why/amount) |

`GET /cash-shifts/{id}` (existing, from EMB-CR) gains a `payments: List<PaymentResponse>` field on `CashShiftDetailResponse`, sourced from a new `paymentRepository.findByCashShiftId(shiftId)` — this is what lets staff reach a payment made in an **already-closed** shift (next-day dispute) and refund it, not just a live session's in-memory bill.

### 3.4 Cash-shift interaction

A physical refund does **not** get bespoke variance-adjustment logic. It calls the existing `CashShiftService.recordMovement(currentOpenShiftId, CASH_OUT, amount, reason="Refund of payment #<id>: <reason>")` — the same method manual till pull-outs already use. This single reuse gets us:

- Decision #2 for free: the shift that recorded the *original* payment never has its stored totals touched; `sumConfirmedPhysicalForShift` for that old shift still sums the untouched original `Payment.amount`.
- The *current* shift's `expectedCash` at its own close correctly nets out today's refund via the pre-existing `Σcash_in − Σcash_out` term — no new formula.
- The existing `CashMovementRecorded` event/broadcast to `/topic/cash-register/{tenantId}` fires automatically — no new WebSocket plumbing for the cash-register page.
- If no shift is currently open, the refund is rejected (409) exactly like a physical payment would be — you can't hand back cash from a till that isn't open.

Digital refunds skip this section entirely (stub gateway, no real till) — pure bookkeeping via the `Refund` row.

**Known limitation, explicitly out of scope for v1:** a digital refund does not adjust `CashShift.totalDigitalSales`/`DailyReportResponse` — those stay gross-of-refund, since that figure is informational on the cash-report and isn't part of the physical arqueo. A fully refund-aware Z-report is a targeted follow-up, not part of this scope (same posture the CR spec itself took on fiscally-sequential Z-numbers).

### 3.5 Analytics adjustment (required, not a follow-up)

`AnalyticsService`'s `/admin/analytics/summary` and `/sales` are documented as "revenue = CONFIRMED `Payment` sums" (task-5.13/5.14). Left alone, that figure would silently overstate revenue after any refund. Both underlying `PaymentRepository` projection queries gain a companion refund-subtraction (net = `Σpayments − Σrefunds` in the same window). `/products` and `/tables` are unaffected — they're already documented as deliberately keying off item money and paid-session existence, not `Payment` sums, so they stay as-is.

### 3.6 Events & real-time

New WS message types on `/topic/session/{id}` (mirroring `SplitPaidMessage`/`SessionClosedMessage`): `BillVoidedMessage`, `SplitRefundedMessage`. Harmless no-ops if the session already closed and nobody's subscribed. No `SessionReopened` event exists or is introduced — **a refund never reopens a closed session or flips a table back to occupied**, even if it drops a fully-paid bill back to partially-paid. `Bill.status` stays `PAID` after a later partial refund; it's a phase marker ("this billing cycle finished collecting payment"), not a live balance — the live balance lives in `BillSplit.status` and the `Refund` rows.

## 4. Frontend design

### 4.1 Live-session surfaces (waiter)

- **Void:** a "Anular Cuenta" action near the split list in `TableInformation.tsx` (rendered only while `Bill.status === 'OPEN'` and no split is paid), opens a confirm dialog asking for `reason`, same `useUIStore` modal + `useMutation` + toast pattern as `ChargeTableModal.tsx`.
- **Refund:** a small action next to any `PAID`/`PARTIALLY_PAID` split, opens a `RefundModal`. On open it calls `GET /billing/bills/{id}/payments` (the bill's `id` is already in view) to resolve that split's `Payment.id` — `SplitPaidMessage` never carried a `paymentId`, so this lookup replaces plumbing one through the live WS state.

### 4.2 Historical surface (cash-register oversight)

`CashShiftDetailResponse`'s new `payments` field surfaces in both `pages/waiter/cashRegister/CashRegister.tsx` and `pages/admin/cashRegister/components/ShiftHistoryTable.tsx`'s detail view, each payment row showing its refund status and a refund action. Since `GET /cash-shifts/{id}` is already open to WAITER for any shift regardless of age (only the *list* endpoint is "recent-only" for WAITER), a waiter can reach and refund a payment from an old, closed shift by navigating to it directly — ADMIN's oversight page is where such a dispute is likeliest to be *spotted* (matching its existing read-only role split from EMB-CR), a WAITER is who *executes* the refund, consistent with decision #3.

### 4.3 State & data layer

- `billingService` in `lib/api.ts` gains `voidBill`, `listPayments`, `refundPayment`, `listRefunds`, following the existing plain-async-function convention.
- New TanStack Query keys: `['billPayments', billId]`, `['paymentRefunds', paymentId]`.
- `websocket.ts`'s session handler gains `BILL_VOIDED`/`SPLIT_REFUNDED` cases, invalidating `['bill', sessionId]` the same way `SPLIT_PAID` already does.
- Types consumed as regenerated `components['schemas'][...]` aliases once the backend schema exists, same caveat already tracked for prior modules in `PROGRESS.md`.

## 5. Error handling

All new failure modes route through the existing `GlobalExceptionHandler` conventions — no new exception types:
- `IllegalStateException` → 409: bill not `OPEN` when voiding, payment not `CONFIRMED` when refunding, no open shift for a physical refund.
- `IllegalArgumentException` → 409: refund `amount` exceeds remaining balance, void attempted with a `CONFIRMED` payment already present.
- `ResourceNotFoundException` → 404: unknown `billId`/`paymentId`.

## 6. Testing strategy

- `BillingService`/`PaymentService` unit tests (Mockito, matching existing style): void guard rejects a bill with a confirmed payment; refund guard rejects over-refunding; partial refund correctly lands `BillSplit.status` on `PARTIALLY_PAID`; a second partial refund exhausting the balance lands `UNPAID`.
- `CashShiftService`/integration test: a refund recorded against shift B for a payment originally taken in already-`CLOSED` shift A leaves shift A's stored `expectedCash`/`variance` byte-for-byte unchanged, and correctly reduces shift B's `expectedCash` at its own close.
- `AnalyticsService` test: `/summary` revenue for a window nets a refund issued inside that window; a refund issued outside the window doesn't affect it.
- `AbstractTenantIsolationTest` coverage for the new `refunds` table, matching every other tenant-scoped entity.
- `SecurityAuditTest`'s 401 matrix gains the four new routes.

## 7. Explicit non-goals

- No `PaymentStatus.REFUNDED` value — `Payment` stays an immutable fact; reversal state lives only in `Refund` rows.
- No ADMIN-approval workflow for refunds (ruled out by decision #3).
- No retroactive edits to a closed `CashShift`'s stored totals, ever.
- No refund-aware adjustment to `CashShift.totalDigitalSales`/`DailyReportResponse` (informational only, not part of the physical arqueo) — flagged as a targeted follow-up, not this scope.
- No session/table reopening triggered by any refund.
- No item-level void UI beyond what already exists (`DELETE /{id}/items/{itemId}` already allows removing a non-`PREPARING` item before a bill is calculated).
