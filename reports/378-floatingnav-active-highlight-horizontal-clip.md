# Report 378 — FloatingNav: active highlight clipped left/right

## 1. Identification
- **Report number:** 378
- **Current Task ID:** the active nav item's red highlight is cut on its left and right
  edges (CUSTOMER view, iPhone 12 Pro)
- **Predecessor Task:** report 377 (limit full-width mobile bar to ADMIN)

## 2. Objective
The links wrapper kept `overflow-x-auto` for every role. `overflow-x: auto` clips on the
X axis even with nothing to scroll, so the active item's `scale-110` (2px bleed per side)
was cut off left and right on the non-scrolling CUSTOMER / WAITER / KITCHEN navs.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
- The wrapper's `overflow-x-auto no-scrollbar` and `-my-2 py-2` are now applied **only for
  `role === 'ADMIN'`** (alongside `flex-1`). Non-ADMIN roles get a plain
  `flex min-w-0 items-center gap-1` row with no overflow, so the active highlight,
  scale and shadow render fully.
- `navItemClass`: the active item's `scale-110` is now `sm:scale-110` — no scale bump
  below `sm`. On the ADMIN mobile scroll strip (which still clips at its scroll edges)
  the active item is now exactly `w-10`, so its red background sits flush instead of
  bleeding into the clip; the `bg-[#920703] text-red-100` still marks it clearly. The
  scale still applies from `sm` up where nothing clips it.

## 5. Why It Changed?
`overflow-x-auto` is only needed where the content actually overflows — the ADMIN strip.
Everywhere else it was silently trimming the active pill's edges. Removing it for the
short navs, and dropping the mobile scale bump that caused the bleed on the ADMIN strip,
keeps the indicator intact in every role without losing the scrollable ADMIN behaviour.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass.
