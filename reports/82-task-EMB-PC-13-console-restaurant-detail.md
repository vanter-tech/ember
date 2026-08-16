# Report 82 — EMB-PC-13: Console Restaurant Detail Page

## 1. Identification
- **Report number:** 82
- **Task ID:** EMB-PC-13
- **Predecessor Task:** EMB-PC-12 (report 81)

## 2. Objective
Add a platform-console restaurant detail page: tenant info, its ADMIN owner(s), an enable/disable (suspend/reactivate) status toggle wired to `PATCH /platform/restaurants/{id}/status`, and a paginated per-tenant audit history from `GET /platform/audit-log?restaurantId=`.

## 3. Modified Files
- `frontend/src/lib/platformApi.ts`
- `frontend/src/pages/console/ConsoleApp.tsx`
- `frontend/src/pages/console/ConsoleRestaurants.tsx`
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx` (new)

## 4. What Changed?
- `platformApi.ts` gained hand-typed `PlatformRestaurantAdmin`, `PlatformRestaurantDetail`, and `PlatformAuditLogEntry` (mirroring `PlatformRestaurantAdminResponse`/`PlatformRestaurantDetailResponse`/`PlatformAuditLogResponse`), plus `platformRestaurantService.getById`/`.updateStatus` and a new `platformAuditLogService.getByRestaurant`.
- New `pages/console/ConsoleRestaurantDetail.tsx`: reads `:id` from the route, fetches restaurant detail (`useQuery`) and a paginated audit log filtered by `restaurantId` (separate `useQuery` with its own page state), renders an info card, an admins table, and an audit-log table using the same table styling as `ConsoleRestaurants.tsx`. A `useMutation` toggles status (`SUSPENDED` &harr; `ACTIVE`) and invalidates the detail, audit-log, and restaurant-list query keys on success. Audit log pagination reuses `PaginationControls`.
- `ConsoleApp.tsx` gained a guarded sibling route `restaurants/:id` inside the same `PlatformLayout`/`PlatformProtectedRoute` tree as `restaurants`.
- `ConsoleRestaurants.tsx` row names are now `Link`s to `/console/restaurants/:id`.

## 5. Why It Changed?
Completes the tenant-management loop started in EMB-PC-06/07/09: operators can now drill from the restaurant list into a single tenant's detail, see who owns it, suspend/reactivate it without leaving the console, and review that tenant's audit trail — all previously backend-only capability with no frontend surface. Status toggle and audit-log invalidation share query keys so the UI reflects a status change immediately without a manual refetch.

## Verification
`cd frontend && pnpm run build` (`tsc -b && vite build`) — exit 0. `ConsoleApp` remains its own code-split chunk (`ConsoleApp-*.js`, 11.88 kB gzip 3.03 kB).
