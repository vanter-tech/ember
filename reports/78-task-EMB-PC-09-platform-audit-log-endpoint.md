# Report 78 — EMB-PC-09: Platform Audit Log Endpoint

## Identification
- **Report:** 78
- **Task ID:** EMB-PC-09
- **Predecessor Task:** EMB-PC-08 (report 77)

## Objective
Expose the platform-operator audit trail (`platform_audit_log`, written since EMB-PC-07/08) as a
paginated, `restaurantId`-filterable read endpoint: `GET /platform/audit-log`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/platform/repository/PlatformAuditLogRepository.java`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformAuditLogResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformAuditLogService.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformAuditLogController.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/repository/PlatformAuditLogRepositoryTest.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformAuditLogServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/platform/controller/PlatformAuditLogControllerTest.java` (new)
- `PROGRESS.md`

## What Changed?
- `PlatformAuditLogRepository` gained `Page<PlatformAuditLog> findByRestaurantId(UUID, Pageable)`.
- New `PlatformAuditLogResponse` DTO (`from(PlatformAuditLog)`) mirrors the entity's fields
  (`id`/`operatorId`/`operatorEmail`/`restaurantId`/`action`/`oldValue`/`newValue`/`createdAt`).
- New `PlatformAuditLogService.getAuditLog(UUID restaurantId, Pageable pageable)`: delegates to
  `findByRestaurantId` when a filter is supplied, otherwise `findAll`; maps to the response DTO.
- New `PlatformAuditLogController` — `GET /platform/audit-log?restaurantId=&page=&size=&sort=`,
  with `@PageableDefault(sort = "createdAt", direction = DESC)` so an unfiltered/unsorted request
  still reads newest-first.
- Repository test extended with `findByRestaurantId_returnsOnlyMatchingEntries`; new service test
  (both branches — filtered/unfiltered) and controller test (401 without auth, 200 unfiltered,
  200 filtered by `restaurantId`), following the exact `@WebMvcTest` + `PlatformSecurityConfig`
  import + manual JWT mock pattern used by `PlatformRestaurantControllerTest`.

## Why It Changed?
EMB-PC-02/07/08 already write to `platform_audit_log` but nothing could read it back; EMB-PC-13's
planned "per-tenant audit history" panel needs this filterable read now. The endpoint sits in its
own controller/service (not folded into `PlatformRestaurantService`) since audit-log reads are not
restaurant-directory-specific — the table has no restaurant FK and `restaurantId` is only one of
several optional filters this resource could grow. `/platform/**`'s existing blanket
`anyRequest().authenticated()` in `PlatformSecurityConfig` already covers the new route; no new
security wiring was needed, verified by the controller test's 401 case and the full green
`./mvnw test` run (580 tests, 0 failures).
