# Report 487 — Hotfix: waiter can't see the open cash shift

**Predecessor:** report 486 (frontend apple-touch-icon)

## Objective
Fix a live regression the user found in prod: after an ACCOUNTANT opens the cash shift, `/waiter/tables`
shows the register as closed (blurred, all tables disabled), even though it's genuinely open.

## Modified Files
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`

## What Changed?
`GET /cash-shifts/current` was `@PreAuthorize("hasRole('ACCOUNTANT')")` — now
`@PreAuthorize("hasAnyRole('ACCOUNTANT','WAITER')")`. `current_forbiddenForWaiter` replaced with
`current_allowedForWaiter` (asserts 200, not 403).

## Why It Changed?
Report 473 (new `ACCOUNTANT` role) moved cash-shift `open`/`recordMovement`/`prolong`/`close`
*and* the read-only `current` check to `ACCOUNTANT`-only, but `frontend/src/pages/waiter/Tables.tsx`
still calls `GET /cash-shifts/current` to gate table assignment (`isCajaOpen`). Since WAITER lost
read access, every call 403'd, React Query left `cashShift` undefined, and `isCajaOpen` was always
`false` — waiters could never assign a table regardless of the register's real state. This was an
oversight in r473's role migration, not an intended security tightening: WAITER already had this
exact read access before r473 ("were WAITER, or WAITER+ADMIN"), only the *write* actions
(open/close/movements) were meant to move to ACCOUNTANT. `history`/`detail` stay
`ACCOUNTANT,ADMIN`-only, unchanged — the waiter only ever needed the live open/closed check, not
shift history.

## Verification
`./mvnw -Dtest=CashShiftControllerTest test` — 16/16. Full `./mvnw test` — **1300/1300**, `BUILD SUCCESS`.

## Release
Shipped as hotfix `v0.2.5.1` off `main` (no other unreleased changes bundled), to get the fix to
prod as fast as possible without dragging in unrelated work.
