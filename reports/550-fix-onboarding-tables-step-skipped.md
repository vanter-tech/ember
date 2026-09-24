# Report 550 — FIX-ONBOARDING-TABLES-STEP-SKIPPED

## 1. Identification
- **Report number:** 550
- **Task ID:** FIX-ONBOARDING-TABLES-STEP-SKIPPED
- **Predecessor:** report 549 (EMPTY-STATES-FIRST-RUN)

## 2. Objective
On a new tenant's first admin login, the onboarding wizard must show welcome → business name → number of tables → done, then land the admin on analytics. The tables step was being skipped straight to the menu.

## 3. Modified Files
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/components/onboarding/AdminOnboardingWizard.tsx`
- `frontend/src/layouts/AdminLayout.test.tsx`

## 4. What Changed?
- `AdminLayout` latches `wizardActive` the first time `needsOnboarding` is true and keeps rendering `AdminOnboardingWizard` until the wizard calls its new `onFinish` prop.
- `AdminOnboardingWizard` takes an optional `onFinish`; the `done` step's button is now a `Link` to `/admin/analytics` (was `/admin/settings`) that calls `onFinish`.
- New regression test in `AdminLayout.test.tsx`: with backend default `totalTables: 10`, saving the name still shows the tables step, then `done`, then lands on analytics.

## 5. Why It Changed?
Root cause: `SettingsPayload.SpaceSettings.TotalTables` defaults to `10` in the backend, so `useOnboardingGate` (`!businessName || !totalTables`) only depended on the business name. Saving the name invalidated `restaurantSettings`, `needsOnboarding` flipped to false, and `AdminLayout` unmounted the wizard before the tables step rendered. The latch makes the wizard's own completion, not the gate, decide when it closes.

Verification: `pnpm run build` clean, `pnpm run lint` 0 errors, `test:run` 213/214. The 1 failure (`MenuJoin.test.tsx` "authenticated: submitting the name joins via the QR token") is pre-existing: it fails identically on a clean tree with these changes stashed.
