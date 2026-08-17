# Report 139 — Task EMB-RV-03: Void/Refund Endpoints

## Identification
- **Report:** 139
- **Task ID:** EMB-RV-03
- **Predecessor Task:** EMB-RV-02 (report 138)

## Objective
Expose Task 2's `BillingService.voidBill` and `PaymentService.refundPayment`/`listPayments`/
`listRefunds` as HTTP endpoints on `BillingController`, with request validation DTOs, and extend
`SecurityAuditTest`'s 401 matrix to cover the new routes.

## Modified Files
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/VoidBillRequest.java`
- Created: `backend/src/main/java/com/vanter/ember/billing/dto/RefundPaymentRequest.java`
- Modified: `backend/src/main/java/com/vanter/ember/billing/controller/BillingController.java`
- Modified: `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- Modified: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## What Changed?
- `VoidBillRequest(@NotBlank String reason)` and `RefundPaymentRequest(@Positive BigDecimal
  amount, @NotBlank String reason)` — `amount` is deliberately not `@NotNull`: Bean Validation
  treats `null` as valid for `@Positive`, so a null `amount` still reaches
  `PaymentService.refundPayment`, which treats it as "refund the full remaining balance".
- `BillingController` gains four endpoints, all under the existing prefix-less `/billing` base
  path:
  - `POST /billing/bills/{id}/void` — WAITER-only, `Bill` response.
  - `GET /billing/bills/{id}/payments` — WAITER/ADMIN, `List<PaymentResponse>`.
  - `POST /billing/payments/{id}/refund` — WAITER-only, `201 Created`, `Refund` response.
  - `GET /billing/payments/{id}/refunds` — WAITER/ADMIN, `List<RefundResponse>`.
- `SecurityAuditTest`'s `@CsvSource` gains 4 rows for the new routes, inserted immediately after
  the existing `POST, /api/billing/payments/1/confirm` row and using the real prefix-less
  `/billing/...` path (matching the `/cash-shifts/...` rows below, not the stale `/api/billing/...`
  rows above — this repo's existing, intentional inconsistency, not something this task fixes).

## Why It Changed?
This is the HTTP surface Task 6 (frontend) will call. The read endpoints (`listPayments`/
`listRefunds`) are WAITER **and** ADMIN, matching the plan's "WAITER executes, ADMIN reviews"
framing — voiding/refunding stays WAITER-only (no ADMIN-approval step, per the plan's Global
Constraints), but ADMIN can look at what happened. `refundPayment` returns `201 Created` (a new
`Refund` row is created) while `voidBill` returns `200 OK` (an existing `Bill` is mutated
in place), matching this controller's existing convention (`calculateBill`/`registerPhysicalPayment`
are `201`, `confirmDigitalPayment` is the default `200`).

## TDD Evidence

**RED** — `cd backend && ./mvnw test -Dtest=BillingControllerTest`, run after adding the 8 new
test methods but before the DTOs/endpoints existed:
```
[ERROR] .../BillingControllerTest.java:[8,36] cannot find symbol
  symbol:   class RefundPaymentRequest
[ERROR] .../BillingControllerTest.java:[12,36] cannot find symbol
  symbol:   class VoidBillRequest
[INFO] BUILD FAILURE
```

**GREEN** — `cd backend && ./mvnw test -Dtest=BillingControllerTest`, run after implementing the
DTOs and the four controller endpoints:
```
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Full suite** — `cd backend && ./mvnw test`:
```
[INFO] Tests run: 679, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
(667 after Task 2 + 8 new `BillingControllerTest` cases + 4 new `SecurityAuditTest`
parameterized rows = 679.)

## Self-Review
- **Completeness:** All 4 endpoints wired, both new DTOs validate as specified, and the
  `SecurityAuditTest` 401 matrix covers all 4 new routes.
- **Quality:** Endpoints follow the controller's existing thin-pass-through style — no logic
  beyond delegating to the service layer and mapping `Authentication#getName()` to the
  `*ByEmail` service parameters, matching every other WAITER-authenticated endpoint in this file.
- **Discipline:** Base path stayed `/billing` (no `/api` prefix) per the plan's explicit
  constraint; did not "fix" the pre-existing stale `/api/billing/...` rows above the insertion
  point, since that's flagged as out of this plan's scope.
- **Testing:** Both positive (200/201, response-body field assertions) and negative
  (403-forbidden per wrong role) cases covered for every new endpoint.
