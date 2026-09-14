# Report 476

## 1. Identification
- **Report Number:** 476
- **Task ID:** PLAN-GATING-PHASE1 Task 3 — gate: cash register open (STARTER+)
- **Predecessor Task:** report 475 (tables gate)

## 2. Objective
Gate the cash-register feature (open/close/arqueo) behind STARTER+, per the approved plan (`docs/superpowers/plans/2026-09-14-plan-gating-phase1.md`, Task 3). Gating only `open` is sufficient — `recordMovement`/`close`/`prolong` are unreachable without an open shift.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerProlongTest.java` (needed a `@MockBean PlanGateService` too — it stands up the same `@WebMvcTest(CashShiftController.class)` slice)
- Modify: `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java` (unplanned — see below)
- Modify: `frontend/src/pages/accountant/cashRegister/components/OpenShiftDialog.tsx`

## 4. What Changed?
`CashShiftController.open` now resolves the tenant id once, calls `planGateService.requirePlanAtLeast(tenantId, RestaurantPlan.STARTER, "cashclose")` before doing anything else, then proceeds unchanged. `OpenShiftDialog.tsx`'s mutation shows the shared plan-gate toast on a 402 instead of the generic "couldn't open shift" toast.

**Unplanned fix:** the full backend suite caught a real regression the plan didn't anticipate — `E2EOrderFlowTest` seeds its tenant with a plain `Restaurant.builder()` (defaults to `FREE`) and opens a cash shift as step 7b of its flow; that step started failing with 402 instead of 201. Fixed by giving the E2E test's restaurant an explicit `.plan(RestaurantPlan.STARTER)` — the test is deliberately exercising the whole paid flow (billing, split, physical payment), so STARTER is the correct fixture, not a workaround.

## 5. Why It Changed?
Third task of PLAN-GATING-PHASE1. The E2E fix was required, not optional — it's a pre-existing test whose fixture assumption (unlimited plan) broke the moment gating had real teeth, exactly the kind of thing full-suite verification exists to catch.

## 6. Verification
- TDD: `open_blockedWhenPlanBelowStarter` written first, confirmed RED (got 404 from an unrelated unmocked `resolveUserId` path rather than never reaching the gate — confirms the gate wasn't there yet), GREEN after wiring.
- `./mvnw test` (full backend suite) — **1286/1286** (+1 net: the new blocked-test, offset by no other count change). Caught and fixed the `E2EOrderFlowTest` regression via full-suite run, not just the targeted test file.
- `pnpm run build` — clean. `pnpm run lint` — 0 errors. `pnpm vitest run` — **139/139**.
- No live browser check.
