# Report 141 — Task EMB-RV-05: Analytics Refund Netting

## Identification
- **Report:** 141
- **Task ID:** EMB-RV-05
- **Predecessor Task:** EMB-RV-04 (report 140)

## Objective
Make `AnalyticsService.getSummary`/`getSales` net refunds out of their revenue figures, so a
refunded sale stops inflating the admin dashboard's revenue and sales-series charts. This is the
last task of the backend stream (EMB-RV-01..05).

## Modified Files
- Modified: `backend/src/main/java/com/vanter/ember/analytics/service/AnalyticsService.java`
- Modified: `backend/src/test/java/com/vanter/ember/analytics/service/AnalyticsServiceTest.java`

## What Changed?
- `AnalyticsService` gained a `RefundRepository` dependency (Task 1).
- `getSummary`: after computing confirmed-payment revenue, also queries
  `refundRepository.sumRefundsInWindow(restaurantId, windowStart, windowEnd)` and subtracts it
  (both null-safe against `BigDecimal.ZERO`) before scaling into the response's `totalRevenue`.
  `averageOrderValue`/`paidBillCount` are untouched — those come from `BillSalesTotals`
  (PAID-bill sums/counts), a deliberately different measure that a later refund doesn't rewrite.
- `getSales`: after the existing per-day `PaymentDailyRevenue` loop populates
  `revenueByBucket`, a second loop over `refundRepository.findRefundsByDay(...)` merges each
  day's refund total into the same map as a **negated** amount, using the same
  `granularity.bucketStart(row.date())` bucketing as the payment loop — so a refund lands in
  whichever day/week/month/year bucket it was issued in, not the bucket the original payment
  landed in (a refund issued the day after the sale nets out of the refund's own day, not
  retroactively rewrites yesterday's bucket).
- Test file gained a `RefundRepository` mock and two new tests,
  `getSummary_netsOutRefundsIssuedInTheWindow` and `getSales_netsOutRefundsPerBucket`, both
  asserting the net (payment minus refund) figure lands in the response.

## Why It Changed?
This is the plan's stated analytics fix: without it, a refunded or voided-then-recreated sale
would keep counting as revenue forever, which is exactly the kind of "plausible but wrong number"
this codebase's analytics module has consistently avoided elsewhere (see the deliberate redundant
`tenantId` predicates in every analytics repository query, kept specifically so a context bug
surfaces as an error rather than a believable-looking figure). Bucketing refunds by their own
`createdAt` day (via `RefundDailyAmount`, same shape as `PaymentDailyRevenue`) rather than by the
original payment's day keeps the chart's math simple and auditable: each day's number is "what
happened financially that day," matching how `CashShiftService.closeShift`'s cash-out-on-the-
currently-open-shift design (Task 2) already treats refunds as events in their own right, not
retroactive edits to history.

## TDD Evidence

**RED** — `cd backend && ./mvnw test -Dtest=AnalyticsServiceTest`, run after adding the 2 new
tests and the `RefundRepository` mock but before wiring the dependency into the service:
```
[ERROR] AnalyticsServiceTest.getSummary_netsOutRefundsIssuedInTheWindow -- Time elapsed: 0.045 s <<< FAILURE!
org.opentest4j.AssertionFailedError:
expected: 85.00
 but was: 100.00
[ERROR] AnalyticsServiceTest.getSales_netsOutRefundsPerBucket -- Time elapsed: 0.013 s <<< FAILURE!
org.opentest4j.AssertionFailedError:
expected: 85.00
 but was: 100.00
[ERROR] Tests run: 37, Failures: 2, Errors: 0, Skipped: 0
[INFO] BUILD FAILURE
```
Exactly the expected failure mode: revenue unadjusted (100.00, ignoring the stubbed 15.00
refund) instead of the netted 85.00. All 35 pre-existing tests in the file still compiled and
passed unchanged, confirming an unstubbed `refundRepository` mock returns `null`/empty list by
Mockito's default and the null-safe code treats that as zero.

**GREEN** — `cd backend && ./mvnw test -Dtest=AnalyticsServiceTest`, run after wiring
`RefundRepository` into `getSummary`/`getSales`:
```
[INFO] Tests run: 37, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**Full suite (final backend verification)** — `cd backend && ./mvnw test`:
```
[INFO] Tests run: 682, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```
(680 after Task 4 + 2 new refund-netting tests = 682. This is the last backend task — the
backend stream (EMB-RV-01..05) ends here, all green.)

## Self-Review
- **Completeness:** Both `getSummary` and `getSales` net refunds as specified; no signature
  change to either method, matching the task's "behavior change only" interface note.
- **Quality:** Refund netting reuses the exact same null-safety and bucketing idioms already
  established in this file for payments (`row.revenue() == null ? ZERO : row.revenue()`,
  `granularity.bucketStart(row.date())`) rather than introducing a new pattern.
- **Discipline:** `getProducts`/`getTables` were deliberately left untouched — the task's scope
  is `getSummary`/`getSales` only; item-level and table-level analytics don't have a refund
  concept to net (a refund is against a `Payment` in money terms, not against specific line
  items or tables).
- **Testing:** Both new tests assert the netted figure directly rather than just verifying the
  refund repository was called, and the RED run confirmed every pre-existing test in the file
  was unaffected by adding the new mock.

## Backend Stream Complete
Tasks EMB-RV-01 through EMB-RV-05 are all implemented, tested, and committed. Final backend test
count: 682 tests, all green.
