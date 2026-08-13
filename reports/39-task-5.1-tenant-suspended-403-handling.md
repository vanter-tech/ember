# Report 39 — task-5.1

**Predecessor Task:** task-4.4

## Objective
Add global handling in the frontend for the 403 `RestaurantStatus != ACTIVE` response written by `SecurityConfig.jwtAuthFilter` (task-4.4) — previously `api.ts`'s response interceptor only special-cased 401, so a suspended/inactive tenant's requests failed silently with no UI treatment.

## Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/uiStore.ts`
- `frontend/src/App.tsx`
- `frontend/src/components/TenantSuspendedModal.tsx` (new)

## What Changed?
- `api.ts`: response interceptor now checks 403 responses' `data.detail` against the exact strings `jwtAuthFilter.writeSuspendedTenantResponse` emits ("This tenant account is …" / "Tenant account not found."), and opens a `TENANT_SUSPENDED` modal via `useUIStore` when matched. `@PreAuthorize` role-denial 403s (`GlobalExceptionHandler.handleAccessDenied`, detail "Access denied") don't match and are left untouched.
- `uiStore.ts`: added `'TENANT_SUSPENDED'` to `ModalType`.
- `TenantSuspendedModal.tsx` (new): shadcn `Dialog`, non-dismissable (`onOpenChange` no-op, `showCloseButton={false}`), shows the backend's detail message, single "Cerrar sesión" action that logs out and navigates to `/login`.
- `App.tsx`: mounted the modal once at the router root (unlike the existing `GlobalDeleteModal`, which is mounted per-page, this needs to catch a 403 from any route).

## Why It Changed?
The suspension check landed in task-4.4 but had no frontend consumer, so a suspended tenant's users saw broken pages/silent failures instead of a clear, unblockable message. Matching on the response `detail` text (rather than title, which is "Forbidden" for both this and role-based 403s) is the only way to distinguish the two cases without a backend change, since neither response sets a distinguishing `type`/custom property today.

## Verification
`cd frontend && pnpm run build` — PASSING (0 TS errors, `tsc -b && vite build` succeeded).
