# Report 482

## 1. Identification
- **Report Number:** 482
- **Task ID:** PLAN-GATING-PHASE1 Task 9 — Console: change a tenant's plan after creation, audited
- **Predecessor Task:** report 481 (Console: assign plan at creation)

## 2. Objective
Let a platform operator change an existing tenant's plan from the Console, audited the same way status changes already are, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 9). Before this task, a plan could only ever be set once, at creation.

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantPlanUpdateRequest.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`
- Modify: `frontend/src/lib/platformApi.ts`
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`

## 4. What Changed?
New `PlatformRestaurantPlanUpdateRequest` (`@NotNull plan`) mirrors `PlatformRestaurantStatusUpdateRequest` exactly. `PlatformRestaurantService.updatePlan(restaurantId, newPlan, operatorEmail)` mirrors `updateStatus` line for line: resolves the operator, loads the restaurant (404 if missing), delegates the actual field change to `RestaurantService.updatePlan` (kept from before — only the caller changed), and writes a `PlatformAuditLog` row (`action: "RESTAURANT_PLAN_UPDATED"`, `oldValue`/`newValue` = plan names) in the same transaction. New `PATCH /platform/restaurants/{id}/plan` controller endpoint. `ConsoleRestaurantDetail.tsx`'s previously read-only "Plan" field is now a `<Select>` wired to a `changePlan` mutation (same shape as the existing `toggleStatus` mutation for status).

## 5. Why It Changed?
Ninth task of PLAN-GATING-PHASE1. This closes the gap Task 8 left open — a tenant's plan could be set at creation but never changed afterward. It also lands **before** Task 10 removes the tenant self-service endpoint, so there is never a window where a tenant's plan cannot be changed by anyone.

## 6. Verification
- TDD: `updatePlan_updatesRestaurantAndWritesAuditLog`/`updatePlan_throwsWhenRestaurantNotFound`/`updatePlan_throwsWhenOperatorNotFound` (service) and `updatePlan_returns401WithoutAuthHeader`/`updatePlan_returns400OnMissingPlan`/`updatePlan_returns200WithUpdatedSummary`/`updatePlan_returns404WhenRestaurantNotFound` (controller) all written first; confirmed RED (compile error for the service tests — `updatePlan` didn't exist; 404 for the controller tests — no route mapped), GREEN after wiring.
- `./mvnw test` (full backend suite) — **1303/1303** (+7 new: 3 service + 4 controller).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142** (`ConsoleRestaurantDetail.test.tsx` unaffected — it didn't assert on the old read-only plan text).
- No live browser check — worth confirming next time the Console is exercised live that changing the plan actually persists and shows up in the audit log table below.
