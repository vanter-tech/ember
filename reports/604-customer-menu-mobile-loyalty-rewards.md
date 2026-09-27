# Report 604 — CUSTOMER-MENU-MOBILE-REWARDS

## 1. Identification
- Report: 604
- Task ID: CUSTOMER-MENU-MOBILE-REWARDS
- Predecessor: STRIP-SHIPPED-HTML-COMMENTS (report 603)

## 2. Objective
On `/customer/menu` mobile view, move the loyalty-program rewards row out of the inline page body and into the existing 3-dot floating menu (`MobileActionsIsland`), alongside "Ver comanda"/"Ver cuenta". The points card stays inline; only the rewards catalog row relocates on mobile.

## 3. Modified Files
- `frontend/src/pages/customer/components/LoyaltySection.tsx`
- `frontend/src/pages/customer/components/useLoyaltyAccount.ts` (new)
- `frontend/src/pages/customer/components/MobileActionsIsland.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- Extracted the loyalty-account query into a standalone hook `useLoyaltyAccount` (own file, to keep `LoyaltySection.tsx` fast-refresh-safe as a components-only module) so it can be reused by `MobileActionsIsland` without duplicating query logic (TanStack Query dedupes by key regardless).
- Extracted the reward-card grid into an exported `RewardsList` component from `LoyaltySection.tsx`; the inline rewards block in `LoyaltySection` is now `hidden sm:flex` (desktop-only), while the points card stays visible at all breakpoints.
- `MobileActionsIsland` gains a "Ver recompensas" option (Gift icon) shown only when loyalty is enabled and the account has at least one reward; selecting it swaps the popover body to `RewardsList` (same sub-panel pattern already used for "Ver participantes"). The popover widens (`w-auto`) while showing rewards, since reward cards (`w-64`) are wider than the default `w-56` menu.
- Added `mobileActionsViewRewards` i18n key (ES: "Ver recompensas", EN: "View rewards").

## 5. Why It Changed?
The rewards catalog row (horizontally-scrolling cards) was cluttering the mobile `/customer/menu` view above the fold. The 3-dot menu already serves as the mobile catch-all for secondary table actions ("Ver comanda", "Ver cuenta", "Ver participantes"), so folding rewards into it keeps the primary menu-browsing view focused on mobile while keeping the feature one tap away, consistent with how "Ver comanda" already works there. Desktop is unaffected — screen space isn't at a premium there, and there's no 3-dot menu in that layout (`MobileActionsIsland` is `sm:hidden`).

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new warnings introduced.
- Not visually verified in a browser/device.
