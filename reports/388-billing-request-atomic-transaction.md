# Report 388 — wrap the billing-request handler in a single transaction

## 1. Identification
- **Report:** 388
- **Task ID:** fix/billing-request-atomic (PR #86)
- **Predecessor:** report 387 — fix(billing): require participantCount for EQUAL_PARTS split (PR #85)

## 2. Objective
Make `BillingEventListener.handleBillingRequested` atomic so a failure in the split step
rolls back the `Bill` that `calculateBill` already committed, instead of leaving an orphan
that blocks every later "calculate bill" for the session.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/listener/BillingEventListener.java`
- `backend/src/test/java/com/vanter/ember/billing/listener/BillingEventListenerTest.java`

## 4. What Changed?
- Added `@Transactional` to `handleBillingRequested` (import
  `org.springframework.transaction.annotation.Transactional`), with a Javadoc block explaining
  the orphan-bill failure mode.
- `BillingEventListenerTest`: two new tests — `handleBillingRequested_whenSplitFails_doesNotBroadcast`
  (split throws → exception propagates, `messagingTemplate.convertAndSend` never called) and
  `handleBillingRequested_isTransactional` (reflection assertion that the annotation stays put).
- Full backend suite: `./mvnw test` → **1092/1092** (1090 baseline + 2 new).

## 5. Why It Changed?
`calculateBill`, `splitByConsumption` and `splitEqually` each carry their own `@Transactional`
(propagation `REQUIRED`). The `@EventListener` is invoked synchronously from
`BillingController.requestBilling`, which is **not** transactional, so before this change each
inner call opened and committed its own transaction independently.

Consequence: when the split step threw after `calculateBill` had already committed — e.g.
`splitEqually` with `participantCount` greater than the real participant count, or the
EQUAL_PARTS divide-by-zero fixed in PR #85 — the `Bill` row stayed committed. The next billing
request for that session then hit `calculateBill`'s `findBySessionIdAndStatusNot(... , VOIDED)`
guard and threw `IllegalStateException("Session already billed")` (HTTP 409) forever, until a
waiter manually voided the orphan bill.

With `@Transactional` on the handler, both inner calls join one unit of work. A split failure
now rolls the `Bill` back too, so the waiter can simply retry. The `convertAndSend` broadcast is
the last statement in the method, so `BILL_READY` still only fires after everything commits.

PR #85 removed the most common trigger (the null `participantCount` divide-by-zero); this change
closes the remaining path (`participantCount` > actual participants) and makes the whole
operation self-healing rather than requiring manual intervention.
