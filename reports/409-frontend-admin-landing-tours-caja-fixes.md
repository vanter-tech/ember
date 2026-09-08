# Report 409 — Frontend: admin landing, request-only tours, cierre-de-caja fixes

## 1. Identification
- **Report number:** 409
- **Current Task ID:** ad-hoc frontend UX batch (no milestone ID; PROGRESS active task was "none")
- **Predecessor Task:** report 408 — landing `CONTACT_TO` → `wrangler.jsonc` vars

## 2. Objective
Fix four frontend behaviours the operator disliked:
1. Logging in as ADMIN dropped straight into the Inventory tab.
2. Section tours auto-started in every screen on a fresh login / cleared storage.
3. Closing the cash shift flashed the arqueo difference for a frame, then it vanished.
4. The "Registrar movimiento" button in the waiter cash register had to go.

## 3. Modified Files
- `frontend/src/App.tsx`
- `frontend/src/components/tours/SectionTour.tsx`
- `frontend/src/components/tours/SectionTour.test.tsx`
- `frontend/src/pages/waiter/components/WaiterTour.test.tsx`
- `frontend/src/components/CashShiftSentinel.tsx`
- `frontend/src/pages/waiter/cashRegister/CashRegister.tsx`
- `frontend/src/pages/waiter/cashRegister/components/MovementDialog.tsx` (deleted)

## 4. What Changed?
- **Admin landing:** the `/admin` index route now `<Navigate to="analytics">` instead of `"inventory"`. Login as ADMIN → `navigateForRole` → `/admin` → analytics (business dashboard).
- **Tours are request-only:** `SectionTour`'s `run` state starts `false` instead of `true`. A tour now renders nothing until the user hits TopNav's "?" button (which sets `requestedTourSection`; an existing effect flips `run` to `true` when that matches). The `hasSeenTour` / `markTourSeen` persistence and the "?" replay path are untouched. Two `SectionTour.test.tsx` cases and one `WaiterTour.test.tsx` case that asserted auto-start now assert no auto-start.
- **Arqueo difference flash:** `CashShiftSentinel` rendered `<CloseShiftDialog />` *after* its `if (!shift) return null` guard. Closing the shift invalidates `['cashShiftCurrent']`; `cashShiftService.current` then resolves `null`, `shift` goes falsy, the sentinel returns `null`, and the dialog — mid-render of its expected/counted/difference panel — unmounts. `<CloseShiftDialog />` is now rendered unconditionally (it already self-gates on `activeModal === 'CLOSE_SHIFT'`); only the alert dialogs stay behind a `{shift && …}` guard.
- **Remove "Registrar movimiento":** dropped the button, its `MovementDialog` mount and import from the waiter cash-register page, and deleted `MovementDialog.tsx` (no other referrers). Manual cash in/out entries are no longer possible from the UI. The `'CASH_MOVEMENT'` modal id in `uiStore` and the now-unused `recordMovementButton` / `cashShiftOverdueMovementBlocked` i18n keys were left in place (harmless, keeps i18n parity).

## 5. Why It Changed?
- Inventory is a maintenance screen, not where an owner starts their day — analytics is.
- The tour swarm on every fresh session (localStorage is per-browser and gets cleared) was noise; keeping tours behind the "?" button preserves the help without ambushing the user.
- The arqueo result (especially the difference) is the whole point of closing the shift — it must stay on screen until the user dismisses it.
- Operator asked for the manual-movement button to be removed for everyone.

## 6. Verification
- `cd frontend && pnpm run build` — clean (`tsc -b` + vite).
- `pnpm run test:run` — 118/118 passing (40 files).
- `pnpm run lint` — 0 errors, 16 pre-existing warnings (baseline unchanged).
