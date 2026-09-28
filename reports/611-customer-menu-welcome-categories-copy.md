# Report 611 — CUSTOMER-MENU-WELCOME-CATEGORIES-COPY

## 1. Identification
- Report: 611
- Task ID: CUSTOMER-MENU-WELCOME-CATEGORIES-COPY
- Predecessor: CUSTOMER-MENU-WELCOME-CATEGORIES-FLOW (report 610)

## 2. Objective
Refine the copy/layout of the welcome and category-list steps shipped in report 610: a proper "Bienvenido a" greeting centered on screen, a "Categorías" title on the category list (dropping the leftover "Carta Digital" title/subtitle there), and a clearer CTA label.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- Welcome step heading is now `t('welcomeGreeting', { name: businessName })` → `Bienvenido a "Nombre"` (ES) / `Welcome to "Name"` (EN), instead of the bare business name.
- Welcome step container centers vertically (`min-h-[70vh]` + `justify-center`, replacing the fixed `py-16` top padding), so it sits in the middle of the screen instead of just below the header.
- Categories step: title changed to `t('categoriesListTitle')` ("Categorías"/"Categories"); the `menuTitle`/`menuSubtitle` ("Carta Digital"/"Explora nuestra selección...") pair was dropped from this step (still used, unchanged, in the `items` step).
- CTA renamed: `welcomeOrderNowCta` → `welcomeViewMenuCta`, copy "Ordena ya" → "Ver Carta" (ES) / "Order now" → "View Menu" (EN).

## 5. Why It Changed?
Direct copy/layout feedback right after 610 shipped.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
