# Report 76 — EMB-PC-07

## 1. Identification
- **Report number:** 76
- **Task ID:** EMB-PC-07
- **Predecessor Task:** EMB-PC-06 (report 75)

## 2. Objective
Wire the previously-unreachable `RestaurantService.updateStatus` behind a platform-operator-only
endpoint (`PATCH /platform/restaurants/{id}/status`), and record every status change in
`PlatformAuditLog` — the first write path into that table.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantStatusUpdateRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`

## 4. What Changed?
- Added `PlatformRestaurantStatusUpdateRequest` (`status: RestaurantStatus`, `@NotNull`).
- `PlatformRestaurantService` gained a `@Transactional updateStatus(restaurantId, newStatus, operatorEmail)`:
  resolves the calling `PlatformOperator` by email (mirrors `PlatformAuthService.changePassword`'s
  lookup pattern; throws `BadCredentialsException` if the operator row is gone despite a valid
  token), 404s via `ResourceNotFoundException` if the restaurant doesn't exist, delegates the
  actual mutation to `RestaurantService.updateStatus`, then writes one `PlatformAuditLog` row
  (`action=RESTAURANT_STATUS_UPDATED`, `oldValue`/`newValue` = status enum names) in the same
  transaction, and returns the updated `PlatformRestaurantSummaryResponse`.
- `PlatformRestaurantController` gained `PATCH /platform/restaurants/{id}/status`, taking
  `Authentication` to source the operator email (same shape as `PlatformAuthController#changePassword`).
- `RestaurantService.updateStatus`'s javadoc updated — it's no longer accurate to say it's
  "not exposed through any controller"; now documents that EMB-PC-07 is its only caller.
- Added service tests (success + audit-log field assertions, restaurant-not-found,
  operator-not-found) and controller tests (200, 400 on missing status, 401 unauthenticated,
  404 restaurant-not-found).

## 5. Why It Changed?
`RestaurantService.updateStatus` existed since task-4.4 specifically so tenant ADMINs could never
self-reactivate a SUSPENDED account, but nothing could call it — it needed a trusted, non-tenant
caller. The `/platform/**` chain (EMB-PC-04) is exactly that trust boundary: it never touches
`TenantContextHolder`, so PlatformRestaurantService's other reads (EMB-PC-06) already worked the
same way. Every mutation an operator makes through this console needs a durable trail
(`PlatformAuditLog`, EMB-PC-02), so the status write and the audit write happen in one
transaction rather than as two independent calls that could diverge on partial failure.

## Verification
- `./mvnw test -Dtest=PlatformRestaurantServiceTest,PlatformRestaurantControllerTest,RestaurantServiceTest` — 17/17 passing.
- `./mvnw test` (full suite) — 566/566 passing (was 559; +7 new tests, 0 regressions).
