# Report 605 — CUSTOMER-REWARDS-DEDICATED-VIEW

## 1. Identification
- Report: 605
- Task ID: CUSTOMER-REWARDS-DEDICATED-VIEW
- Predecessor: CUSTOMER-MENU-MOBILE-REWARDS (report 604)

## 2. Objective
Fix a live bug found right after report 604: "Ver recompensas" in the mobile 3-dot menu expanded the reward cards inline inside the small popover instead of opening its own view (unlike "Ver comanda"/"Ver cuenta"), and the cards overflowed past the edge of the mobile viewport.

## 3. Modified Files
- `frontend/src/pages/customer/RewardsView.tsx` (new)
- `frontend/src/pages/customer/components/LoyaltySection.tsx`
- `frontend/src/pages/customer/components/MobileActionsIsland.tsx`
- `frontend/src/App.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- New `menu/:id/rewards` route + `RewardsView.tsx` page, following the exact header/back-button/content pattern already used by `ComandaView.tsx`/`Bill.tsx` (back link to `/customer/menu`, `max-w-2xl mx-auto` content column, no fixed-width popover to overflow).
- `LoyaltySection.tsx`: extracted the single reward card markup into `RewardCard` (shared by both the desktop-inline `RewardsList`, horizontal scroll, and the new full page, which lays rewards out in a `grid grid-cols-1 sm:grid-cols-2` — no `w-64`/`shrink-0`/overflow-x hacks needed there since it has the full page width).
- `MobileActionsIsland.tsx`: "Ver recompensas" now does `close(); navigate(`${tableId}/rewards`)`, mirroring "Ver comanda"/"Ver cuenta" exactly. Removed the `showRewards` inline-expand state and the popover's conditional `w-auto` width hack from report 604 — the popover is back to its original fixed `w-56`.
- Added `loyaltyNoRewards` i18n key for the page's empty state (ES/EN).

## 5. Why It Changed?
User feedback after 604: clicking the option should open "its own view, like the comanda option" — the 3-dot menu is meant as a navigation launcher for full sub-views, not a container for arbitrarily-sized content. Inlining the reward grid inside a corner-anchored popover had no width constraint against the viewport, so it visibly broke out of the screen on a phone. A dedicated route sidesteps the layout problem entirely and matches the existing UX pattern instead of inventing a new one.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build); `RewardsView` code-splits into its own lazy chunk.
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
