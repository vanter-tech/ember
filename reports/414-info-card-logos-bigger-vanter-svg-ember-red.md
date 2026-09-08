# Report 414 — Info card: bigger logos, Vanter as SVG, Ember mark in #8c1717

## 1. Identification
- **Report number:** 414
- **Current Task ID:** ad-hoc frontend polish (no milestone ID)
- **Predecessor Task:** report 413 — Info card Vanter · Ember logo row

## 2. Objective
Three tweaks to the `/admin/settings` → Información logo row:
- make both logos bigger
- swap the Vanter logo from `.webp` to the SVG the owner produced
- recolour the Ember mark from black to Ember red (`#8c1717`)

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/InfoSettings.tsx`
- `frontend/public/vanter-tech_logo.svg` (new, replaces the `.webp`)
- `frontend/public/vanter-tech_logo.webp` (removed)
- `frontend/public/ember_logo_info.svg` (fill change)

## 4. What Changed?
- **Size:** logo `<img>`s go from `h-12 sm:h-14` to `h-20 sm:h-24`; the vertical hair-line
  divider follows at `h-16 sm:h-20`.
- **Vanter asset:** `src="/vanter-tech_logo.webp"` → `src="/vanter-tech_logo.svg"`; the `.webp`
  is deleted from `frontend/public/`.
- **Ember colour:** `ember_logo_info.svg` — the single `fill="#000000"` on its root `<g>`
  (inherited by all 9 paths) changed to `fill="#8c1717"`.

## 5. Why It Changed?
Owner's request: larger marks, use the new vector Vanter logo, and match the Ember mark to
the brand red already used across the app (`#8c1717`).

## 6. Verification
- `cd frontend && pnpm run build` — clean; `dist/` has both SVGs, no stray `.webp`.
- `pnpm run lint` — 0 errors, 16 pre-existing warnings.
- `pnpm run test:run` — 118/118.
