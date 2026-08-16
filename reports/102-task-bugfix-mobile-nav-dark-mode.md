# Report 102 — bugfix-mobile-nav-dark-mode

## 1. Identification
- **Report Number:** 102
- **Task ID:** bugfix-mobile-nav-dark-mode (ad hoc, not in EMB-LP/EMB-PC backlog)
- **Predecessor Task:** EMB-LP-18 (report 101)

## 2. Objective
Fix a broken mobile layout in `FloatingNav`/`PaginationControls` and add a dark ("nocturne") mode toggle that uses the brand primary color instead of the generic shadcn gray.

## 3. Modified Files
- `frontend/index.html`
- `frontend/src/index.css`
- `frontend/src/store/themeStore.ts` (new)
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/components/PaginationControls.tsx`

## 4. What Changed?
- `FloatingNav`: pill nav was `w-max` with no width cap — for ADMIN (5 icons + avatar/logout) this exceeded small viewport widths with no way to scroll, pushing buttons off-screen. Added `max-w-[92vw] overflow-x-auto no-scrollbar` (reusing the existing `.no-scrollbar` utility) plus tighter mobile spacing (`bottom-4`/`px-2`/`gap-1` on mobile, widening at `sm:`).
- `PaginationControls`: was `fixed bottom-8 right-8`, same vertical offset as the centered `FloatingNav`, causing overlap on narrow screens. Now `bottom-24 right-4` on mobile (sits above the nav bar) and reverts to `sm:bottom-8 sm:right-8` at wider breakpoints.
- Added `frontend/src/store/themeStore.ts`: a Zustand store (`theme`, `toggleTheme`) that toggles the `.dark` class on `<html>` and persists the choice to `localStorage` under `ember-theme`, defaulting to `prefers-color-scheme` on first load.
- `frontend/index.html`: added an inline pre-hydration script that applies the stored/system theme to `<html>` before first paint, avoiding a flash of the wrong theme.
- `FloatingNav.tsx`: added a Sun/Moon toggle button (next to logout) wired to `useThemeStore`.
- `frontend/src/index.css`: `.dark`'s `--primary`/`--primary-foreground` were the default shadcn near-white/near-black pair, unrelated to the brand. Changed to `oklch(0.55 0.19 28.5)` / `oklch(0.985 0 0)` — same hue family as the light-mode primary (`oklch(0.395 0.175 28.5)`) and the hardcoded brand reds (`#920703`/`#8c1717`), lightened for contrast on a dark background.

## 5. Why It Changed?
The mobile nav had no upper bound on its rendered width, so on real phone viewports (≤375px) it silently overflowed the screen with unreachable nav items — a functional break, not cosmetic. It shared a bottom offset with the pagination pill, so any admin list page with pagination collided the two fixed elements on mobile. Dark mode CSS variables already existed (shadcn scaffold, `.dark`/`@custom-variant dark`) but nothing ever toggled the class and the dark palette's primary color was the generic shadcn default rather than Ember's brand red, so a user-facing "nocturne mode" request needed both the toggle wiring and the color fix.

## 6. Verification
`cd frontend && pnpm run build` → `tsc -b && vite build` passed, exit 0. `dist/` removed post-verify.
