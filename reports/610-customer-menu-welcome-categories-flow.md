# Report 610 — CUSTOMER-MENU-WELCOME-CATEGORIES-FLOW

## 1. Identification
- Report: 610
- Task ID: CUSTOMER-MENU-WELCOME-CATEGORIES-FLOW
- Predecessor: CUSTOMER-MENU-LANGUAGE-FAB-SIZE-MATCH (report 609)

## 2. Objective
Replace `/customer/menu`'s single screen (horizontal-scroll category chips + item grid, always visible together) with a 3-step flow: a welcome screen with restaurant info + an "Ordena ya" CTA, then a category list (using each category's own image, previously fetched but unused), then the existing item grid for the chosen category.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/store/sessionStore.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- `sessionStore`: new persisted `hasSeenMenuWelcome` flag + `markMenuWelcomeSeen()` action; reset (to `undefined`) in `clearSession()` so leaving a table shows the welcome screen again on the next join.
- `Menu.tsx` is now a 3-step wizard (`step: 'welcome' | 'categories' | 'items'`, local state seeded from `hasSeenMenuWelcome` so repeat visits within the same table session skip straight to `categories`):
  - **welcome**: business name/phone/address (`settings.branding`) + opening-hours lines (reusing the existing `hoursLines()` helper from `receiptBusinessInfo.ts`, the same formatter the printed receipt uses) + "Ordena ya" button (`goToCategories`, also calls `markMenuWelcomeSeen`).
  - **categories**: vertical list of rows (was the horizontal-scroll chip bar), each showing the category's `imgUrl` (fell back to a `UtensilsCrossed` placeholder icon when absent) + name + description; tapping a row sets `activeCategory` and moves to `items`.
  - **items**: unchanged product grid and its `menuTitle`/`menuSubtitle`/table-code badge/"Ver cuenta" header row, minus the chip bar (category is chosen beforehand now). `LoyaltySection`/`MobileActionsIsland`/`ItemsFloatingIsland`/`ParticipantsPopUp` now only mount in this step (previously mounted unconditionally).
  - Header back button (`ArrowLeft`) is now wired (it had no `onClick` before): `items` → `categories` → `welcome` → `/customer/home`.
  - The `isLoading`/`isError` guards for the menu-items query only apply outside the `welcome` step, so the welcome screen renders immediately without waiting on that fetch.
- Added `welcomeOrderNowCta`, `welcomePhoneLabel`, `welcomeCategoriesEmpty` i18n keys (ES/EN).

## 5. Why It Changed?
Direct request, following up on earlier feedback that the horizontal category chips weren't convincing and left the category images unused: a welcome step surfaces basic restaurant info up front, and a dedicated category list finally uses the images the backend already provides per category (`MenuDTO.imgUrl`), replacing the plain-text scroll strip.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case, unrelated.
- Not visually verified on a device/browser.
