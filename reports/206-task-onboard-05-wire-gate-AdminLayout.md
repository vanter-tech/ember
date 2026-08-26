# Report 206

## Identification
- **Report Number:** 206
- **Task ID:** onboard-05 (Restaurant Onboarding plan, Task 5)
- **Predecessor Task:** onboard-04 (report 205)

## Objective
Wire `useOnboardingGate` into `AdminLayout` so it renders `AdminOnboardingWizard` instead of the
normal admin shell (`TopNav`/`Outlet`/`FloatingNav`) whenever the tenant hasn't finished setup.

## Modified Files
- `frontend/src/layouts/AdminLayout.tsx`
- `frontend/src/layouts/AdminLayout.test.tsx` (new)
- `frontend/src/hooks/useOnboardingGate.ts` (bugfix, not in the plan's file list — see below)

## What Changed?
`AdminLayout` now calls `useOnboardingGate()`: returns `null` while `isLoading`, renders
`<AdminOnboardingWizard/>` while `needsOnboarding`, otherwise the existing shell unchanged. 3
tests cover wizard-shown/incomplete, normal-route/complete, and normal-route-on-fetch-error.

**Bugfix found while verifying this task (not a separate task, fixed inline since Task 5's own
test plan exercises exactly this path):** the plan's 3rd test case — render the real
`AdminLayout` (real `TopNav`) with a rejected `getSettings()` — hung/looped indefinitely instead
of settling. Root cause: `TopNav` runs its own separate `useQuery(['restaurantSettings'])`
(`settingStore.ts`), and it only mounts once `useOnboardingGate`'s `isLoading` goes false. A fresh
observer joining an already-errored, always-stale (`staleTime` 0) query triggers a background
refetch; that refetch flips the shared query's `status` back to `'pending'` mid-flight (confirmed
via a reduced repro: two `useQuery` observers on the same key, one mounted only after the other
resolves, produced 1000+ fetches in under a second — a single shared observer, or two observers
mounted simultaneously up front, does not reproduce it). Without a fix, that flip cascades:
`isLoading` flips back true → `AdminLayout` unmounts `TopNav` → its observer detaches → the next
settle flips `isLoading` false again → `AdminLayout` remounts `TopNav` → triggers another
refetch — an infinite mount/refetch loop that would also fire in production on any real
`GET /settings` failure. Fixed in `useOnboardingGate.ts`: the returned `{needsOnboarding, isError}`
now freezes (via `useRef`) the first time the query settles and never recomputes after that,
regardless of later background refetches triggered by sibling components sharing the same query
key. The 4 existing unit tests for the hook (report 203) still pass unchanged — single-observer,
single-settle scenarios behave identically before and after.

Also removed an unused `waitFor` import from `AdminLayout.test.tsx` (present in the plan's literal
test snippet but never called) — `tsc -b`'s `noUnusedLocals`-equivalent check failed on it.

## Why It Changed?
Fifth task of the restaurant-onboarding plan
(`docs/superpowers/plans/2026-08-24-restaurant-onboarding.md`) — this is what actually turns the
gate on for real admin traffic. The freeze fix belongs in `useOnboardingGate` rather than
`AdminLayout` or `settingStore` because it's the one place that owns the "gate decision" and is
resilient regardless of which other component ends up sharing the `restaurantSettings` query key
in the future.

## Verification
- `pnpm vitest run src/layouts/AdminLayout.test.tsx` — 3/3 PASS
- `pnpm vitest run src/hooks/useOnboardingGate.test.tsx` — 4/4 PASS (no regression)
- `pnpm run test:run` (full suite) — 16/16 PASS
- `pnpm run build` (`tsc -b && vite build`) — PASS
