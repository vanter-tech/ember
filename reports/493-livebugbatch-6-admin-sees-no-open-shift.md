# Report 493

## 1. Identification
- **Report number:** 493
- **Task ID:** LIVE-BUG-BATCH 6/7 — ADMIN sees "no open shift" in `/waiter/tables`
- **Predecessor task:** report 492 (bug 5 — cash-shift payments show the table, not the person)

## 2. Objective
Live user bug report: an ADMIN visiting `/waiter/tables` sees the floor as if no cash shift is
open, even when one genuinely is.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`

## 4. What Changed?
Confirmed root cause via code inspection matching the branch's own history: `GET
/cash-shifts/current` was `ACCOUNTANT,WAITER`-only. Prior to the r473 `ACCOUNTANT` role migration
this endpoint was `WAITER,ADMIN`; r473 narrowed all cash-shift endpoints to `ACCOUNTANT`-only; r487
(report 487, a standalone hotfix) restored `WAITER` access since `Tables.tsx` polls this endpoint to
gate table assignment — but never restored `ADMIN`, which had the same access before r473.
`Tables.tsx` has no other role-based gating; it purely renders based on this query's result, so the
403 alone explains an ADMIN seeing every table blurred as if the register were closed. Added
`'ADMIN'` to the endpoint's `@PreAuthorize`, matching `history`/`detail` (already `ACCOUNTANT,ADMIN`)
and restoring `Tables.tsx`'s original access.

Renamed the existing `current_forbiddenForAdmin` test to `current_allowedForAdmin`, asserting `200`
instead of `403` — this test itself was exercising the exact regression.

## 5. Why It Changed?
This is a read-only status endpoint (write actions on cash shifts remain `ACCOUNTANT`-only,
unaffected) — an ADMIN visiting the waiter floor view should see accurate register status just like
a WAITER does, not a false "closed" state caused by an access-control gap left over from an earlier
role migration.

## Verification
- `cd backend && ./mvnw -Dtest=CashShiftControllerTest test` → 16/16 pass.
- `cd backend && ./mvnw test` → **1302/1302**, BUILD SUCCESS.
- No frontend changes — `Tables.tsx` already worked correctly for any role once the API call
  succeeds; not re-verified separately.
