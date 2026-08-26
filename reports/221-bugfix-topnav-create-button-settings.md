# Report 221 — bugfix-topnav-create-button-settings

## 1. Identification
- **Report Number:** 221
- **Task ID:** bugfix-topnav-create-button-settings
- **Predecessor Task:** report 220 (bugfix-topnav-create-button-analytics)

## 2. Objective
Remove TopNav's "+ Nuevo registro" create button on `/admin/settings`, the same no-op button already fixed for `/admin/analytics` in report 220.

## 3. Modified Files
- `frontend/src/components/TopNav.tsx`

## 4. What Changed?
Added `isSettingsRoute = path.includes('/admin/settings')`. The right-side header slot's ternary now also renders `null` for that route: `isWaiterRoute ? <clock/> : isAnalyticsRoute || isSettingsRoute ? null : <button/>`.

## 5. Why It Changed?
Same root cause as report 220: `/admin/settings` matched none of `TopNav.tsx`'s route branches, so it fell through to the default label with `actionType` left `null` — a visible but non-functional create button. Flagged as a known follow-up in `PROGRESS.md` after report 220; user requested the same fix here. `pnpm run build` PASS after the fix.
