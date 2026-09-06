# Report 372 — FloatingNav: scroll fade hint, fixed logout, drop dead avatar

## 1. Identification
- **Report number:** 372
- **Current Task ID:** FloatingNav crowds/overlaps its icons on mobile (ADMIN role)
- **Predecessor Task:** report 371 (TopNav action button icon-only on mobile)

## 2. Objective
For ADMIN the pill packs 6 links + settings + a decorative avatar + logout. On mobile
they compressed together and looked cramped. Keep the horizontal scroll but make it
readable: a right-edge fade telling the user there is more, a logout that never scrolls
away, and no unused avatar.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
- **Dead avatar removed** — the non-interactive `User` chip and its lucide import are
  gone.
- **Logout pinned** — the `<nav>` is now `[ scrollable links region ] + [ fixed logout ]`.
  The links live in an inner `overflow-x-auto no-scrollbar` div inside a
  `relative flex-1 min-w-0` wrapper; the logout button (now a `w-12 h-12` circle to
  match the nav items) sits after it behind the `border-l`, with `shrink-0`, so it stays
  visible regardless of scroll.
- **Icons stop compressing** — `shrink-0` added to `navItemClass`, the admin divider and
  the customer "leave" button, so items keep their 48px and scroll instead of squashing.
- **Right-edge fade hint** — a `pointer-events-none absolute inset-y-0 right-0 w-10
  bg-gradient-to-l from-[#8c1717]/70 to-transparent` overlay, rendered only while the
  region can still scroll right. A `scrollRef` plus a `scroll` + `resize` listener sets
  `showEndFade = scrollLeft + clientWidth < scrollWidth - 1`; short navs
  (waiter/kitchen/customer) never show it.

## 5. Why It Changed?
`navItemClass` set `w-12` but not `shrink-0`, so inside the flex row the icons were being
squeezed below size ("amontonado") rather than scrolling cleanly. Pinning item size, and
moving the scroll onto an inner region so the logout can stay outside it, restores a
proper horizontal scroll; the brand-red fade makes the hidden overflow discoverable
without adding an overflow menu. The avatar chip was purely decorative and only added to
the crowding.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass (unchanged; no test renders FloatingNav).
