# Report 483

## 1. Identification
- **Report Number:** 483
- **Task ID:** PLAN-GATING-PHASE1 Task 10 — remove tenant self-service plan endpoint; regen `backend-types.ts` (**final task of the phase**)
- **Predecessor Task:** report 482 (Console: change plan after creation)

## 2. Objective
Remove the unpaid tenant self-service `PATCH /admin/restaurant/plan` — with the Console now able to assign (Task 8) and change (Task 9) a tenant's plan, this endpoint's only remaining use was letting any tenant ADMIN grant themselves any plan for free, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 10). **This is the last task of PLAN-GATING-PHASE1.**

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/controller/RestaurantAdminController.java`
- Delete: `backend/src/main/java/com/vanter/ember/restaurant/model/dto/UpdateRestaurantPlanRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- Modify: `backend/src/test/java/com/vanter/ember/restaurant/controller/RestaurantAdminControllerTest.java`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/backend-types.ts`

## 4. What Changed?
`RestaurantAdminController` is now GET-only (`GET /admin/restaurant`, unchanged — a tenant ADMIN can still see their own plan, read-only). Its `updatePlan` method and the `UpdateRestaurantPlanRequest` DTO are deleted. `RestaurantAdminControllerTest`'s 3 `updatePlan_*` tests are removed (this is a pure deletion, not a red/green cycle — there's no new behavior, only removal of an unpaid privilege-escalation path). `RestaurantService.updatePlan`'s doc comment now points at the actual (platform-operator-only) caller instead of the stale "self-service" description. `frontend/src/lib/api.ts`'s `restaurantAdminService.updatePlan` and the `UpdateRestaurantPlanRequest` type alias are deleted; `.getPlan` is kept.

**`backend-types.ts` regeneration — hand-patched, not `pnpm run openapi`:** attempted a live regeneration first (started the backend, confirmed `/v1/v3/api-docs` responding, ran `pnpm run openapi`), but the diff it produced was enormous (2206 insertions / 877 deletions) and missing Task 9's brand-new `/platform/restaurants/{id}/plan` endpoint entirely while still containing the just-deleted `/admin/restaurant/plan` — clear signs it hit a stale backend instance on port 8080 (not code freshly compiled from this session's changes), not this session's own code. Reverted that regeneration and hand-patched instead, same fallback the plan itself allowed: renamed the `UpdateRestaurantPlanRequest` schema to `PlatformRestaurantPlanUpdateRequest`, moved the path entry from `/admin/restaurant/plan` to a new `/platform/restaurants/{id}/plan` sibling of the existing `/status` entry, and updated the `updatePlan` operation's shape (path `id` param, `PlatformRestaurantPlanUpdateRequest` body, `PlatformRestaurantSummaryResponse` response) to match what Task 9 actually built.

## 5. Why It Changed?
Tenth and final task of PLAN-GATING-PHASE1. Ordered last on purpose (per both the spec and the plan) — Tasks 8-9 built the Console's full replacement path first, so there was never a window where a tenant's plan could not be changed by anyone.

## 6. Verification
- `RestaurantAdminControllerTest` re-run standalone after the removal — **3/3** (the 3 remaining `get_*` tests), confirming no regression from the deletion.
- `./mvnw test` (full backend suite) — **1300/1300** (-3 net from the removed tests, no other change in count).
- `pnpm run build` — clean (confirms the hand-patched `backend-types.ts` is valid TypeScript and nothing in the app still references the removed types/paths). `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142**.
- Grepped the whole frontend source for `UpdateRestaurantPlanRequest`/`/admin/restaurant/plan` post-change — zero hits outside the (now-corrected) generated file.
- No live browser check.

## 7. Phase Summary
**PLAN-GATING-PHASE1 is complete — all 10 tasks done (reports 474-483).** `Restaurant.plan` now actually gates 6 features (table count, cash register, analytics period filters, export, branding, extra staff roles), the Console can assign and change a tenant's plan, and the unpaid self-service escape hatch is gone. Out of scope for this phase (per the spec's §9, unchanged): the 4 "spread" features (floor/table management, bill splitting, printing, staff CRUD as a whole) and the 6 marketing-only rows that don't exist as features yet (rooms, a distinct "advanced analytics" tier, a waiter-count limit, multi-branch, integrations, SLA/AM/support tiers).
