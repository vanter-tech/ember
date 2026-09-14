# Report 480

## 1. Identification
- **Report Number:** 480
- **Task ID:** PLAN-GATING-PHASE1 Task 7 — gate: KITCHEN/ACCOUNTANT staff roles (STARTER+)
- **Predecessor Task:** report 479 (branding gate)

## 2. Objective
Gate creating staff with the KITCHEN or ACCOUNTANT role behind STARTER+ — a FREE tenant may only create WAITER/ADMIN staff — per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 7). **This is the 6th and last of the phase's gates.**

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- Modify: `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- Modify: `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx`

## 4. What Changed?
`UserAdminService.create` now checks `request.role() == Role.KITCHEN || request.role() == Role.ACCOUNTANT` right after the existing CUSTOMER-rejection guard, calling `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "roles")` before touching the email-uniqueness check or the database. WAITER and ADMIN creation is completely unaffected. `CreateStaffModal.tsx`'s submit mutation shows the plan-gate toast on 402.

## 5. Why It Changed?
Seventh task of PLAN-GATING-PHASE1, and the last of the 6 gates from the spec. Remaining tasks (8-10) are Console plan management and removing the tenant self-service endpoint — no more feature gates.

## 6. Verification
- TDD: `create_blockedForKitchenRoleWhenPlanBelowStarter` and `create_blockedForAccountantRoleWhenPlanBelowStarter` written first, confirmed RED (both got `ResourceNotFoundException` instead of the plan exception — proof the gate wasn't reached yet), GREEN after wiring; `create_allowsWaiterRoleWithoutTouchingThePlanGate` added to lock in that WAITER/ADMIN never touch this check.
- `./mvnw test` (full backend suite) — **1294/1294** (+3 new). `UserAdminControllerTest` unaffected (fully mocks `UserAdminService`).
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **142/142**.
- No live browser check.
