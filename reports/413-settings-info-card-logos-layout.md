# Report 413 — Settings "Información": Vanter · Ember logo row, remove double frame

## 1. Identification
- **Report number:** 413
- **Current Task ID:** ad-hoc frontend polish (no milestone ID)
- **Predecessor Task:** report 412 — landing favicon + Settings "Información" tab

## 2. Objective
Redesign the "Información" card added in report 412:
- a centered logo row — Vanter on the left, a hair-line vertical divider, Ember on the right
- the made-by / location / legal / support text below it
- kill the blank space at the bottom of the card (the inner card was short while the outer
  container stretched to the settings sidebar's height)

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/InfoSettings.tsx`
- `frontend/public/vanter-tech_logo.webp` (new, provided by the owner)
- `frontend/public/ember_logo_info.svg` (new, provided by the owner)

## 4. What Changed?
- **Dropped the inner `<Card>`** (and the `Card`/`Info` imports). `Settings.tsx` already wraps
  every tab in `#settings-tour-content`, a white rounded/bordered/shadowed container — the
  second frame was redundant and the reason the short content left a white gap. `InfoSettings`
  is now a single `div` with `h-full flex flex-col justify-center gap-8 p-6 md:p-10`, so the
  content sits centered in whatever height the container takes (matching the sidebar) with no
  trailing blank.
- **Logo row:** `vanter-tech_logo.webp` (linked to `https://vanter.net`) · a `w-px` /
  `h-12 sm:h-14` `bg-zinc-200` divider · `ember_logo_info.svg`. Both `h-12 w-auto object-contain`
  (`sm:h-14`).
- **Hair-line horizontal divider** (`h-px max-w-xs bg-zinc-200`) then the text block, now
  center-aligned: made-by → `vanter.net` link, `Managua, Nicaragua`, `© {year} …`, and the
  support line linking to `ember.vanter.net/contacto`. Same i18n keys as report 412 — no
  string changes.

## 5. Why It Changed?
- The owner asked for the two brand marks shown side by side (who builds it · the product)
  with the text underneath, and for the empty space in the card to go.
- A card inside a card is visual noise; removing it also fixes the gap in one move.

## 6. Verification
- `cd frontend && pnpm run build` — clean; both assets emitted to `dist/`.
- `pnpm run lint` — 0 errors, 16 pre-existing warnings.
- `pnpm run test:run` — 118/118.
