# Report 203

## Identification
- **Report Number:** 203
- **Task ID:** onboard-02 (Restaurant Onboarding plan, Task 2)
- **Predecessor Task:** onboard-01 (report 202)

## Objective
Add `useOnboardingGate`, a hook that derives whether a tenant still needs the admin onboarding
wizard, from the existing `restaurantSettings` query — no new backend endpoint or stored flag.

## Modified Files
- `frontend/src/hooks/useOnboardingGate.ts` (new)
- `frontend/src/hooks/useOnboardingGate.test.tsx` (new)

## What Changed?
`useOnboardingGate` wraps `useQuery(['restaurantSettings'], SettingsService.getSettings)` and
returns `{ needsOnboarding, isLoading, isError }`. `needsOnboarding` is `true` only once the query
has settled successfully and either `branding.businessName` is blank/whitespace or
`space.totalTables` is falsy (0/undefined); it stays `false` while loading and on a failed fetch.
4 unit tests cover: blank business name, zero tables, both set (false), and a rejected fetch
(false, `isError` true).

## Why It Changed?
First consuming piece of the restaurant-onboarding plan's gating logic (plan Task 2,
`docs/superpowers/plans/2026-08-24-restaurant-onboarding.md`) — `AdminLayout` (onboard-05) will
use this hook to decide whether to render the wizard instead of `<Outlet/>`. Deriving the gate
from the query already fetched elsewhere avoids a new endpoint/column, and defaulting to `false`
on error prevents a transient network failure from locking an admin out behind the wizard.

## Verification
- `pnpm vitest run src/hooks/useOnboardingGate.test.tsx` — 4/4 PASS
- `pnpm run build` (`tsc -b && vite build`) — PASS
