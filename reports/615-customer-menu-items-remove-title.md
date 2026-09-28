# Report 615 — CUSTOMER-MENU-ITEMS-REMOVE-TITLE

## 1. Identification
- Report: 615
- Task ID: CUSTOMER-MENU-ITEMS-REMOVE-TITLE
- Predecessor: APP-WIDE-ROUTE-TRANSITIONS (report 614)

## 2. Objective
Remove the leftover "Carta Digital" title + description from `/customer/menu`'s `items` step, leaving just the dish grid (plus the desktop table-code badge/"Ver cuenta" and the loyalty section).

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- `items` step: dropped the `menuTitle`/`menuSubtitle` heading block entirely. The desktop-only table-code badge + "Ver cuenta" button (previously laid out beside that heading with `justify-between`) is now its own `hidden sm:flex justify-end` row so it still lands on the right on desktop with nothing left of it; on mobile that row is hidden as before, so nothing renders in its place.
- Removed the now-unused `menuTitle`/`menuSubtitle` i18n keys (ES/EN) — the categories step already uses its own `categoriesListTitle` since report 611, so nothing else referenced them.

## 5. Why It Changed?
Direct request — the categories step already carries its own "Categorías" title, so the item grid didn't need a second, generic "Carta Digital" title repeating the same information.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
