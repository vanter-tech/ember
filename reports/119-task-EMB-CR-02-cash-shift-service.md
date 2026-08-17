# Report 119 — Task EMB-CR-02: Cash Shift Service, Events, WebSocket Broadcast

**Predecessor Task:** EMB-CR-01 cash shift data layer (report 117)

## Objective

Implement `CashShiftService` (open/movement/close lifecycle, history, daily report, response-DTO mapping), its domain events, and the `CashRegisterWebSocketListener` broadcast, per `docs/superpowers/plans/2026-08-16-cash-register-shift-management.md` Task 2.

## Modified Files

- `backend/src/main/java/com/vanter/ember/cashregister/dto/{CashShiftResponse,CashMovementResponse,CashShiftDetailResponse,DailyReportResponse}.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/event/{CashShiftOpened,CashShiftClosed,CashMovementRecorded}.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/listener/CashRegisterWebSocketListener.java` (new)
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java` (new)
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java` (new)

## What Changed

- Four response DTOs (records), each mapping straight to the JSON shape the controller (Task 3) will return.
- Three domain events, published via `ApplicationEventPublisher` per this codebase's Kafka-free convention, and `CashRegisterWebSocketListener` broadcasting all three to `/topic/cash-register/{tenantId}`, mirroring `WaiterWebSocketListener`'s shape exactly.
- `CashShiftService`: `openShift`, `recordMovement`, `closeShift` (the blind-close math: `expectedCash = openingFloat + cashIn − cashOut + confirmedPhysicalSales`, `variance = countedCash − expectedCash`), `getCurrentOpenShift`, `getById`, `getDetail`, `getHistory`, `getDailyReport`, plus `toResponse`/`toMovementResponse` with batched `UserRepository.findAllById` name resolution (avoids N+1 lookups and avoids ever exposing a raw `User` object).

## Why It Changed

Builds the business-logic layer on top of Task 1's data layer. `closeShift` locks the shift row via `findByIdForUpdate` before computing totals, so a payment landing mid-close either completes before the lock is acquired (counted) or is rejected once the shift is `CLOSED` (Task 4's job).

## Deviation From the Plan (self-review finding, fixed)

The plan's `openShift(String openedByUserId, BigDecimal openingFloat)` signature resolved the tenant internally via `TenantContextHolder.requireTenantId()`. Writing the RED test first surfaced a real bug this would have shipped: a Mockito unit test with no bound `TenantContextHolder` throws `IllegalStateException("No tenant bound...")` *before* the method ever reaches `cashShiftRepository.findByTenantIdAndStatus`, and since both that error and the intended "shift already open" rejection are `IllegalStateException`, `assertThatThrownBy(...).isInstanceOf(IllegalStateException.class)` couldn't tell them apart — the test would have silently verified the wrong thing. Mockito's strict stubbing caught it as `UnnecessaryStubbingException` (the `findByTenantIdAndStatus` stub was never reached).

Fix: changed `openShift` to accept `UUID tenantId` as an explicit parameter, matching every other tenant-aware method already in this service (`getCurrentOpenShift`, `getHistory`, `getDailyReport`) and the `AnalyticsService` convention elsewhere in the codebase (controller resolves `TenantContextHolder.requireTenantId()`, service takes it as a parameter). Task 3's controller must call `cashShiftService.openShift(TenantContextHolder.requireTenantId(), resolveUserId(authentication), request.openingFloat())` — **this differs from the plan's Task 3 controller snippet**, which must be updated to match when that task is implemented.

Also added one test the plan didn't include (`openShift_createsShiftWithNextSequentialNumberAndPublishesEvent`) — the plan only tested `openShift`'s rejection path, not its happy path.

## Verification

- `cd backend && ./mvnw test -Dtest=CashShiftServiceTest` — RED (compile failure) → implemented → first GREEN attempt caught the `UnnecessaryStubbingException` above → fixed signature + test → GREEN: `Tests run: 7, Failures: 0, Errors: 0`.
- `cd backend && ./mvnw test` (full suite): `Tests run: 610, Failures: 0, Errors: 0` — no regressions.
