# Report 211: Onboarding gate stayed frozen after the wizard's own save succeeded

**Predecessor:** report 210

## Objective
Fix the "Ir al panel" (finish) button appearing to do nothing after completing the admin
onboarding wizard end-to-end.

## Modified Files
- `frontend/src/hooks/useOnboardingGate.ts`
- `frontend/src/hooks/useOnboardingGate.test.tsx`

## What Changed?
`useOnboardingGate` no longer freezes its result on the *first* settle regardless of outcome. It
now only freezes (permanently, for the life of the mounted `AdminLayout`) once the settings query
has been observed in an **error** state. A successful settle stays fully live — `needsOnboarding`
recomputes on every render from the query's current data. Added a regression test asserting
`needsOnboarding` flips from `true` to `false` live once `queryClient.invalidateQueries` resolves
with fresh, complete settings (the exact sequence the wizard's own save mutation triggers).

## Why It Changed?
Report 206 froze the gate's result on the first settle to stop an infinite TopNav mount/refetch
loop. That loop's precondition, per report 206's own analysis, was specifically an **errored**
`restaurantSettings` query: react-query only resets `status` from `error` back to `pending` when a
new observer joins and retries — a *successful* query's `status` stays `success` through
background refetches (that's the entire point of v5 splitting `isPending`/`isFetching`), so it
can never exhibit that ping-pong.

The blanket freeze was broader than the bug it fixed: it also locked in `needsOnboarding: true`
forever, even after the wizard's `saveTables` mutation succeeded and called
`queryClient.invalidateQueries(['restaurantSettings'])`. `AdminLayout` kept rendering
`AdminOnboardingWizard` — including its own already-visible "done" screen — regardless of the
`<Link to="/admin/settings">` navigation, because the gate's `needsOnboarding` never got the chance
to re-evaluate to `false`. Visually this looked exactly like the finish button "doing nothing."

Narrowing the freeze to the error case only preserves report 206's fix (confirmed no unit test for
that scenario regressed) while letting the intended completion transition work.

## Verification
`cd frontend && pnpm run test:run` — 25/25 PASS (new live-transition test + all 4 existing
`useOnboardingGate`/`AdminLayout` tests, including the error-freeze one, still green).
`cd frontend && pnpm run build` — PASS.
Manually re-verified in-browser by the user against a fresh platform-console restaurant: wizard now
completes and "Ir al panel" correctly lands on the normal admin dashboard.
