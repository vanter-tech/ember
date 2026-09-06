# Report 387 — fix(billing): reject EQUAL_PARTS billing without a valid participantCount

## Identification
- **Report:** 387
- **Task:** production bug fix (systematic-debugging) — "pago dividido" (EQUAL_PARTS) bill
  request never reaches any diner.
- **Predecessor:** report 386 (`EmberHub.iss` compile fix).
- **Branch:** `fix/billing-equal-parts-participant-count` off `main`.

## Objective (root cause)
Reported from prod: when the waiter picks "pago dividido" and sends the bill, **nobody** (not
the reporting diner, not the colleague) sees it.

`RequestBillingRequest.participantCount` was `@Min(1)` but **not `@NotNull`**. The waiter client
(`ChargeTableModal`) only sends `participantCount` for the EQUAL_PARTS option, and if the modal
payload lacked it the field was omitted. `BillingController.requestBilling` then coerced the
`null` to `0`:

```java
request.participantCount() == null ? 0 : request.participantCount()
```

`BillingEventListener.handleBillingRequested` (a synchronous `@EventListener`) called
`billingService.splitEqually(bill.getId(), 0)` →

```java
bill.getTotal().divide(BigDecimal.valueOf(0), 2, RoundingMode.FLOOR)   // ArithmeticException: / by zero
```

The exception propagated out of the listener → HTTP 500 to the waiter — **before**
`messagingTemplate.convertAndSend("/topic/session/…", BILL_READY)`, so no diner ever got the
frame. Worse: `calculateBill` runs in its own `@Transactional` and had already committed the
`Bill` row, leaving an **orphan OPEN bill with no splits**. Every later `calculateBill` for that
session then threw `IllegalStateException("Session already billed")` — so "pago por consumo"
stopped working too until the orphan bill was voided.

## Modified Files
- `backend/src/main/java/com/vanter/ember/billing/dto/RequestBillingRequest.java`
- `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `backend/src/test/java/com/vanter/ember/billing/service/BillingServiceTest.java`

## What Changed?
- **`RequestBillingRequest`** — added a cross-field `@AssertTrue`
  (`isParticipantCountValidForMethod`, `@JsonIgnore`): when `splitMethod == EQUAL_PARTS`,
  `participantCount` must be non-null and `>= 1`. An invalid EQUAL_PARTS request is now a **400
  at the controller** — before `publishEvent`, so no `Bill` is created, no divide-by-zero, no
  orphan. BY_CONSUMPTION is unaffected (it ignores the field).
- **`BillingService.splitEqually`** — a leading guard: `if (participantCount < 1) throw new
  IllegalArgumentException(...)`. Defense-in-depth so any other path that reaches this method
  with a non-positive count gets a clean 409 instead of a raw 500.

## Why It Changed?
The confirmed root cause is unvalidated input reaching the split maths as `0`. Fixing it at the
request-DTO boundary stops the failure before any partial state is written; the service guard is
the second layer per the debugging skill's defense-in-depth pattern.

## Not in scope (separate follow-ups)
- **Orphan bill on a *valid* request that fails deeper** (e.g. `participantCount` >
  `session.getParticipants().size()` → `IllegalArgumentException` inside `splitEqually`, after
  `calculateBill` committed): `handleBillingRequested` should be `@Transactional` (or the void
  path made reachable) so a split failure rolls the `Bill` back. Left for its own change.
- **"Bug Y":** the colleague never sees the bill regardless of split method — suspected
  `SessionService.isParticipant` returning `false` for that diner (blocks both the
  `/topic/session/{id}` SUBSCRIBE and the `GET /billing/sessions/{id}/bill` fallback). Still
  needs prod-log confirmation.

## Verification
`cd backend && ./mvnw test` — full suite **1098/1098**. New tests: `requestBilling` rejects
EQUAL_PARTS with a missing count (400), still accepts BY_CONSUMPTION with no count (202);
`splitEqually(_, 0)` throws `IllegalArgumentException`.
