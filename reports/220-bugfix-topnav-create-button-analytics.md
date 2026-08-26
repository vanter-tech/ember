# Report 220 — bugfix-topnav-create-button-analytics

## 1. Identification
- **Report Number:** 220
- **Task ID:** bugfix-topnav-create-button-analytics
- **Predecessor Task:** report 219 (bugfix-settings-tour-help-button-missing)

## 2. Objective
Remove TopNav's "+ Nuevo registro" create button on `/admin/analytics`, a read-only view with nothing to create, per user report.

## 3. Modified Files
- `frontend/src/components/TopNav.tsx`

## 4. What Changed?
Added `isAnalyticsRoute = path.includes('/admin/analytics')`. The right-side header slot's ternary now renders `null` for that route instead of falling through to the default create button (`isWaiterRoute ? <clock/> : isAnalyticsRoute ? null : <button/>`).

## 5. Why It Changed?
`TopNav`'s create button always renders on non-waiter routes; its `buttonText`/`actionType` are only overridden by an `else if` chain keyed on specific admin routes. `/admin/analytics` matched none of those branches, so it fell through to the default label ("Nuevo registro") with `actionType` left `null` — the button was visible but clicking it called `openModal(null, ...)`, a no-op, on a page that has nothing to create. `pnpm run build` PASS after the fix.
