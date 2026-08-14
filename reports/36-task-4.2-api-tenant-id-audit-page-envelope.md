# Report 36 — task-4.2: audit `api.ts` tenant-id usage, consume `Page<T>` envelope

## 1. Identification
- **Report number:** 36
- **Task ID:** task-4.2
- **Predecessor task:** task-4.1 (report 35 — Vitest/RTL setup)

## 2. Objective
Audit `frontend/src/lib/api.ts` for client-supplied tenant-id usage (confirm `restaurantId` is
sourced only from session state, never user-editable input), and update
`kitchenServices.getOrders`/`menuItemService.getAll` to consume the `Page<T>` envelope task-3.6
introduced on `GET /kitchen/orders` and `GET /catalog/items` (see report 32, §6).

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/pages/waiter/Tables.tsx`
- `frontend/src/pages/admin/ListMenuItem.tsx`

## 4. What Changed?

### 4.1 Tenant-id audit finding
`restaurantId` is written to `authStore` only via `setAuth(data: LoginResponse)` at login
(`store/authStore.ts`) and read back in `Tables.tsx` purely as a `queryKey`/`enabled` gate — never
as free-text/user-editable input. No IDOR risk. However, `DashboardService.getDashboardData` was
still sending it as a `restaurantId` query param to `GET /dashboard/status`, a param
`DashboardController` has ignored since task-2.12 (tenant is derived server-side from
`TenantContextHolder.requireTenantId()`, bound from the JWT `rid` claim). Removed the dead param:
`DashboardService.getDashboardData` now takes no arguments and issues a plain
`GET /dashboard/status`; `Tables.tsx`'s `queryFn` call was updated to match (the `restaurantId`
`queryKey`/`enabled` gate was left untouched — it still correctly waits for an authenticated
session).

### 4.2 `Page<T>` envelope
Added a local `Page<T>` interface to `api.ts` mirroring Spring Data's JSON shape (`content`,
`totalElements`, `totalPages`, `size`, `number`) rather than regenerating `backend-types.ts` via
`pnpm run openapi`, since that script requires a live backend and the shape is a stable Spring
Data contract, not a hand-maintained DTO.
- `menuItemService.getAll` return type changed to `Promise<Page<MenuItemResponse>>`.
- `kitchenServices.getOrders` return type changed to `Promise<Page<kitchenOrders>>` (currently has
  no callers anywhere in the frontend, so this is a pure type-level fix — see §4.3).
- `ListMenuItem.tsx`, the sole caller of `menuItemService.getAll`, now reads
  `menuItemsPage?.content ?? []` instead of treating the response as a bare array.

### 4.3 `GET /kitchen/display` / `kitchenServices.getOrdersByTables` untouched
Per report 32 §4.3, `/kitchen/display` was deliberately kept unpaginated (still a bare array), so
`kitchenServices.getOrdersByTables` (the KDS board's actual data source) needed no change.

## 5. Why It Changed?
- The tenant-id audit is a standing security-review item after task-2.12's DashboardController IDOR
  fix; confirming the frontend never re-introduces a client-supplied tenant id closes the loop, and
  removing the now-ignored param eliminates dead, misleading code that could suggest the endpoint is
  still tenant-scoped by request param.
- The `Page<T>` update is required to avoid a runtime break: task-3.6 changed the wire format of
  `/kitchen/orders` and `/catalog/items` from a bare array to `{content, totalElements, ...}`, and
  this was explicitly deferred to task-4.2 by user decision at that time.

## 6. Verification
`cd frontend && pnpm run build` → `tsc -b` clean (0 errors), `vite build` succeeded.
