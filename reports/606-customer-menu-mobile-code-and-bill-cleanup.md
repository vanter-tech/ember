# Report 606 — CUSTOMER-MENU-MOBILE-CODE-AND-BILL-CLEANUP

## 1. Identification
- Report: 606
- Task ID: CUSTOMER-MENU-MOBILE-CODE-AND-BILL-CLEANUP
- Predecessor: CUSTOMER-REWARDS-DEDICATED-VIEW (report 605)

## 2. Objective
On mobile, remove the "Ver cuenta" button and the table-code badge from `/customer/menu`'s header, since both are redundant with entries already reachable from the 3-dot `MobileActionsIsland` menu; the table code becomes a new option there, shown with the same panel styling as "Ver participantes".

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/pages/customer/components/TableCodePanel.tsx` (new)
- `frontend/src/pages/customer/components/MobileActionsIsland.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- `Menu.tsx`: the header's table-code `Badge` + "Ver cuenta" `Button` wrapper is now `hidden sm:flex` (mobile-only hide, matches the pattern already used for the desktop-only rewards row) — desktop keeps both as before.
- New `TableCodePanel.tsx`, structurally mirroring `ParticipantsList.tsx` (title row with a bottom border, then content) but showing the join code as large centered text instead of a list.
- `MobileActionsIsland.tsx`: new "Ver código de mesa" option (Hash icon, ahead of the rewards option) toggles a `showCode` sub-view rendering `TableCodePanel`, same toggle pattern as `showParticipants`/(the old `showRewards`, now `RewardsView` navigation). `joinCode` pulled from `useSessionStore`.
- Added `mobileActionsViewTableCode` + `tableCodePanelTitle` i18n keys (ES/EN).

## 5. Why It Changed?
Both the bill button and table-code badge were taking up header space on phones while duplicating an action already one tap away in the 3-dot menu ("Ver cuenta" already navigates to `.../bill`). The table code itself had no equivalent entry point once hidden, so it needed a new option — styled like the participants panel per direct instruction, since that's the existing "info panel inside the popover" pattern in this menu.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
