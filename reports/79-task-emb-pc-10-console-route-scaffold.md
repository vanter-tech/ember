# Report 79 — EMB-PC-10: Platform Console Route Scaffold

## 1. Identification
- **Report:** 79
- **Task ID:** EMB-PC-10
- **Predecessor Task:** EMB-PC-09 (report 78)

## 2. Objective
Scaffold the frontend entry point for the platform/super-admin console: a code-split `/console` route tree, a token store for platform operators separate from tenant `useAuthStore`, and a dedicated Axios instance for `/platform/**` calls.

## 3. Modified Files
- `frontend/src/App.tsx`
- `frontend/src/store/platformAuthStore.ts` (new)
- `frontend/src/lib/platformApi.ts` (new)
- `frontend/src/pages/console/ConsolePlaceholder.tsx` (new)

## 4. What Changed?
- `platformAuthStore.ts`: zustand store (persisted under `ember-platform-auth-storage`) holding `token`/`operatorId`/`name`/`email` for a platform operator, with `setAuth`/`logout` — structurally mirrors `authStore.ts` but is a fully separate slot, never sharing state with tenant auth.
- `platformApi.ts`: separate `axios.create()` instance (`platformApi`) with its own request interceptor (attaches the platform token) and response interceptor (401 → `usePlatformAuthStore.logout()`). Also hand-types `PlatformLoginRequest`/`PlatformAuthResponse`/`PlatformPasswordChangeRequest` and exposes `platformAuthService.login`/`changePassword` against `/platform/auth/login` and `/platform/auth/password`.
- `ConsolePlaceholder.tsx`: minimal stub page rendered at `/console/*` until EMB-PC-11 adds the real login/layout.
- `App.tsx`: `ConsolePlaceholder` is imported via `React.lazy`, mounted at `/console/*` wrapped in `<Suspense fallback={null}>`, outside every existing `ProtectedRoute` tree.

## 5. Why It Changed?
The platform console is a distinct audience (Vanter/platform operators, not restaurant tenants) authenticated against a separate JWT signing key (`platform.jwt.secret`, see EMB-PC-04). Keeping its token store, API client, and route chunk fully separate from the tenant app prevents any operator-only code or credentials from ending up in the bundle a tenant browser downloads — `React.lazy` guarantees `ConsolePlaceholder` (and everything it imports, including `platformApi`) ships as its own chunk (confirmed in the build output: `ConsolePlaceholder-*.js`, 0.38 kB, separate from the 707 kB main `index-*.js`). `backend-types.ts` has never been regenerated since the `platform/**` endpoints landed (it requires a live backend for `pnpm run openapi`), so the platform request/response shapes are hand-typed against the backend DTOs directly rather than left blocked on that regeneration.

## Verification
`cd frontend && pnpm run build` (`tsc -b && vite build`) — PASSING, exit 0. `ConsolePlaceholder` chunk confirmed separate in build output.
