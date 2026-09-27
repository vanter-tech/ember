# Report 608 — CUSTOMER-MENU-MOBILE-HEADER-LANGUAGE-FAB

## 1. Identification
- Report: 608
- Task ID: CUSTOMER-MENU-MOBILE-HEADER-LANGUAGE-FAB
- Predecessor: FLOATINGNAV-CUSTOMER-ICON-ORDER (report 607)

## 2. Objective
On mobile, `/customer/menu`'s top header (back button + "Ember" + language control) needed: a smaller back button, the language control pinned to the far right, and the `Select`-based dropdown replaced with the circular FAB-style picker already used on the login/auth screens. Desktop keeps the previous layout.

## 3. Modified Files
- `frontend/src/components/LanguageFabButton.tsx` (new)
- `frontend/src/pages/auth/LanguageFab.tsx`
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- Extracted the circular locale-code button + options popover out of `pages/auth/LanguageFab.tsx` into a shared, position-agnostic `components/LanguageFabButton.tsx` (takes an optional `side` prop for popover placement, since the header needs it to open downward instead of upward). `LanguageFab.tsx` is now a thin `fixed bottom-6 right-6` wrapper around it — `Login.tsx`/`JoinShell.tsx` behavior unchanged.
- `Menu.tsx` header, mobile-only (`sm:` breakpoints keep desktop exactly as before):
  - Back button: `h-13 w-13` → `h-10 w-10` (`sm:h-13 sm:w-13`), icon `w-5 h-5` → `w-4 h-4` (`sm:w-5 sm:h-5`).
  - Language control wrapper gets `ml-auto sm:ml-0`, pushing it flush to the header's right edge on mobile (previously it just trailed the "Ember" title with no forced spacing).
  - Below `sm`: renders `LanguageFabButton side="bottom"` instead of the `Select`-based `LanguageSwitcher`; at `sm`+ the original `LanguageSwitcher` is shown (`hidden sm:block`).

## 5. Why It Changed?
Direct visual request: the back button read as oversized next to the compact header content, the language dropdown sat awkwardly next to "Ember" instead of anchored to the header's edge, and the dropdown style was inconsistent with the FAB picker already established as the app's language-switch pattern on login/join screens.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- `cd frontend && pnpm run test:run` — 245/246 (67/68 files); the 1 failure is the pre-existing `MenuJoin.test.tsx` case, unrelated.
- Not visually verified on a device/browser.
