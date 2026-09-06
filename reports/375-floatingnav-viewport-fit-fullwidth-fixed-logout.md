# Report 375 — FloatingNav: viewport-fit=cover + full-width mobile bar with pinned logout

## 1. Identification
- **Report number:** 375
- **Current Task ID:** FloatingNav still hugs the bottom edge on newer iPhones and clips
  the logout on iPhone 12 and smaller
- **Predecessor Task:** report 374 (FloatingNav safe-area attempt + smaller icons)

## 2. Objective
1. Report 374's `env(safe-area-inset-bottom)` did nothing because the page never opted
   into the safe area.
2. Even at `w-10`, ADMIN's ~8 icons + logout overflow a centred pill on a 390px phone,
   so the logout sits off the right edge.

## 3. Modified Files
- `frontend/index.html`
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
**`index.html`** — viewport meta now includes `viewport-fit=cover`, so iOS actually
reports `env(safe-area-inset-*)` instead of `0`.

**`FloatingNav.tsx`**
- Bottom offset `bottom-[calc(1rem_+_env(safe-area-inset-bottom))] sm:bottom-8` — with
  `viewport-fit=cover` this clears the home indicator on notched iPhones and stays at
  16px elsewhere / 32px on `sm+`.
- The pill is near full-width on mobile: `w-full max-w-[calc(100vw-1.5rem)]`
  (`mx-auto` → 12px each side), reverting to `sm:w-max sm:max-w-[92vw]` (centred content
  pill) at `sm+`.
- Structure split: the role links live in a
  `flex-1 min-w-0 overflow-x-auto no-scrollbar` region that becomes
  `sm:flex-initial sm:overflow-visible` at `sm+`; the logout button sits **outside** that
  region behind the `border-l` with `shrink-0`, so it is always visible while the links
  scroll under it on small screens. No gradient overlay (that was the part rejected in
  report 372).
- Icon sizing / removed avatar from report 374 are kept.

## 5. Why It Changed?
`env(safe-area-inset-bottom)` only resolves to a non-zero value when the document sets
`viewport-fit=cover`; without it the report-374 `calc()` was just `1.25rem`. For the
horizontal clip, a centred `w-max` pill can't hold the ADMIN set on a phone at any
reasonable icon size — giving the bar the full width and pinning the logout outside the
scrollable link strip guarantees the logout is reachable and visible, which was the
specific complaint.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in touched files).
- `pnpm run test:run` — 24 files, 78 tests pass.
