# Report 359 — Fix stale-shift alert blocking admin + trapping the waiter in a close-shift loop

## 1. Identification
- **Report number:** 359
- **Current Task:** bugfix — stale cash-shift alert (`CashShiftSentinel`) is a hard modal block; shows for admins and deadlocks waiters when open tables prevent the close
- **Predecessor Task:** report 358 (fix cash-shift alert "Cerrar caja" no-op)
- **Branch:** `feat/waiter-quick-login-table-actions`

## 2. Objective
User report: the "una caja no se cerró" notice appears on the **admin** views and blocks all interaction until the shift is adjusted — it should not gate the admin at all. And on the waiter side the user got trapped in a loop: stale-shift alert → "Cerrar caja" → arqueo modal → "Confirmar conteo" → error "hay 2 mesas abiertas" → no way to dismiss the alert to go close those tables. Break the loop and stop blocking the admin.

## 3. Modified Files
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/components/CashShiftSentinel.tsx`
- `frontend/src/pages/waiter/cashRegister/components/CloseShiftDialog.tsx`
- `frontend/src/components/CashShiftSentinel.test.tsx`

## 4. What Changed?
- **`AdminLayout.tsx`** — removed `<CashShiftSentinel />` and its import. The cash shift is a waiter-operated concern (opening/closing the till, arqueo); the admin keeps read/oversight access via `/admin/cash-register`. The app-wide blocking alert no longer mounts on any `/admin/*` route.
- **`CashShiftSentinel.tsx`** — the STALE alert is now dismissible like PRE_WARNING / OVERDUE:
  - `showStale` is gated on `!suppressed`.
  - The STALE `AlertDialog` gained `onOpenChange={(o) => !o && snooze()}` and an `AlertDialogCancel` ("Ahora no", `cashShiftLaterButton`). Dismissing it snoozes for `REMINDER_INTERVAL_MS` (15 min); it re-appears on the next 30 s tick once the snooze lapses.
- **`CloseShiftDialog.tsx`** — on an open-tables close failure (`extractOpenTablesCount` returns a number), in addition to the existing toast the dialog now closes itself (`handleOpenChange(false)`) and `navigate('/waiter/tables')` so the waiter lands on the floor to close the sessions. `handleOpenChange` was moved above the `useMutation` call so `onError` can reference it without a use-before-define. Added `useNavigate` import.
- **`CashShiftSentinel.test.tsx`** — wrapper now provides a `MemoryRouter` (the mounted `CloseShiftDialog` calls `useNavigate`). Added a test: STALE alert → click "Ahora no" → the alert's "Cerrar caja del …" button is gone.

## 5. Why It Changed?
- The STALE `AlertDialog` had no `onOpenChange` and no cancel control — its only action was "Cerrar caja del {date}". Mounted in `AdminLayout` (`:51`) it locked every admin screen whenever the tenant had a prior-day unclosed shift, with the only escape being a waiter action.
- On the waiter side, `CashShiftService.closeShift` (`CashShiftService.java:142`) rejects the close while any session is `OPEN` ("Cannot close cash shift: N table(s) still have an open session"). `CloseShiftDialog.onError` only showed a toast; neither dialog could be dismissed, so the waiter could not reach `/waiter/tables` to close the sessions — a hard deadlock. Making STALE dismissible plus actively routing the waiter to the floor on that specific error breaks the loop while still nagging them back (snooze re-fires) until the shift is actually closed.

## 6. Verification
- `pnpm exec vitest run src/components/CashShiftSentinel.test.tsx src/pages/waiter` — 6 files, 15 tests, all pass.
- `pnpm run build` — PASS, 0 TypeScript errors.
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
