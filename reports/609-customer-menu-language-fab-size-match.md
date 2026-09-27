# Report 609 — CUSTOMER-MENU-LANGUAGE-FAB-SIZE-MATCH

## 1. Identification
- Report: 609
- Task ID: CUSTOMER-MENU-LANGUAGE-FAB-SIZE-MATCH
- Predecessor: CUSTOMER-MENU-MOBILE-HEADER-LANGUAGE-FAB (report 608)

## 2. Objective
Fix a visual mismatch from report 608: the mobile `/customer/menu` header's language FAB circle (`h-12 w-12`, hardcoded) was visibly bigger than the back button (`h-10 w-10`) next to it.

## 3. Modified Files
- `frontend/src/components/LanguageFabButton.tsx`
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- `LanguageFabButton` gains an optional `className` prop, merged onto its base classes via `cn` (tailwind-merge), so size can be overridden per usage without touching the default.
- `Menu.tsx` passes `className="h-10 w-10 text-xs"` to the mobile FAB usage, matching the back button's `h-10 w-10` exactly. The auth `LanguageFab` (login/join screens) keeps the original `h-12 w-12` default — untouched.

## 5. Why It Changed?
Direct visual feedback right after 608 shipped: the two circles side by side in the header didn't match in size.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- Not visually verified on a device/browser.
