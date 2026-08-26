# Report 247 — Task 9: Hide "Registrarse" on the Hub build

## 1. Identification
- **Report Number:** 247
- **Task ID:** Task 9: Frontend — hide "Registrarse" on the Hub build
- **Predecessor Task:** report 246 (Task 8: console-license-button)

## 2. Objective
Hide the "Registrarse" (customer self-registration) link on the Login page specifically for the Hub build. The Hub's admin is pre-provisioned via `HubProvisioningRunner` (Task 6), so self-registration is a customer-only flow (join-table/collaborative cart) and misleading as an entry point for the Hub's own admin user. The signal to distinguish the Hub build is `import.meta.env.BASE_URL !== '/'` — only the Hub build (via `pnpm run build:hub` with `--base=/app/`) has a non-`/` BASE_URL.

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?

Added a module-level constant after imports, before `createLoginSchema`:

```tsx
const isHubBuild = import.meta.env.BASE_URL !== '/'
```

This constant signals whether the app is running in Hub mode (BASE_URL is `/app/`) or standard mode (BASE_URL is `/`).

Wrapped the existing "Registrarse" button (the registration link button) with a conditional:

```tsx
{!isHubBuild && (
  <Button asChild variant="outline" className="w-full text-center mb-3">
    <Link to="/register">{tAuth('registerLink')}</Link>
  </Button>
)}
```

The button now only renders when `isHubBuild` is `false` (i.e., in standard deployments). In the Hub build, the button is completely hidden.

## 5. Why It Changed?

The Hub build (`build:hub`) compiles with `--base=/app/`, setting `import.meta.env.BASE_URL` to `/app/` instead of `/`. This value is already used elsewhere in the codebase (e.g., `App.tsx`'s router basename) to detect the Hub variant.

The "Registrarse" link leads to the customer self-registration flow, which is only relevant for the standard multi-tenant deployment where customers join tables via QR codes. In the Hub, the admin user is pre-provisioned at activation time (Task 6: `HubProvisioningRunner` seeds the local Restaurant + admin User), so displaying a self-registration button on the Hub's login screen is misleading and confusing for the pre-provisioned admin user.

## 6. Build & Test Results

All verification commands completed successfully:

- `pnpm run build` (regular frontend): PASS
  - The default `BASE_URL` remains `/`, so `isHubBuild` is `false`
  - The "Registrarse" button is rendered as before — standard deployments unaffected
  
- `pnpm run build:hub` (Hub variant): PASS
  - `BASE_URL` is `/app/`, so `isHubBuild` is `true`
  - The "Registrarse" button is conditionally hidden — Hub build hides it as intended
  
- `pnpm run test:run` (frontend test suite): PASS
  - Test Files: 11 passed
  - Tests: 36 passed (no new tests required — no existing test covers `Login.tsx`'s register link)
  - Duration: ~57s

No TypeScript compilation errors. No linter failures.
