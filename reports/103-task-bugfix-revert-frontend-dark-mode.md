# Report 103

## 1. Identification
- **Report number:** 103
- **Current Task ID:** bugfix-revert-frontend-dark-mode
- **Predecessor Task:** bugfix-mobile-nav-dark-mode (report 102)

## 2. Objective
Report 102 bundled a dark-mode toggle (theme store, Sun/Moon button, brand-red `.dark` primary color) into `frontend`. That was the wrong target — dark mode belongs to the `/landing` marketing site, not the authenticated app. Remove it cleanly from `frontend` while keeping report 102's unrelated mobile floating-nav overflow fix intact.

## 3. Modified Files
- `frontend/src/store/themeStore.ts` (deleted)
- `frontend/index.html`
- `frontend/src/components/FloatingNav.tsx`
- `frontend/src/index.css`

## 4. What Changed?
- Deleted `store/themeStore.ts` (Zustand theme store; confirmed no other consumers).
- Removed the pre-hydration inline dark-mode script from `index.html`'s `<head>`.
- `FloatingNav.tsx`: removed the `Sun`/`Moon` icon imports, `useThemeStore` import/usage, and the toggle `<button>`. The mobile-overflow className fix (`max-w-[92vw] overflow-x-auto no-scrollbar`, tighter mobile spacing) from report 102 was left untouched.
- `index.css`: reverted `.dark`'s `--primary`/`--primary-foreground` from the brand-red `oklch(0.55 0.19 28.5)` back to the original generic gray (`oklch(0.922 0 0)` / `oklch(0.205 0 0)`).
- `PaginationControls.tsx` was untouched — its report-102 changes (mobile reposition, `Button` component) were unrelated to dark mode.

## 5. Why It Changed?
User correction: the dark-mode/color work was scoped to the wrong project. It's being re-implemented properly on `/landing` in a follow-up task, using that site's own accent color. System health: `pnpm run build` (`tsc -b && vite build`) PASSING, `dist/` removed post-verify. Backend/landing untouched by this task.
