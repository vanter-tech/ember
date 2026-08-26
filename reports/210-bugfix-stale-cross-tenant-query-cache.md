# Report 210: Stale cross-tenant/session query cache on login

**Predecessor:** report 209 (task-onboard-08)

## Objective
Fix onboard-09's manual-verification finding: the admin onboarding wizard (report 206) never
appeared for a genuinely fresh restaurant when the browser tab had previously loaded any other
tenant/user's `restaurantSettings` in the same session.

## Modified Files
- `frontend/src/store/authStore.ts`
- `frontend/src/store/authStore.test.ts` (new)

## What Changed?
`authStore`'s `setAuth` and `logout` actions now call `queryClient.clear()` before/while updating
auth state. Added `authStore.test.ts` (2 tests) asserting both actions clear the `['restaurantSettings']`
cache entry.

## Why It Changed?
`frontend/src/queryClient.ts` exports a single app-wide `QueryClient` instance. Neither `setAuth`
nor `logout` ever cleared it, so switching identity within one browser tab (no full reload) left
the previous tenant's cached `['restaurantSettings']` response in place. React Query serves cached
data instantly on mount (`isPending: false` from the first render), so `useOnboardingGate` computed
`needsOnboarding` from the *old* tenant's already-configured settings and (per report 206's freeze-
on-first-settle logic) locked that wrong answer in permanently — the wizard never got a chance to
show for the new, genuinely-unconfigured restaurant.

Confirmed root cause with a failing regression test (`AdminLayout.test.tsx`, later removed once the
fix moved to the correct layer — see report 211) that pre-seeded stale cache and showed the wizard
never appearing. Backend was verified correct via direct `curl` calls (`GET /v1/settings` on a fresh
platform-console-created restaurant genuinely returns `businessName: null`).

Fix follows TanStack Query's own recommended pattern: clear the query cache on every
authentication/identity transition, not just an explicit logout click, since `setAuth` is the single
choke point for login, register, and customer session-join token swaps alike.

## Verification
`cd frontend && pnpm run test:run` — 25/25 PASS (includes new `authStore.test.ts`).
`cd frontend && pnpm run build` — PASS.
