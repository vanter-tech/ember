## 1. Identification
- Report: 83
- Task ID: EMB-PC-14
- Predecessor Task: EMB-PC-13 (report 82)

## 2. Objective
Add the frontend console UI for the two remaining `/platform` write endpoints without a client: tenant onboarding (`POST /platform/restaurants`) and operator self password-change (`PATCH /platform/auth/password`).

## 3. Modified Files
- `frontend/src/lib/platformApi.ts`
- `frontend/src/pages/console/ConsoleRestaurantCreate.tsx` (new)
- `frontend/src/pages/console/ConsolePasswordChange.tsx` (new)
- `frontend/src/pages/console/ConsoleApp.tsx`
- `frontend/src/pages/console/ConsoleRestaurants.tsx`
- `frontend/src/layouts/PlatformLayout.tsx`

## 4. What Changed?
- `platformApi.ts`: added `PlatformRestaurantCreateRequest` (mirrors backend DTO: `name`/`slug`/`adminName`/`adminEmail`/`adminPassword`) and `platformRestaurantService.create()` (`POST /platform/restaurants`).
- `ConsoleRestaurantCreate.tsx`: new page, `react-hook-form` + `zod` (same pattern as `ConsoleLogin.tsx`), validation mirrors the backend's slug regex and password complexity `Pattern`. On success invalidates `['platformRestaurants']` and navigates to the new restaurant's detail page; 409 (duplicate slug/email) gets a distinct toast.
- `ConsolePasswordChange.tsx`: new page, same form pattern, calls `platformAuthService.changePassword` (already existed in `platformApi.ts`, previously unused). On success resets the form; 401 (wrong current password) gets a distinct toast.
- `ConsoleApp.tsx`: added guarded sibling routes `restaurants/new` (placed before `restaurants/:id`) and `password`.
- `ConsoleRestaurants.tsx`: added a "Nuevo restaurante" link to `restaurants/new`.
- `PlatformLayout.tsx`: added a "Cambiar contraseña" link in the header, next to Log out.

## 5. Why It Changed?
EMB-PC-08/EMB-PC-05 landed both backend endpoints with no frontend consumer. This closes the console's remaining backlog gap: an operator can now onboard a new tenant end-to-end from the UI and rotate their own password without touching the API directly. `platformApi.ts` keeps its own request/response types, consistent with the rest of the console's independence from the tenant `api.ts` layer (EMB-PC-10/12 decision).

## Verification
`cd frontend && pnpm run build` — `tsc -b && vite build`, exit 0. `ConsoleApp` remains its own code-split chunk (17.25 kB).
