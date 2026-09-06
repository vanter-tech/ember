# Report 377 — FloatingNav: limit the full-width mobile bar to ADMIN

## 1. Identification
- **Report number:** 377
- **Current Task ID:** FloatingNav broken in the CUSTOMER view on mobile — full-width bar
  with one left-aligned icon, not centred
- **Predecessor Task:** report 376 (FloatingNav iPad regression + clipped active highlight)

## 2. Objective
Report 375's near full-width mobile bar + `flex-1` scroll region were applied to every
role. CUSTOMER has only 1-3 items, so on a phone the bar spanned the screen with a lone
icon pinned left and the logout far right. Scope the full-width treatment to ADMIN, the
only role with enough items to need it.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
- `<nav>` width is now conditional: `role === 'ADMIN'` →
  `w-full max-w-[calc(100vw-1.5rem)]` (the mobile strip), every other role →
  `w-max max-w-[92vw]` (shrink-to-content centred pill). `sm:w-max sm:max-w-[92vw]` still
  applies to all at `sm+`.
- The links wrapper gets `flex-1` only for ADMIN; for other roles it just shrinks to its
  content, so the nav is a small centred pill with no empty gap. `overflow-x-auto`,
  `-my-2 py-2` and `sm:contents` are unchanged (harmless when there's nothing to
  scroll).

## 5. Why It Changed?
`w-full` + a `flex-1` link region only makes sense when the links would overflow — i.e.
ADMIN. For the short WAITER / KITCHEN / CUSTOMER navs it produced a stretched bar with a
left-anchored icon. Gating both on `role === 'ADMIN'` keeps ADMIN's scrollable
fixed-logout strip and restores the normal centred pill everywhere else, on mobile and
`sm+` alike.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass.
