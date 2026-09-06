# Report 371 — TopNav action button: icon-only on mobile

## 1. Identification
- **Report number:** 371
- **Current Task ID:** TopNav action button wraps to its own row on `/admin/inventory`
  (long labels) while short-label views look fine
- **Predecessor Task:** report 370 (TopNav mobile wrap)

## 2. Objective
After report 370 the TopNav wraps on mobile. On `/admin/inventory` the action label is
long ("Nuevo grupo de modificadores", "Rastrear inventario"), so the `shrink-0` button
drops below the title row; short-label views ("Nueva categoría", "Nuevo platillo") stay
inline. Make the button a constant size on mobile so every view behaves the same.

## 3. Modified Files
- `frontend/src/components/TopNav.tsx` (class names + markup only)

## 4. What Changed?
`#topnav-create-button`:
- The label is now `<span className="hidden sm:inline">{buttonText}</span>` — below the
  `sm` breakpoint the button is icon-only (`Plus`), from `sm` up it shows the text as
  before.
- Added `aria-label={buttonText}` and `title={buttonText}` so the icon-only state keeps
  an accessible name and a hover tooltip.
- Horizontal padding `px-5` → `px-3 sm:px-5` so the icon-only pill isn't overly wide.

No logic, i18n, or dependency changes; the waiter clock pill and desktop layout are
untouched.

## 5. Why It Changed?
The button label length varied per route, so a fixed-size (`shrink-0 whitespace-nowrap`)
button fit beside the truncating title on some views and wrapped on others —
inconsistent. Collapsing to an icon on small screens makes its width label-independent,
so it always sits inline next to the title regardless of route, and the full label is
still available at `sm+` and via `title` / `aria-label`.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass (unchanged).
