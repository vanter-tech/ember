# Report 617 — CUSTOMER-FLOATING-ACTIONS-NAV-FIX-AND-HOME-SCOPE

## 1. Identification
- Report: 617
- Task ID: CUSTOMER-FLOATING-ACTIONS-NAV-FIX-AND-HOME-SCOPE
- Predecessor: CUSTOMER-WELCOME-SCOPE-AND-GLOBAL-FLOATING-ACTIONS (report 616)

## 2. Objective
Fix a 404 introduced by report 616's move of the floating actions into `CustomerLayout`, and scope those actions out of `/customer/home` per user confirmation (they should show on the ordering flow — menu/comanda/cuenta/recompensas — but not on the home landing page, even while still seated).

## 3. Modified Files
- `frontend/src/pages/customer/components/MobileActionsIsland.tsx`
- `frontend/src/pages/customer/components/ItemsFloatingIsland.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`

## 4. What Changed?
- **Root cause of the 404:** `MobileActionsIsland`'s "Ver comanda"/"Ver cuenta"/"Ver recompensas" used relative `navigate(`${tableId}/comanda`)` etc. That worked while the component only rendered inside `/customer/menu` (relative resolution landed on `/customer/menu/{id}/comanda`), but report 616 made it render globally via `CustomerLayout` — from `/customer/home` the same call resolves to `/customer/home/{id}/comanda`, which matches no route.
- Fixed by switching to absolute paths: `/customer/menu/${tableId}/comanda`, `/customer/menu/${tableId}/bill`, `/customer/menu/${tableId}/rewards`.
- `ItemsFloatingIsland` (desktop equivalent) had the identical relative-path bug plus a pre-existing latent one: it read `state.tableId` (the physical table's id) instead of `state.id` (the session id) for that URL segment — harmless before since `ComandaView` ignores the URL param and reads the session from the store, but wrong regardless and now fixed alongside the same-line absolute-path change.
- `CustomerLayout.tsx`: the three floating components (participants popup, cart preview, mobile circle) are now wrapped in `{!isHome && (...)}` (`isHome = useLocation().pathname === '/customer/home'`), so they render on every customer route except `/customer/home`, even while a table session is active.

## 5. Why It Changed?
Both were live regressions/follow-ups from report 616. The 404 was a direct consequence of relocating components that used relative navigation without adjusting it for global mounting. The home-page scoping was confirmed via a direct question — the user wants the ordering quick-actions absent from the landing page specifically, not tied to whether a table session exists.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case, unrelated.
- Not visually verified on a device/browser.
