# Report 122 — Task EMB-CR-04: Physical Payments Require an Open Cash Shift

**Predecessor Task:** EMB-CR-03 cash shift controller (report 121)

## Objective

Enforce that `PaymentService.registerPhysicalPayment` requires an `OPEN` cash shift and stamps `cashShiftId`/`processedBy`; stamp `processedBy` on `initiateDigitalPayment` too. Final task of the backend track (EMB-CR-01 → 04), per `docs/superpowers/plans/2026-08-16-cash-register-shift-management.md` Task 4.

## Modified Files

- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java`

## What Changed

- `registerPhysicalPayment` now takes a 4th `processedByEmail` parameter, locks the tenant's open shift via `cashShiftRepository.findOpenForUpdate(TenantContextHolder.requireTenantId())` before anything else (409 `IllegalStateException` if none), and stamps `cashShiftId`/`processedBy` on the saved `Payment`.
- `initiateDigitalPayment` also takes `processedByEmail` and stamps `processedBy` (no shift check — DIGITAL is unaffected by the till).
- `BillingController`'s two endpoints now take `Authentication` and pass `authentication.getName()` through.
- `E2EOrderFlowTest` gets a new step — the waiter opens a cash shift (`POST /cash-shifts/open`) before registering the physical payment — reflecting the new real-world precondition.

## Self-Review Findings (fixed)

1. **`BillingControllerTest` compile failure**: the plan only called out `PaymentServiceTest`'s Mockito arg-count mismatch; `BillingControllerTest`'s `when(paymentService.registerPhysicalPayment(...))`/`initiateDigitalPayment(...)` stubs needed the same 4th `anyString()` matcher, and their `@WithMockUser` annotations needed `username = "..."` so `authentication.getName()` resolves. Fixed all three affected tests.
2. **`PaymentServiceTest` — same `TenantContextHolder`-unbound bug class as Tasks 2/3**: `registerPhysicalPayment` now calls `TenantContextHolder.requireTenantId()` first; with no tenant bound, every `registerPhysicalPayment_*` test threw `IllegalStateException("No tenant bound...")` before reaching the `cashShiftRepository`/`billRepository` stubs — passing some assertions for the wrong reason and failing others outright, with Mockito's strict stubbing catching the unused-stub cases. Fixed with a class-level `@BeforeEach TenantContextHolder.setTenantId(TENANT_ID)` / `@AfterEach clear()`, matching `AnalyticsControllerTest`'s established pattern — simpler than threading `tenantId` through every test individually.
3. **Full-suite regression**: `E2EOrderFlowTest` (a real end-to-end HTTP flow, not a mock) failed at the physical-payment step with 409 once the shift-gate went live — correctly, since no shift was open in that flow. Fixed by inserting the open-shift step (not by loosening the gate) — this is the E2E test needing to catch up to the new real requirement, not a bug in the requirement.

## Verification

- `cd backend && ./mvnw test -Dtest=PaymentServiceTest,BillingControllerTest` — RED (compile failures, both files) → fixed signatures/stubs → RED again (`TenantContextHolder` errors + 1 `UnnecessaryStubbing`) → fixed → GREEN: `PaymentServiceTest` 17/17, `BillingControllerTest` 14/14.
- `cd backend && ./mvnw test -Dtest=E2EOrderFlowTest` — RED (409 at the payment step) → added the open-shift step → GREEN: 1/1.
- `cd backend && ./mvnw test` (full suite): `Tests run: 623, Failures: 0, Errors: 0` — no regressions.

## Backend Track Complete

This closes out EMB-CR-01 through EMB-CR-04 (reports 117, 119, 121, 122). Handed off to the orchestrator for a consolidated `PROGRESS.md` update and the final whole-branch review once the frontend track also reports done.
