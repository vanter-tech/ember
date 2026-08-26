# Report 207 — Task onboard-06: `waiterTourStore`

## 1. Identification
- **Report:** 207
- **Task ID:** onboard-06 (restaurant-onboarding plan, Task 6)
- **Predecessor Task:** onboard-05 (report 206)

## 2. Objective
Add a per-user "seen" flag for the waiter tour so it shows exactly once per waiter identity, persisted client-side, matching the existing `localeStore.ts` `persist` pattern.

## 3. Modified Files
- `frontend/src/store/waiterTourStore.ts` (new)
- `frontend/src/store/waiterTourStore.test.ts` (new)

## 4. What Changed?
Added a Zustand `persist` store `useWaiterTourStore` holding `seenByUserId: Record<string, boolean>`, with `hasSeenTour(userId)` and `markTourSeen(userId)`, persisted to `localStorage` under `ember-waiter-tour-storage`. 2 tests: default-false for an unseen user, and per-user isolation after `markTourSeen` (a second user's flag stays false). Test written first, confirmed failing (module didn't exist), then implementation added and confirmed passing.

## 5. Why It Changed?
Two different waiters can share the same browser/PC, so a single global boolean would wrongly mark the tour "seen" for every waiter once any one of them dismissed it. A persisted per-user map avoids a backend field/endpoint (plan's zero-backend-changes constraint) while still surviving reloads, mirroring the existing `localeStore.ts` `persist` convention exactly.

## Verification
- `pnpm vitest run src/store/waiterTourStore.test.ts` — 2/2 PASS
- `cd frontend && pnpm run build` (`tsc -b && vite build`) — PASS
