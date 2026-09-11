# Report 431

**Task ID:** ad-hoc — `/waiter/cash-register`: hide write actions from ADMIN
**Predecessor Task:** report 430 — Customer Menu loyalty points + rewards visibility

## Objective
Found while manually testing report 430 in the browser (logged in as ADMIN): navigating to `/waiter/cash-register` and clicking "Open register" produced a generic "Could not open the shift" toast with no explanation. Fix the UX gap without touching the (correct) backend authorization.

## Modified Files
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## What Changed?
Investigated first: `/waiter/cash-register` is intentionally reachable by both `WAITER` and `ADMIN` (`App.tsx` route guard `allowedRoles={['WAITER','ADMIN']}`, consistent with `/waiter/tables` and `/kitchen/orders`), and `CashShiftController.java` correctly restricts the actual write actions — `open`, `{id}/movements`, `{id}/close` — and `BillingController.refundPayment` to `@PreAuthorize("hasRole('WAITER')")` only; `current`/history/detail/prolong/daily-report stay `WAITER,ADMIN`. So this was never a security hole — the backend already rejects an admin's write attempt with 403. The gap was purely that `CashRegister.tsx` rendered those action buttons (Open register, Record movement, Close/Arqueo, Refund) identically regardless of role, so an admin could click them and only learn something was wrong from a generic error toast.

`CashRegister.tsx` now reads `isWaiter = useAuthStore(s => s.role) === 'WAITER'`:
- No open shift: the "Open register" button only renders for a waiter; an admin sees an explanatory line instead (`adminCashRegisterReadOnly`).
- With an open shift: "Record movement", "Close (Arqueo)", and each payment row's "Refund" button are `disabled` for a non-waiter, with the same explanatory text as the button's `title` tooltip (same disable+tooltip pattern already used for the `shift.overdue` case).

+1 i18n key/locale (`adminCashRegisterReadOnly`).

## Why It Changed?
User: "por que como admin eres cpz de entrar a una vista que es solo para el mesero... ESO ES UN MAL COMPORTAMIENTO" — flagged after I navigated to the waiter-only cash-register page under an admin session while trying to set up test data for report 430, and hit the 403. After confirming the backend enforcement was already correct and the route sharing was intentional elsewhere in the app, the user chose (via AskUserQuestion) to fix the actual gap now, as its own task: stop showing an admin actions they can never legally perform.

## Verification
`cd frontend && pnpm run build` — clean.
`cd frontend && pnpm run test:run` — 121/121 passed, no regressions (no existing test covers this component).
Verified live in Chrome (claude-in-chrome) logged in as ADMIN: `/waiter/cash-register` with no open shift now shows "No hay un turno de caja abierto." + "Solo un mesero puede abrir, cerrar o modificar la caja. Tu sesión de administrador solo puede consultarla." — no button to click.
