# Report 335 — fix(console): logout redirects to a blank relative `/login`

## 1. Identification
- **Report number:** 335
- **Current Task:** `fix-console-logout-blank-redirect` (bug A of the waiter-quick-login / table-actions effort, branch `feat/waiter-quick-login-table-actions` off `main`)
- **Predecessor Task:** report 334 (`feat-waiter-table-detail-section-tour`)

## 2. Objective
Logging out from `/console` (platform-operator console) left the user on a blank page with
`/login` appended to the current path instead of returning to the console login screen.

## 3. Modified Files
- `frontend/src/components/PlatformProtectedRoute.tsx`
- `frontend/src/pages/console/ConsoleApp.tsx`
- `frontend/src/layouts/PlatformLayout.tsx`

## 4. What Changed?
- **`PlatformProtectedRoute.tsx`:** the unauthenticated redirect target `<Navigate to="login" replace />`
  (a *relative* path) is now the absolute `<Navigate to="/console/login" replace />`.
- **`ConsoleApp.tsx`:** added a catch-all `<Route path="*" element={<Navigate to="/console/login" replace />} />`
  inside the console `<Routes>` so any unmatched console path lands on the login screen instead of
  rendering nothing.
- **`PlatformLayout.tsx`:** the "Log out" button now calls a `handleLogout` that runs `logout()` and
  then `navigate('/console/login', { replace: true })`, giving a deterministic redirect that does
  not depend on a re-render race in `PlatformProtectedRoute`.

## 5. Why It Changed?
`ConsoleApp` mounts its `<Routes>` under the app-level `/console/*` route. When the operator was on
e.g. `/console/restaurants/5` and hit "Log out", `logout()` cleared the platform auth store; the
tree re-rendered and `PlatformProtectedRoute` returned `<Navigate to="login">`. React Router
resolves a relative `to` against the current location, producing `/console/restaurants/5/login`,
which matches no route in `ConsoleApp` (it has no catch-all) — so the outlet rendered nothing and
the browser showed a blank page with `/login` tacked onto the URL. Making the redirect absolute,
adding the console-level catch-all, and having the logout handler navigate explicitly all converge
on the same correct destination (`/console/login`) regardless of where logout is triggered from.

## 6. Verification
- `cd frontend && pnpm run build` — PASS (0 TS errors, built in 4.49s).
</content>
</invoke>
