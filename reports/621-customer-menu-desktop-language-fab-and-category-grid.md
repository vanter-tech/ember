# Report 621 — CUSTOMER-MENU-DESKTOP-LANGUAGE-FAB-AND-CATEGORY-GRID

## 1. Identification
- Report: 621
- Task ID: CUSTOMER-MENU-DESKTOP-LANGUAGE-FAB-AND-CATEGORY-GRID
- Predecessor: COMANDA-SUBTOTAL-CARD-FOOTER (report 620)

## 2. Objective
Desktop `/customer/menu` still used the old `Select`-based language dropdown (report 608 only swapped it for the mobile FAB), and the categories step used the compact row-list on every breakpoint. Desktop should use the same `LanguageFabButton` as mobile, and show categories as the same hero-image grid used for dishes instead of a list.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- Header: dropped the `hidden sm:block`/`Select`-based `LanguageSwitcher` branch entirely; `LanguageFabButton` now renders unconditionally, sized `h-10 w-10 text-xs` on mobile and `sm:h-13 sm:w-13 sm:text-sm` on desktop to match the back button.
- Categories step: the existing row-list is now `md:hidden` (mobile-only, unchanged content); added a `hidden md:grid` variant reusing the exact dish-grid classes/structure (`grid-cols-1 md:grid-cols-4 lg:grid-cols-4`, `rounded-4xl` `Card`, full-bleed image, gradient overlay, hero span on the first two cards) — category name/description overlaid at the bottom instead of price/add button, whole card clickable (`onClick` → `goToItems`).
- `LanguageSwitcher` import removed from this file (component itself untouched, still used by `TopNav`/kitchen `OrdersDisplay`).

## 5. Why It Changed?
Direct request, scoped to desktop: bring the language control to parity with mobile, and reuse the already-designed dish-grid visual language for categories instead of a second, different list style.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`** against the running dev server + real session, desktop width (~1552px): category grid renders with the hero-card style (image, gradient, name/description overlay), clicking a card correctly navigates into its items, and the language FAB opens the ES/English popover in place of the old dropdown.
- Mobile-width re-verification was attempted but `resize_window` didn't take effect in this browser session (stuck reporting desktop viewport dimensions across two separate tabs) — not a code issue. The mobile list markup itself is unchanged from before (only wrapped in `md:hidden`), and the same `hidden md:`/`md:hidden` breakpoint-toggle pattern is already proven elsewhere in this codebase (`LanguageFabButton`/`LanguageSwitcher` from report 608, `ItemsFloatingIsland`/`MobileActionsIsland` from report 616).
