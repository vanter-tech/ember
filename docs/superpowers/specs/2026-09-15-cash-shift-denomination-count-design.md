# Cash Shift Denomination Count (Arqueo por Denominación) — Design

- **Date:** 2026-09-15
- **Branch:** `feat/cash-shift-denomination-count` off `main`
- **Status:** design approved in chat 2026-09-15, pending spec review → `writing-plans`
- **Related:** live user report — the ACCOUNTANT's arqueo (`/accountant/cash-register`, `CloseShiftDialog.tsx`) accepts a single typed-in total instead of counting bills/coins by denomination, which is how a real arqueo de caja is supposed to work.

## 1. Objective

Replace the single free-text `openingFloat`/`countedCash` amount in the accountant's open/close
cash-shift flow with a proper denomination count: the user enters *how many* of each córdoba bill
and coin they have, and the total is computed and submitted automatically — never typed by hand.
This matches how a real arqueo is done (confirmed against several restaurant-accounting sources,
see the design-chat sources) and gives the app an actual audit trail of what was counted, not just
a final number someone could mistype or fabricate.

Scope: both the **opening float** and the **closing count** get the denomination grid (a proper
template counts both, not just the close). The close flow additionally gains a **notes field** for
explaining a variance, which the current flow has no way to record at all.

## 2. Current state (verified)

- **`CloseShiftRequest`/`OpenShiftRequest`** (`backend/.../cashregister/dto/`) are each a single
  `@NotNull @DecimalMin("0.00") BigDecimal` — no denomination data anywhere in the request, the
  entity, or the response.
- **`CashShift`** (`backend/.../cashregister/model/CashShift.java`) stores `openingFloat` and
  `countedCash` as plain `BigDecimal` columns. Its own class javadoc states it is "this module's
  immutable Z-record; there is no separate report table" — ruling out a relational child table for
  the breakdown (see §7.1 for the alternatives considered).
- **The "blind count" property is already correctly implemented** — `CloseShiftDialog.tsx` only
  shows `expectedCash`/`countedCash`/`variance` *after* the close mutation succeeds; the accountant
  never sees the expected total before entering their count. This design does not change that.
- **No notes/observations field exists anywhere in the close flow.** `CashMovement` has a `reason`
  field for manual movements, but shift close has nothing equivalent for explaining a variance.
- **Established JSON-column pattern already exists** for exactly this kind of structured,
  no-history-needed data attached to one entity: `PrintAgent.discoveredPrinters`
  (`@JdbcTypeCode(SqlTypes.JSON)` on a `List<DiscoveredPrinter>` record), same pattern as
  `Session.participants` / `RestaurantSettings.payload`. This design reuses that pattern rather
  than inventing a new one.
- **Denominations confirmed against the Banco Central de Nicaragua's official pages** (see sources
  in the design chat): 7 banknotes (1000, 500, 200, 100, 50, 20, 10 córdobas) + 7 coins (10, 5, 1
  córdoba, and fractional 50, 25, 10, 5 centavos) = **14 denominations total**, per explicit user
  decision (the "practical 10" alternative — dropping the 4 smallest coins — was considered and
  rejected).
- **`OpenShiftDialog.tsx`/`CloseShiftDialog.tsx`** both currently render exactly one
  `<Input type="number">` for the whole amount — the component this design replaces in both.
- **`CashShiftDetailResponse`** (read by `DailyZReportPanel`/admin Corte Z view) does not expose
  any breakdown today — needs to, or the counted data is invisible to anyone auditing later.

## 3. Backend

### 3.1 New shared type

