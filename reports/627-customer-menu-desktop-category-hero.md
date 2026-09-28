# Report 627 — CUSTOMER-MENU-DESKTOP-CATEGORY-HERO

## 1. Identification
- Report: 627
- Task ID: CUSTOMER-MENU-DESKTOP-CATEGORY-HERO
- Predecessor: CUSTOMER-MENU-EMPTY-CATEGORY-STATE (report 626)

## 2. Objective
The desktop categories grid (mixed `col-span`/`row-span` sizes) read as a disorganized "pile of images" rather than an intentional layout — flagged specifically as a desktop-only issue (mobile's list was already fine). Wanted something that grabs attention first, then leads the eye to the rest without losing focus.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
- Extracted a local `CategoryCard` component (`variant: 'hero' | 'grid'`) from the previously-inlined card markup, so the hero and regular cards share one implementation instead of duplicating it — hero gets a bigger min-height (`min-h-96` vs `min-h-56`), larger title/description/badge, everything else identical.
- Desktop layout is now: the **first category** rendered as a full-width hero banner, then the **remaining categories** in a plain uniform `grid-cols-2 lg:grid-cols-4` grid below (no more mixed hero/1x1 spans mixed together in one grid). Mobile's row-list (`md:hidden`) is untouched.

## 5. Why It Changed?
Direct design feedback: the old asymmetric grid (first two cards oversized, rest 1x1, all mixed together) didn't read as a deliberate hierarchy because the images/colors vary too much to visually "explain" the size differences on their own. Separating one full-width hero from a calm, uniform grid gives an unambiguous focal point followed by an orderly secondary section.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`**: "Bebidas" renders as the full-width hero, the other 7 categories sit in a clean 4-column grid below; clicking the hero correctly navigates into its items (showing the empty-state added in report 626).
