# Report 616 — CUSTOMER-WELCOME-SCOPE-AND-GLOBAL-FLOATING-ACTIONS

## 1. Identification
- Report: 616
- Task ID: CUSTOMER-WELCOME-SCOPE-AND-GLOBAL-FLOATING-ACTIONS
- Predecessor: CUSTOMER-MENU-ITEMS-REMOVE-TITLE (report 615)

## 2. Objective
Fix two live bugs: (1) a device that already saw the welcome screen for one table skipped straight to "categorías" when joining a *different* table, misread as "adding an item sends you to categories"; (2) the mobile 3-dot circle (participants/rewards/table-code/comanda/cuenta) and its desktop equivalents only existed inside `/customer/menu`'s `items` step, instead of everywhere the customer is seated at a table.

## 3. Modified Files
- `frontend/src/store/sessionStore.tsx`
- `frontend/src/layouts/CustomerLayout.tsx`
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- **Root cause of bug 1:** `hasSeenMenuWelcome` (added report 610) is a `localStorage`-persisted, device-scoped flag. `setSession` (called on every table join) never reset it — only `clearSession` (explicit "salir de la mesa") did. So a device that had ever seen the welcome screen for *any* table skipped it for *every future table*, landing straight on `categories`. Traced this by ruling out every code path that could otherwise reset `Menu`'s local `step` state (there is none — `step` is private React state, only `goToCategories`/`handleBack` touch it, so the reset had to come from the component remounting with a stale persisted flag, not from a live interaction).
- **Fix:** `sessionStore.setSession` now resets `hasSeenMenuWelcome` to `undefined` whenever the incoming session `id` differs from the currently stored one (a genuinely new/different table), while preserving it when resuming the *same* session (page reload, `navigateForRole`'s resume-on-login path) — so refreshing mid-visit doesn't replay the welcome screen, but a new table always does.
- **Bug 2:** `ParticipantsPopUp`, `ItemsFloatingIsland`, and `MobileActionsIsland` (all already self-gating on `tableId`/`participants`, no dependency on `Menu`'s local state) moved out of `Menu.tsx`'s `items`-step block and into `CustomerLayout.tsx`, alongside `FloatingNav`. They now render (or self-hide) consistently across every customer route — home, menu (any step), comanda, cuenta, recompensas — instead of only inside the item grid.
- `Menu.tsx`: dropped the now-unused imports for those three components.

## 5. Why It Changed?
Both were reported live right after report 615 shipped. Bug 1 needed root-causing since "adding an item" doesn't touch step state at all — the actual trigger was a stale device-level flag surfacing on a *new* table, not a live mutation. Bug 2 was a straightforward relocation once confirmed all three floating components have no dependency on `Menu`'s internals.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case, unrelated.
- Not visually verified on a device/browser; the original "adding an item sends you to categories" report couldn't be reproduced live in this environment (no running backend) — the fix targets the only code path capable of resetting `step`, confirmed by elimination of every other candidate (mutation handlers, WS message handlers, route/query invalidation).
