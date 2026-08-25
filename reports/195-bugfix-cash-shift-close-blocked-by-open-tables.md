# Report 195 — Bugfix: cash shift close no longer allowed with occupied tables

## 1. Identification
- **Report number:** 195
- **Task ID:** bugfix — cash-shift-close-validation
- **Predecessor Task:** hotfix — WebSocket message broker prefix overwrite (report 194)

## 2. Objective
Prevent a waiter from closing the daily cash shift (`POST /cash-shifts/{id}/close`) while any table still has an open session (unpaid orders, orders in kitchen), which previously had zero validation.

## 3. Modified Files
- Modify: `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- Modify: `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`

## 4. What Changed?
`CashShiftService` gained a `SessionRepository` dependency. `closeShift` now calls `sessionRepository.countByTenantIdAndStatus(tenantId, SessionStatus.OPEN)` right after confirming the shift itself is `OPEN`, and throws `IllegalStateException` (mapped to HTTP 409 by the existing `GlobalExceptionHandler` rule, same as the "shift already open" case) if the count is greater than zero — before any expected-cash/variance calculation runs.

Test coverage: added `closeShift_throwsWhenTablesStillHaveOpenSessions`, and stubbed the new repository call (returning `0L`) in the existing `closeShift_computesExpectedCashAndVariance` happy-path test so it still reaches the calculation logic.

## 5. Why It Changed?
User-reported bug: a waiter could close the shift with 3 tables still having active kitchen orders, with no restriction at all. `SessionRepository.countByTenantIdAndStatus` was already a proven, tested query (used by `AnalyticsService` for `activeSessions`/`activeTableCount`), so it was reused rather than adding a new query. An occupied table always has its `Session` in `OPEN` status until payment/release, so this check directly covers the reported scenario without relying on unrelated signals (an earlier "block close unless 2+ active waiters" idea was rejected — it doesn't correlate with table occupancy and would break normal single-waiter operation).

This also resolves a secondary concern raised by the user (a table staying open across a shift boundary, and which shift its revenue gets attributed to): since `CashShift` is a single shared till per tenant and each `Payment` is stamped with whichever shift is `OPEN` at the moment it's registered (not the shift that was open when the table opened), blocking close while any table is open makes that scenario impossible to reach in the first place.
