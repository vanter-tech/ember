# Report 370 — TopNav: stop clipping the action button on mobile

## 1. Identification
- **Report number:** 370
- **Current Task ID:** TopNav mobile overflow — action button rendered half cut off
- **Predecessor Task:** report 369 (admin sidebar mobile popover menu)

## 2. Objective
On narrow viewports the TopNav's single non-wrapping row (title + search + action
button) overflowed horizontally and the trailing button was clipped to ~half width.
Make the header reflow instead of overflow.

## 3. Modified Files
- `frontend/src/components/TopNav.tsx` (class names only)

## 4. What Changed?
- `<header>`: `flex` → `flex flex-wrap`, added `gap-x-3 gap-y-2`, padding
  `px-6` → `px-3 sm:px-6`.
- Title group `<div>`: added `min-w-0`; `<h1>` now `text-xl sm:text-2xl md:text-3xl`
  with `truncate min-w-0` so a long business name shrinks instead of pushing siblings
  off-screen.
- Search wrapper: `flex-1 max-w-md mx-8` → `w-full order-last md:order-none md:flex-1
  md:max-w-md mx-0 md:mx-8`. Below `md` it wraps onto its own full-width row; at `md+`
  it is the same centre element as before.
- Action button (`#topnav-create-button`) and the waiter clock pill: added
  `shrink-0 whitespace-nowrap` so flex-wrap can never compress or clip them.

No logic, i18n, or dependency changes.

## 5. Why It Changed?
The header was `flex items-center justify-between` with no `flex-wrap`, a `text-3xl`
title, a `flex-1 mx-8` search, and a button with no `shrink-0`. Their combined
min-content width exceeds a phone viewport, so the row overflowed and the last child
(the button) was cut. Allowing the row to wrap, letting the title truncate, and pinning
the button/clock against shrink keeps every control fully visible; the `md:` overrides
preserve today's desktop single-row layout exactly.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass (unchanged).