`cashregister/model/DenominationCount.java` (record, mirrors `DiscoveredPrinter`'s shape):

```java
public record DenominationCount(BigDecimal value, int quantity) {}
```

`cashregister/model/NicaraguaDenominations.java` — the fixed list of the 14 legal values
(`List<BigDecimal>`), used only for server-side validation (§3.3), not persisted itself.

### 3.2 `CashShift` — 3 new columns

- `opening_breakdown` (`List<DenominationCount>`, `@JdbcTypeCode(SqlTypes.JSON)`, nullable —
  null for shifts opened before this ships, or if a client omits it)
- `closing_breakdown` (same type, nullable, same reasoning)
- `close_notes` (`String`, nullable — free text, required by the frontend only when `variance != 0`,
  not enforced at the DB/API level as NOT NULL so a zero-variance close never needs one)

New migration `V12__cash_shift_denomination_breakdown.sql`, `ADD COLUMN IF NOT EXISTS` (idempotent,
matching `V9`/`V10`/`V11`'s convention).

### 3.3 Request/response changes

`OpenShiftRequest` gains `List<DenominationCount> breakdown` (nullable — optional, so a hand-typed
request without it still works, e.g. from an API client or a future non-grid entry path).
`CloseShiftRequest` gains the same `breakdown` plus `String notes` (both nullable).

`CashShiftService.openShift`/`closeShift`: when `breakdown` is non-null, validate two things before
persisting —
1. every `value` in the list is one of the 14 legal denominations (`NicaraguaDenominations`) —
   guards against a tampered/buggy client sending a nonsense value;
2. `sum(value * quantity)` equals the submitted `openingFloat`/`countedCash` exactly — guards
   against the grid and the total silently diverging (shouldn't happen if the frontend always
   derives the total from the grid, but the backend re-validates rather than trusting the client).

Either failure → existing validation-error shape (400), no new exception type needed — reuse
whatever `IllegalArgumentException`-to-400 mapping the rest of this controller already relies on.

`CashShiftResponse`/`CashShiftDetailResponse` add `openingBreakdown`, `closingBreakdown`,
`closeNotes` so the admin's Corte Z detail view can render them.

## 4. Frontend

### 4.1 New shared component: `DenominationCounter`

`components/cashRegister/DenominationCounter.tsx` (or alongside the existing dialogs — exact
folder is an implementation-plan judgment call), used by both `OpenShiftDialog` and
`CloseShiftDialog`:

- 14 fixed rows (the constant list mirrors the backend's, kept in sync manually — no shared
  package between frontend/backend today, same as every other enum/constant in this codebase).
- Each row: denomination label (`C$1000`, `C$0.50`, etc.) + a quantity `<Input type="number" min="0"
  step="1">`.
- Running total computed client-side (`Σ value × quantity`), shown read-only, large and prominent
  — this *replaces* today's single editable amount field entirely; the total is never hand-typed.
- Emits `{ breakdown: DenominationCount[], total: number }` to the parent form.

### 4.2 `OpenShiftDialog.tsx`

Replaces its single `openingFloat` number input with `<DenominationCounter>`; submits
`{ openingFloat: total, breakdown }`.

### 4.3 `CloseShiftDialog.tsx`

Replaces its single `countedCash` number input with `<DenominationCounter>`; adds an **optional**
notes `<Textarea>` below it (label makes clear it's for explaining a difference if the accountant
already suspects one). It is never required at submit time: the accountant is doing a blind count
(§2) and cannot know the variance until *after* the close response comes back, so client-side
`required` logic tied to variance is not possible without a second round-trip. Rather than add a
`PATCH /cash-shifts/{id}/notes` endpoint and a second forced UI step just to backfill notes after
the fact (real complexity for a marginal completeness gain — see §7.1's rejected alternative), v1
ships the simpler version: the field is always there, always optional, and an admin reviewing Corte
Z later can follow up out-of-band if a variance has no explanation.

### 4.4 Admin Corte Z (`DailyZReportPanel.tsx` / shift detail)

Renders `openingBreakdown`/`closingBreakdown` (small denomination table, read-only) and
`closeNotes` when present, alongside the existing expected/counted/variance figures.

## 5. Files touched (anticipated)

**Backend**
- `cashregister/model/DenominationCount.java` — new.
- `cashregister/model/NicaraguaDenominations.java` — new.
- `cashregister/model/CashShift.java` — 3 new columns.
- `cashregister/dto/{OpenShiftRequest,CloseShiftRequest,CashShiftResponse,CashShiftDetailResponse}.java` — new fields.
- `cashregister/service/CashShiftService.java` — breakdown validation in `openShift`/`closeShift`.
- `cashregister/controller/CashShiftController.java` — request/response wiring for the new fields.
- `backend/src/main/resources/db/migration/V12__cash_shift_denomination_breakdown.sql` — new.

**Frontend**
- `pages/accountant/cashRegister/components/DenominationCounter.tsx` — new.
- `pages/accountant/cashRegister/components/{OpenShiftDialog,CloseShiftDialog}.tsx` — replace the
  amount input with the counter; `CloseShiftDialog` adds the notes field.
- `pages/admin/cashRegister/components/DailyZReportPanel.tsx` (and/or `ShiftHistoryTable.tsx`'s
  detail view, whichever currently renders per-shift financial detail) — render the breakdown +
  notes.
- `lib/api.ts` — request/response type updates, matching `backend-types.ts` regen.
- `locales/{es,en}/waiter.ts` (and/or `admin.ts`) — new i18n keys (denomination labels, notes field,
  totals).

## 6. Testing

**Backend**
- `CashShiftServiceTest` — breakdown sum matches total (accepted), breakdown sum mismatches total
  (rejected, 400), breakdown contains a non-legal denomination value (rejected, 400), no breakdown
  at all (still works exactly as today, backward compatible).
- `CashShiftControllerTest` — request/response shape for the new fields; role coverage unchanged
  (this design doesn't touch `@PreAuthorize`).
- Full `./mvnw test` green.

**Frontend**
- `DenominationCounter.test.tsx` — total sums correctly across all 14 rows, zero-quantity rows
  don't count, changing a quantity updates the total live.
- `OpenShiftDialog`/`CloseShiftDialog` tests updated for the new submission shape.
- `pnpm run build` + `pnpm run test:run` clean.

## 7. Decisions locked (were the open questions during design)

1. **Denominations** — all 14 from the BCN (7 bills + 7 coins, fractional centavos included), not
   the reduced "practical 10" alternative.
2. **Both open and close get the grid** — a correct arqueo template counts both, not just the
   close; confirmed by the researched sources.
3. **Storage** — JSON columns on `CashShift` (`opening_breakdown`/`closing_breakdown`), matching
   the codebase's existing `@JdbcTypeCode(SqlTypes.JSON)` convention; a relational child table was
   considered and rejected as inconsistent with `CashShift`'s own "no separate report table"
   design principle.
4. **Notes field** — new `close_notes`, nullable at the DB/API level (not force-required
   server-side).
5. **Backend re-validates the breakdown sum** rather than trusting the frontend's computed total,
   even though the frontend is the only real-world caller today.

### 7.1 Alternatives considered for storage (not chosen)

- Relational child table (`cash_shift_denomination_counts`) — more queryable, but goes against
  `CashShift`'s explicit "no separate report table" design note; rejected.
- No persistence, client-side sum only — loses the audit trail that is the entire point of this
  feature; rejected.

### 7.2 Notes field is optional, not enforced (resolved during self-review)

Considered requiring `close_notes` whenever the resulting `variance != 0`, via a
`PATCH /cash-shifts/{id}/notes` follow-up endpoint once the accountant sees the variance (blind
count means it can't be required at submit time — see §4.3). Rejected in favor of the simpler,
always-optional field: a whole extra endpoint and a forced second UI interaction is disproportionate
to the completeness gained, and an admin can always follow up on an unexplained variance from Corte
Z directly.
