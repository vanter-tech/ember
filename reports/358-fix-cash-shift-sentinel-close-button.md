# Report 358 — Fix: cash-shift alert "Cerrar caja" button does nothing

## 1. Identification
- **Report number:** 358
- **Task ID:** bugfix — `CashShiftSentinel` "Cerrar caja" no-op (user-reported)
- **Predecessor Task:** EMB-FEAT-22 (report 357 — Plan C wrap-up)
- **Branch:** `feat/waiter-quick-login-table-actions`

## 2. Objective
When a cash shift is left open past its business day, `CashShiftSentinel` shows a blocking "La caja del {date} nunca se cerró" alert on the next login. Its **Cerrar caja del {date}** button did nothing. Make the button actually open the close-shift flow, from whatever screen the alert appears on.

## 3. Root Cause
`CashShiftSentinel` is mounted app-wide in `WaiterLayout` and `AdminLayout`, so its STALE / OVERDUE / PRE_WARNING alerts can surface on any page. All three alerts' "Cerrar caja" button calls `openModal('CLOSE_SHIFT', { shiftId })`, which only writes Zustand UI state. The component that renders for `activeModal === 'CLOSE_SHIFT'` — `CloseShiftDialog` — was mounted **only inside `pages/waiter/cashRegister/CashRegister.tsx`**. So:
- For an **ADMIN**, `CloseShiftDialog` is never in the tree at all (the admin cash-register page only shows history + Z-report) → the button is always a no-op.
- For a **WAITER**, it renders only on `/waiter/cash-register`; the stale alert fires right after login on `/waiter/tables`, where nothing listens.

The forced-close plan (`docs/superpowers/plans/2026-08-28-cash-shift-expiry-forced-close.md:1276`) assumed the existing `CloseShiftDialog` would handle the modal but never gave it a global mount point.

## 4. Modified Files
- `frontend/src/components/CashShiftSentinel.tsx` — import + render `<CloseShiftDialog />` inside the sentinel's fragment
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx` — remove the now-redundant `<CloseShiftDialog />` + its import (prevents a double mount on `/waiter/cash-register`, where `WaiterLayout` → `CashShiftSentinel` now provides it)
- `frontend/src/components/CashShiftSentinel.test.tsx` — **new** (1 test)

## 5. What Changed?
- **`CashShiftSentinel.tsx`:** `<CloseShiftDialog />` is now rendered by the sentinel itself, so it exists wherever the sentinel does (both `WaiterLayout` and `AdminLayout`). `CloseShiftDialog` reads the same `useUIStore().activeModal`/`modalPayload`, so the existing `closeShift = () => openModal('CLOSE_SHIFT', { shiftId: shift.id })` handler is unchanged — it now has a listener. The dialog invalidates `['cashShiftCurrent']` on success, which flips the sentinel's `shift` to `null` and dismisses the alert, exactly as the plan intended.
- **`CashRegister.tsx`:** dropped its local `<CloseShiftDialog />`; the page's own "Cerrar caja" button (`openModal('CLOSE_SHIFT', …)`) still works because `WaiterLayout` wraps the page and now supplies the single dialog instance. `OpenShiftDialog` / `MovementDialog` / `RefundPaymentModal` are page-scoped triggers and stay put.
- **`CashShiftSentinel.test.tsx`:** mounts `<CashShiftSentinel />` with `cashShiftService.current` mocked to a shift whose `businessDay` is before today (→ STALE alert). Clicking the "Cerrar caja del 2020-01-01" button must surface `CloseShiftDialog`'s title ("Arqueo de turno"). RED confirmed before the fix (button found + clicked, dialog never appeared); GREEN after.

## 6. Why It Changed?
A global alert component whose primary action opens a modal must also host that modal — otherwise the action is a silent no-op on every screen except the one page that happens to mount the modal. Mounting it in the sentinel keeps the whole cash-shift-expiry concern in one place and matches how the layouts already host other cross-cutting singletons (`SetPinPrompt`, `FloatingNav`, the sentinel itself).

## 7. Verification
- `pnpm run test:run CashShiftSentinel CloseShiftDialog` → **1/1** (RED→GREEN on the new test)
- `pnpm run test:run` → **67/67** (22 files; was 66/66 / 21 files)
- `pnpm run build` → PASS, 0 TypeScript errors
- `pnpm run lint` → 0 errors (16 pre-existing warnings, none in touched files)
- Manual (for the maintainer): log in as ADMIN or WAITER the day after leaving a shift open → stale alert shows → click "Cerrar caja del {date}" → the arqueo dialog opens; enter counted cash → shift closes, alert clears.

## 8. Next
None. Independent bugfix on top of the EMB-FEAT branch; folds into the pending `feat/waiter-quick-login-table-actions` → `main` merge.
