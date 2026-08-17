# Report 135 — bugfix-waiter-tables-require-open-caja

## 1. Identification
- **Report #:** 135
- **Task ID:** bugfix-waiter-tables-require-open-caja
- **Predecessor Task:** bugfix-staff-generated-types (report 134)

## 2. Objective
On `/waiter/tables`, a waiter could open a table (create a session, generate the join QR/code) with no cash-register shift open at all, letting customers join a table the till isn't tracking. Gate table assignment behind an `OPEN` cash shift and show a clear blocking message.

## 3. Modified Files
- `frontend/src/pages/waiter/Tables.tsx`

## 4. What Changed?
- Added a `useQuery(['cashShiftCurrent'], cashShiftService.current)` fetch (same query key already used by `waiter/cashRegister/CashRegister.tsx`, so cache/invalidation stays consistent across pages) and derived `isCajaOpen = cashShift?.status === 'OPEN'`.
- Table grid `Card` clicks now no-op when `!isCajaOpen`; cards render visually disabled (`opacity-50`, `pointer-events-none`, `cursor-not-allowed`).
- Added a centered overlay on the grid, shown only when `!isCajaOpen`, with the exact copy: "Necesita abrir la caja para poder asignar mesa."
- The "Asignar Mesa" button in the table-details panel (the one that actually opens `ParticipantQrModal` → `createSession`) is now `disabled={!isCajaOpen}` as defense in depth, in case a table was selected before the shift was closed elsewhere.

## 5. Why It Changed?
The bug report: as a waiter, tables could be assigned/opened to customers without first opening the "Caja" (cash shift), which is a data-integrity/operations gap — sessions and eventual payments should always trace back to a tracked shift. `cashShiftService.current()` already exists and resolves to `null` (404) or a `CashShiftResponse` with a `status` field, so this was a pure frontend gating fix reusing existing infrastructure — no backend or API changes needed for the requested behavior.

**Known gap (out of scope, flagged during planning):** the backend `SessionService.createSession` has no server-side cash-shift guard either — only `PaymentService.registerPhysicalPayment` enforces `OPEN` shift today. A determined/buggy client could still bypass this UI gate. Left as a follow-up task per user's explicit UI-only request.
