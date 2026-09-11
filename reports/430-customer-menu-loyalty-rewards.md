# Report 430

**Task ID:** ad-hoc — Customer Menu: loyalty points + rewards visibility
**Predecessor Task:** report 429 — Landing footer Facebook link

## Objective
Admin-configured loyalty rewards (`LoyaltyRewardsSettings.tsx`, catalog stored via `LoyaltyReward`) were never shown anywhere on the customer side, and points weren't visible until the Bill page (post-order). Add a section to the customer Menu (`/customer/menu`, reached right after joining a table) showing the customer's points and the reward catalog — each independently gated so nothing renders when not applicable.

## Modified Files
- `frontend/src/pages/customer/components/LoyaltySection.tsx` (new)
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## What Changed?
New `LoyaltySection.tsx`, mounted in `Menu.tsx` right below the header (before the category tabs row). It fetches `settings` via `useSettingStore()` and `loyaltyAccountService.me` (`GET /loyalty/accounts/me`, already CUSTOMER-authorized backend-side and already used by `Bill.tsx`) — the account query only runs when `settings.loyalty.enabled` is true, and the whole section renders `null` when disabled or when the account fetch hasn't resolved (`retry: false`, same pattern as `Bill.tsx`). Two independently-gated pieces inside:
- **Points banner** (`Sparkles` card, styled like the existing one in `Bill.tsx`) — only when `totalPoints > 0`.
- **Rewards strip** — only when the account's `rewards` array (server-computed, active rewards only, tier-gated `unlocked` flag) is non-empty. Each card shows name/description, a lock icon + "tier required" label when not yet unlocked by the customer's current tier, or an "Available" badge when it is.

Rewards are catalog-display only in this system (`LoyaltyReward.java`: "no points cost, no redemption flow, no billing integration" — a v1 design decision, not something this task changed), so there's no redeem action, matching what the backend actually supports today.

+3 i18n keys/locale (`loyaltyRewardsTitle`, `loyaltyRewardLocked`, `loyaltyRewardUnlocked`); reused 4 pre-existing but previously-unused keys (`loyaltyPointsLabel`, `loyaltyPointsToNextTier`, `loyaltyMaxTierReached`) for the points banner text. `TIER_LABELS` reused from `@/pages/admin/components/settings/loyalty/types` (cross-boundary admin→customer import already established by `Bill.tsx`).

No backend changes — `/settings` already allows the CUSTOMER role, and `/loyalty/accounts/me` already returns everything needed (points, tier, active reward catalog with per-reward `unlocked`); this was purely a "never wired up on the frontend" gap.

## Why It Changed?
User reported that rewards added in Settings never appeared anywhere for customers, and asked for a Menu-page section showing both the reward catalog and the customer's points — each visible only when the restaurant has loyalty enabled, and the points only when the customer actually has points at that restaurant.

## Verification
`cd frontend && pnpm run build` — clean (tsc -b + vite build, no errors).
`cd frontend && pnpm run test:run` — 121/121 passed (40 test files), no regressions.
Not verified in a running browser (needs Postgres + backend + a seeded loyalty-enabled tenant with an account that has points/rewards) — relying on the existing, already-proven `/loyalty/accounts/me` contract (same call `Bill.tsx` has used in production) plus the build/type-check.
