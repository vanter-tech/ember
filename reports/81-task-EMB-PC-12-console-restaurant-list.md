# Report 81 — EMB-PC-12: Console Restaurant List Page

## 1. Identification
- **Report Number:** 81
- **Task ID:** EMB-PC-12
- **Predecessor Task:** EMB-PC-11 (report 80)

## 2. Objective
Add a paginated restaurant list page to the `/console` platform app, consuming `GET /platform/restaurants`.

## 3. Modified Files
- `frontend/src/lib/platformApi.ts`
- `frontend/src/pages/console/ConsoleRestaurants.tsx` (new)
- `frontend/src/pages/console/ConsoleApp.tsx`
- `frontend/src/pages/console/ConsoleDashboard.tsx`

## 4. What Changed?
- `platformApi.ts`: added hand-typed `PlatformRestaurantSummary` (mirrors backend `PlatformRestaurantSummaryResponse`) and a local `Page<T>` interface (same shape as `api.ts`'s, but `platformApi.ts` doesn't import from `api.ts` to keep the two API layers independent), plus `platformRestaurantService.getAll(page, size)` calling `GET /platform/restaurants`.
- `ConsoleRestaurants.tsx`: new page — `useState` page + `useQuery` (keyed `['platformRestaurants', page]`), renders a table (name/slug/plan/status/createdAt) with a status badge and the shared `PaginationControls` component, following the same data-fetching shape as `pages/admin/Category.tsx`.
- `ConsoleApp.tsx`: added a guarded sibling route `restaurants` → `ConsoleRestaurants`, inside the existing `PlatformProtectedRoute`/`PlatformLayout` nesting.
- `ConsoleDashboard.tsx`: replaced the "EMB-PC-12+" placeholder text with a link to `restaurants`.

## 5. Why It Changed?
Continues the Platform/Super-Admin Console backlog (EMB-PC-10/11 landed the shell, auth guard, and layout with no content yet). This is the first real console page, giving operators visibility into the tenant directory before EMB-PC-13 (detail + status toggle) and EMB-PC-14 (create-restaurant form) build on top of it. Reused `PaginationControls` and the `Page<T>`/`useQuery` pattern already established for admin catalog lists (report 68) rather than inventing new pagination UI.

## Verification
`cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, exit 0). `ConsoleApp` still code-splits into its own chunk (`ConsoleApp-CLuXrc3b.js`, 6.33 kB), separate from the main bundle.
