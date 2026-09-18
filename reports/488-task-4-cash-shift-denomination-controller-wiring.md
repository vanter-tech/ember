# Report 488

## 1. Identification
- **Report number:** 488
- **Task ID:** CASH-SHIFT-DENOMINATION-COUNT Task 4 — backend `CashShiftController` wiring
- **Predecessor task:** report 487 (hotfix: waiter can't see the open cash shift)

## 2. Objective
Finish Task 4 of the cash-shift-denomination-count plan: verify the controller correctly passes
the denomination breakdown through `openShift`/`closeShift`, and add the coverage test the plan
calls for.

## 3. Modified Files
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`

## 4. What Changed?
Added `open_passesTheBreakdownThrough`: posts `{"openingFloat": 100.00, "breakdown": [{"denominationId": "bill_100", "quantity": 1}]}` to `POST /cash-shifts/open` and verifies `cashShiftService.openShift(...)` is invoked with the deserialized `List.of(new DenominationCount("bill_100", 1))`. Added the supporting imports (`DenominationCount`, `List`, `anyList`).

No production code changes were needed. Inspecting `CashShiftController.java` and both existing test files (`CashShiftControllerTest.java`, `CashShiftControllerProlongTest.java`) confirmed the controller signatures, the `open`/`close` breakdown pass-through, and every positional `CashShiftResponse`/`OpenShiftRequest`/`CloseShiftRequest` call site were already correct — a side effect of Task 3's changes, since Maven compiles the whole test module together and those call sites had to compile then.

## 5. Why It Changed?
Task 3's report had already noted the controller call sites were fixed as a compile-time side effect, leaving Task 4 with only its own new test to add per the plan. Adding it closes the coverage gap the plan specifically asked for (breakdown pass-through, not just DTO shape).

## Verification
- `cd backend && ./mvnw -Dtest=CashShiftControllerTest,CashShiftControllerProlongTest test` → 21/21 pass.
- `cd backend && ./mvnw test` → **1312/1312**, BUILD SUCCESS.
