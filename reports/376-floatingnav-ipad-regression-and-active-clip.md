# Report 376 — FloatingNav: fix iPad disappearance + clipped active highlight

## 1. Identification
- **Report number:** 376
- **Current Task ID:** report 375 broke FloatingNav on iPad (gone) and clips the active
  item's red highlight top/bottom on small phones
- **Predecessor Task:** report 375 (viewport-fit=cover + full-width mobile bar)

## 2. Objective
1. Report 375 wrapped the links in a flex/scroll `<div>` that stayed in the layout at all
   sizes; on iPad (`sm+`) that changed the pill's flex model enough that it stopped
   showing.
2. The mobile wrapper's `overflow-x-auto` also clips overflow on the Y axis (the spec
   forces `overflow-y: auto` when `overflow-x` isn't `visible`), so the active nav item's
   `scale-110` + `shadow-md` were cut off top and bottom.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
The links wrapper `<div>` is now
`flex min-w-0 flex-1 items-center gap-1 -my-2 py-2 overflow-x-auto no-scrollbar sm:contents`:
- **`sm:contents`** — at `sm+` the wrapper is `display: contents`, so it generates no box
  and the links render as direct `<nav>` children again. The `sm+` layout is therefore
  byte-for-byte the original centred pill (the state that worked on iPad before report
  375). The mobile-only `flex-1 / overflow-x-auto` scroll strip stays for `< sm`.
- **`-my-2 py-2`** — expands the wrapper's padding box 8px on each vertical edge (pulled
  back by the negative margin so the pill height is unchanged), giving the active item's
  `scale-110` and `shadow-md` room before the horizontal-scroll container clips them.
  Inert at `sm+` where the wrapper has no box.

Dropped the now-redundant `sm:gap-2 sm:flex-initial sm:overflow-visible` from the wrapper.
The pinned-logout `<div>`, near-full-width nav on mobile, and `viewport-fit=cover` from
report 375 are unchanged.

## 5. Why It Changed?
`display: contents` is the clean way to make a wrapper exist only at one breakpoint:
below `sm` it's a real scroll container, at `sm+` it vanishes and the parent flex layout
is exactly what it was before report 375 — which removes the iPad regression by
construction. The negative-margin/padding pair is the standard fix for a scaled/shadowed
child inside an `overflow` scroller.

## 6. Verification
- `pnpm run build` — clean; `.sm\:contents{display:contents}` present in the bundle CSS.
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass.
