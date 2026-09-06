# Report 374 — FloatingNav: iOS safe-area gap + smaller icons on mobile

## 1. Identification
- **Report number:** 374
- **Current Task ID:** FloatingNav sits against the bottom edge on iPhone + icons still
  crowd on mobile
- **Predecessor Task:** report 373 (revert of report 372's FloatingNav rework)

## 2. Objective
1. On iPhone 14 Pro Max the pill has only `bottom-4` (16px) clearance, so it hugs the
   home-indicator area with no breathing room.
2. The ADMIN pill still packs too many `w-12` icons on a phone. Chosen fix: shrink the
   items on mobile and drop the unused avatar — no structural change, no overflow menu.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
- **Safe-area bottom offset** — `<nav>` `bottom-4 sm:bottom-8` →
  `bottom-[calc(1.25rem_+_env(safe-area-inset-bottom))] sm:bottom-8`, so on devices with
  a home indicator the pill clears it (≈20px + the inset) while `sm+` keeps 32px.
- **Smaller items below `sm`** — `navItemClass` is now `w-10 h-10 sm:w-12 sm:h-12` with
  `[&_svg]:size-5 sm:[&_svg]:size-6` (icons 20px on mobile, 24px from `sm`), plus
  `shrink-0` so the row scrolls instead of squashing when it still overflows on the
  narrowest phones. The admin divider (`h-6 sm:h-8`, `mx-1 sm:mx-2`, `shrink-0`) and the
  customer "leave" button got the same size treatment.
- **Avatar removed** — the non-interactive `User` chip and its lucide import are gone;
  the trailing group is just the logout button (kept as a bare icon as before, only the
  icon size made responsive), still behind the `border-l`.

The single `overflow-x-auto no-scrollbar` row structure from before is unchanged — no
scroll fade, no split region (those were reverted in report 373).

## 5. Why It Changed?
`bottom-4` predates testing on home-indicator iPhones; `env(safe-area-inset-bottom)` is
the standard way to keep a fixed bottom element off that zone. For the icons, 8 controls
at 48px + gaps overflow a phone's `max-w-[92vw]`; dropping each to 40px (and the icon to
20px) plus removing the dead avatar frees roughly one icon's worth of width per two
items, which fits a modern iPhone without scrolling and keeps a clean horizontal scroll
on smaller devices.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass.
