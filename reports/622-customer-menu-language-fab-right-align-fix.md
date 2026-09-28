# Report 622 — CUSTOMER-MENU-LANGUAGE-FAB-RIGHT-ALIGN-FIX

## 1. Identification
- Report: 622
- Task ID: CUSTOMER-MENU-LANGUAGE-FAB-RIGHT-ALIGN-FIX
- Predecessor: CUSTOMER-MENU-DESKTOP-LANGUAGE-FAB-AND-CATEGORY-GRID (report 621)

## 2. Objective
Report 621 swapped desktop's language dropdown for the FAB but left it sitting next to the "Ember" title instead of pinned to the header's right edge.

## 3. Modified Files
- `frontend/src/pages/customer/Menu.tsx`

## 4. What Changed?
The header's language-control wrapper was `ml-auto sm:ml-0` — a leftover from when only mobile used the FAB and desktop's old `Select` dropdown was fine sitting next to the title. Since desktop now uses the same FAB (report 621), the `sm:ml-0` was canceling the right-push at desktop widths. Removed it; the wrapper is now plain `ml-auto`, pinning the FAB to the right at every breakpoint.

## 5. Why It Changed?
Direct visual feedback right after 621 shipped.

## Verification
- `cd frontend && pnpm run build` — clean (tsc -b + vite build).
- `cd frontend && pnpm run lint` — 0 errors, 15 pre-existing warnings, no new ones.
- **Live-verified with `claude-in-chrome`** against the running dev server, desktop width: the "ES" circle now sits at the header's right edge instead of next to "Ember".
