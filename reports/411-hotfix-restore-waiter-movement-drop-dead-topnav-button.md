# Report 411 — Hotfix: restore the waiter "Registrar movimiento" button; drop the dead TopNav "Nuevo registro" button

## 1. Identification
- **Report number:** 411
- **Current Task ID:** hotfix (pushed straight to `main`, no PR — frontend only, Cloudflare Pages auto-deploys)
- **Predecessor Task:** report 410 — `/cash-shifts/current` empty 200

## 2. Objective
Report 409 removed the **wrong** button. The waiter's working "Registrar movimiento"
button on `/waiter/cash-register` was deleted; the button that actually needed to go —
a dead "Nuevo registro" in the admin TopNav that fired nothing — was left in place.

## 3. Modified Files
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- `frontend/src/pages/waiter/cashRegister/components/MovementDialog.tsx` (restored)
- `frontend/src/components/TopNav.tsx`

## 4. What Changed?
- **Reverted the report-409 removal** on the waiter cash-register page: restored the
  `MovementDialog` import, the `recordMovementButton` button (with its `disabled` /
  `title` overdue guard), and the `<MovementDialog />` mount. `CashRegister.tsx` is now
  byte-identical to its pre-409 state; `MovementDialog.tsx` restored from `df3ca4c9`.
- **`TopNav.tsx`:** the context create button (`#topnav-create-button`) fell through to
  `buttonText = t('defaultButtonText')` ("Nuevo registro") with `actionType = null` on
  any admin route without a defined create action — e.g. `/admin/cash-register`. Its
  `onClick` called `openModal(null, …)`, which does nothing. Added `|| !actionType` to
  the render guard (next to the analytics/settings guards) so the button only renders
  when there is a real action wired. Routes that set `actionType` (menu items,
  categories, modifiers, stock, employees) are unaffected.

## 5. Why It Changed?
- Manual cash in/out (paying a supplier in cash, dropping surplus to the safe, petty
  cash) is a real waiter operation — removing it in 409 was a mistake.
- A button that visibly does nothing when clicked is worse than no button.

## 6. Verification
- `cd frontend && pnpm run build` — clean.
- `pnpm run lint` — 0 errors, 16 pre-existing warnings.
- `pnpm run test:run` — 118/118 (no tests reference either button).
