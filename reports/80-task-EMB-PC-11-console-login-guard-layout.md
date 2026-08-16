# Report 80 — EMB-PC-11

**Task ID:** EMB-PC-11
**Predecessor Task:** EMB-PC-10 (report 79)

## Objective
Add the platform-console auth guard, its own layout shell, and `/console/login` wired to `POST /platform/auth/login`, replacing the `ConsolePlaceholder` stub.

## Modified Files
- `frontend/src/components/PlatformProtectedRoute.tsx` (new)
- `frontend/src/layouts/PlatformLayout.tsx` (new)
- `frontend/src/pages/console/ConsoleLogin.tsx` (new)
- `frontend/src/pages/console/ConsoleDashboard.tsx` (new)
- `frontend/src/pages/console/ConsoleApp.tsx` (new)
- `frontend/src/pages/console/ConsolePlaceholder.tsx` (deleted)
- `frontend/src/App.tsx`

## What Changed?
`PlatformProtectedRoute` mirrors the tenant `ProtectedRoute` shape but reads `usePlatformAuthStore().token` and redirects (relative) to `login` — no role check, single operator identity. `PlatformLayout` is a standalone header (title, operator name/email, logout button) plus `Outlet`; it imports no tenant nav components (`TopNav`/`FloatingNav`). `ConsoleLogin` reuses the `react-hook-form` + `zod` + shadcn `Form`/`Card` pattern from the tenant `Login.tsx`, calling `platformAuthService.login`, `usePlatformAuthStore().setAuth`, then navigating to `/console`. `ConsoleDashboard` is a minimal authenticated placeholder (restaurant list is EMB-PC-12). `ConsoleApp` is a new component that defines a nested `<Routes>` (`login` route, then guard → layout → index dashboard) and is the thing `App.tsx` now lazy-loads at the existing `/console/*` mount — `ConsolePlaceholder.tsx` is deleted since `ConsoleApp` fully supersedes it.

## Why It Changed?
Keeps the single dynamic-`import()` boundary the EMB-PC-10 comment establishes ("must never land in the tenant app's main bundle"): all console-only code (guard, layout, login, dashboard) lives inside the one lazily-loaded `ConsoleApp` subtree rather than being statically imported into `App.tsx`, so the console still ships as its own chunk. Verified in the `vite build` output: `ConsoleApp-*.js` (3.91 kB) is separate from `index-*.js` (main bundle).

## Verification
`pnpm run build` (`tsc -b && vite build`) — exit 0. `ConsoleApp` chunk confirmed separate from main bundle.
