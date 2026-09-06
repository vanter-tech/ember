# Report 373 — Revert FloatingNav changes from report 372

## 1. Identification
- **Report number:** 373
- **Current Task ID:** revert — FloatingNav rework (report 372) rejected on visual review
- **Predecessor Task:** report 372 (FloatingNav scroll fade + fixed logout + drop avatar)

## 2. Objective
Report 372's FloatingNav restructure (split scroll region, pinned logout, brand-red
right-edge gradient, removed avatar) looked wrong in the running app. Restore the
component to its pre-372 state.

## 3. Modified Files
- `frontend/src/components/FloatingNav.tsx`

## 4. What Changed?
`FloatingNav.tsx` was checked out from `04c0f7cb~1` (report 371 state), fully undoing the
report-372 edits: the `<nav>` is again a single `overflow-x-auto no-scrollbar` flex row,
`navItemClass` has no `shrink-0`, the `useRef`/`useEffect` scroll-fade tracking and the
gradient overlay are gone, and the `User` avatar chip plus its lucide import are back.

Report 372's file (`reports/372-...md`) is kept as the record of what was tried.

## 5. Why It Changed?
User feedback: "Quita esos cambios al floatingNav, quedaron horribles." No alternative
FloatingNav treatment was requested, so this is a straight revert; the original
mobile-crowding concern from report 372 is unaddressed again and can be revisited with a
different approach if raised.

## 6. Verification
- `pnpm run build` — clean (`tsc -b` + `vite build`).
- `pnpm run lint` — 0 errors (16 pre-existing warnings, none in this file).
- `pnpm run test:run` — 24 files, 78 tests pass.
