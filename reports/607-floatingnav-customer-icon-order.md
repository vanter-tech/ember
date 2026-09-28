# Report 607 — FLOATINGNAV-CUSTOMER-ICON-ORDER

## 1. Identification
- Report: 607
- Task ID: FLOATINGNAV-CUSTOMER-ICON-ORDER
- Predecessor: CUSTOMER-MENU-MOBILE-CODE-AND-BILL-CLEANUP (report 606)

## 2. Objective
Originally requested as "move 'salir de la mesa' out of `FloatingNav` into the circle," but the user reconsidered mid-task: `FloatingNav`'s leave-table button is global (every customer page, every breakpoint), while the 3-dot circle only exists on `/customer/menu` on mobile — moving it there would have removed the ability to leave a table from home/comanda/bill/rewards or from desktop entirely. The confirmed ask instead: keep the button in `FloatingNav`, just reorder it — "salir de la mesa" next to the menu button, "home" next to logout.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
Reordered the `role === 'CUSTOMER'` icon block: Menu link → "salir de la mesa" (`DoorOpen`) button → Home link, so the leave-table action sits immediately after the menu icon, and the home icon now sits immediately before the separator + logout button. No logic change — same `onClick`/confirm-dialog wiring, pure JSX reorder.

## 5. Why It Changed?
Direct user request after weighing the tradeoff of moving the button into the mobile-only circle (which would have silently dropped desktop and non-menu-page access to leaving a table) — they chose a position swap instead, no functionality lost anywhere.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case tracked throughout `PROGRESS.md`, unrelated to this change.
- Not visually verified on a device/browser.
