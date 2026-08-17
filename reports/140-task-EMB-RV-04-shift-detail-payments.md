# Report 140 — Task EMB-RV-04: Cash Shift Detail Gains Payments

## Identification
- **Report:** 140
- **Task ID:** EMB-RV-04
- **Predecessor Task:** EMB-RV-03 (report 139)

## Objective
Surface a cash shift's physical payments (with their refund status) on
`GET /cash-shifts/{id}` so a waiter/admin looking at a shift's detail can see and act on
payments made during it, without a separate lookup call.

## Modified Files
- Modified: `backend/src/main/java/com/vanter/ember/cashregister/dto/CashShiftDetailResponse.java`
- Modified: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Modified: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`

## What Changed?
- `CashShiftDetailResponse` gains a third field, `List<PaymentResponse> payments` — a
  backward-incompatible record signature change (all three constructor args are required; no
  other place in the codebase constructs this record besides `CashShiftService.getDetail`, so
  nothing else needed updating).
- `CashShiftService` gained a `PaymentService` dependency and now looks up
  `paymentRepository.findByCashShiftId(id)` inside `getDetail`, converting the result to
  `PaymentResponse`s via `PaymentService.toResponses` (added in Task 2) — reusing the exact same
  refund-aware projection the billing module's own `GET /billing/bills/{id}/payments` endpoint
  uses, so a payment's `refundedAmount`/`remaining` reads identically whether it's viewed from
  the bill side or the shift side.
- `CashShiftServiceTest` gained a `PaymentService` mock and one new test,
  `getDetail_includesPaymentsForTheShift`, asserting the response's `payments()` list is
  populated and that a payment's `participantName` survives the round trip through the mock.

## Why It Changed?
This is the plan's "cash-shift-detail extension" — an admin reviewing a shift's Z-report/detail
page needs to see which payments landed in it, not just cash movements, since Task 3's refund
flow now happens against a payment that may be tied to any shift (not necessarily the one
currently open). Reusing `PaymentService.toResponses` rather than hand-rolling a second
projection in `CashShiftService` keeps the refund-status computation (`refundedAmount`/
`remaining`) in exactly one place.

This is also the dependency direction the plan's Global Constraints section calls out by name:
`CashShiftService` now depends on `PaymentService`, and `PaymentService` (Task 2) deliberately
does **not** depend back on `CashShiftService` — it builds its `CashMovement` directly via
`CashMovementRepository` instead of calling `CashShiftService.recordMovement`, specifically so
this task's new dependency wouldn't create a Spring bean cycle. Running the full suite after this
change (which boots the full `ApplicationContext` in several `@SpringBootTest`-annotated test
classes) is this task's own tripwire for that concern, and it stayed green.

## TDD Evidence

**RED** — `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`, run after adding the
`getDetail_includesPaymentsForTheShift` test but before `CashShiftDetailResponse`/
`CashShiftService.getDetail` were changed:
```
[ERROR] .../CashShiftServiceTest.java:[162,26] cannot find symbol
  symbol:   method payments()
  location: variable detail of type com.vanter.ember.cashregister.dto.CashShiftDetailResponse
[INFO] BUILD FAILURE
```

**GREEN** — `cd backend && ./mvnw test -Dtest=CashShiftServiceTest`, run after extending the
record and wiring `PaymentService` into `getDetail`:
```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Full suite** — `cd backend && ./mvnw test`:
```
[INFO] Tests run: 680, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
(679 after Task 3 + 1 new `getDetail_includesPaymentsForTheShift` = 680. Full-context
`@SpringBootTest` classes — `SecurityAuditTest` and others — passing confirms no circular-bean
startup failure from `CashShiftService` -> `PaymentService`.)

## Self-Review
- **Completeness:** Task's only "Files" entries (`CashShiftDetailResponse`, `CashShiftService`,
  `CashShiftServiceTest`) are exactly what was touched.
- **Quality:** No duplicate refund-status computation — `getDetail` reuses
  `PaymentService.toResponses` rather than reimplementing the `refundedAmount`/`remaining` math.
- **Discipline:** Did not add a controller-level change (`CashShiftController.detail` already
  just returns whatever `getDetail` produces, so no controller edit was needed or made).
- **Testing:** The new test verifies the actual field population end-to-end through the mocked
  collaborators, not just that the method returns without throwing.
