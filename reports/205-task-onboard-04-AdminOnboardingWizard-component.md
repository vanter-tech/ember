# Report 205

## Identification
- **Report Number:** 205
- **Task ID:** onboard-04 (Restaurant Onboarding plan, Task 4)
- **Predecessor Task:** onboard-03 (report 204)

## Objective
Build the self-contained 4-screen admin onboarding wizard (welcome → business name → table
count → done), saving through the existing `PUT /settings` endpoint.

## Modified Files
- `frontend/src/components/onboarding/AdminOnboardingWizard.tsx` (new)
- `frontend/src/components/onboarding/AdminOnboardingWizard.test.tsx` (new)

## What Changed?
`AdminOnboardingWizard` is a props-free component with local `WizardStep` state
(`welcome`/`businessName`/`tables`/`done`). It reads the existing `['restaurantSettings']` query
and drives one `useMutation` wrapping `SettingsService.updateSettings`: the business-name screen
saves `{ ...settings, branding: { ...settings.branding, businessName } }` then advances to
`tables`; the tables screen saves `{ ...settings, space: { totalTables } }` then advances to
`done`. A failed save shows `onboardingSaveErrorMessage` inline and does not advance. Each
`Input`/`Label` pair uses matching `id`/`htmlFor` (`onboarding-business-name`,
`onboarding-total-tables`), the same wiring `SpaceSettings.tsx` already uses for
`getByLabelText` to resolve. 2 tests cover the full happy path (both saves fire with the right
payload, ends on the "done" screen) and the inline-error/no-advance case.

## Why It Changed?
Fourth task of the restaurant-onboarding plan
(`docs/superpowers/plans/2026-08-24-restaurant-onboarding.md`) — this is the component
`AdminLayout` (onboard-05) will render instead of `<Outlet/>` while
`useOnboardingGate().needsOnboarding` is true. No new backend endpoint: reuses the same
`GET`/`PUT /settings` calls `BrandingSettings`/`SpaceSettings` already make, so a restaurant that
finishes the wizard is indistinguishable from one configured by hand through Configuración.

## Verification
- `pnpm vitest run src/components/onboarding/AdminOnboardingWizard.test.tsx` — 2/2 PASS
- `pnpm run build` (`tsc -b && vite build`) — PASS
