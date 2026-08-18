# Report 151

**Task ID:** EMB-CLP-06
**Predecessor Task:** EMB-CLP-05 (report 150)

## Objective

Add admin reward-catalog endpoints (`POST/GET/PATCH /loyalty/rewards`) over the existing `LoyaltyReward` entity, with DTOs and `SecurityAuditTest` coverage.

## Modified Files

- `backend/src/main/java/com/vanter/ember/loyalty/dto/CreateLoyaltyRewardRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/dto/UpdateLoyaltyRewardRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyRewardResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyRewardService.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyRewardController.java` (new)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## What Changed?

- `LoyaltyRewardController` (`@RequestMapping("/loyalty/rewards")`, no `/api` prefix — matches the rest of the codebase, `/api` is a known-stale convention): `POST` (create), `GET` (list all, including inactive), `PATCH /{id}` (edit fields / toggle `active`), all `@PreAuthorize("hasRole('ADMIN')")`.
- `LoyaltyRewardService`: `create` builds and saves a `LoyaltyReward` from the request (tenant auto-populated by Hibernate's `@TenantId` on insert, same as `CategoryService.create`); `list(tenantId)` reuses the pre-existing `LoyaltyRewardRepository.findByTenantId`; `update` does `findById` (implicitly tenant-scoped by the entity's `@TenantId` filter, same as `CategoryService.findEntityById`/`MenuItemService.findById` — no manual tenant re-check needed) then applies only the non-null fields from `UpdateLoyaltyRewardRequest`, mirroring `UpdateStaffProfileRequest`'s partial-PATCH pattern.
- DTOs: `CreateLoyaltyRewardRequest` (`name` `@NotBlank`, `requiredTier` `@NotNull`, `description` optional), `UpdateLoyaltyRewardRequest` (all fields optional, PATCH-style), `LoyaltyRewardResponse` (plain projection record).
- `SecurityAuditTest`: 3 new CSV rows (`POST/GET /loyalty/rewards`, `PATCH /loyalty/rewards/1`) asserting unauthenticated 401.

## Why It Changed?

Implements EMB-CLP-06 per `docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` §3.5. No dedicated service/controller unit test was added — the spec's §5 testing strategy only assigns `SecurityAuditTest` coverage to this task (reward CRUD is straightforward, and tier/accrual math already has dedicated coverage from EMB-CLP-03/05); this keeps the change surgical rather than introducing test scope beyond what the task and spec call for. Lookup-by-id relies on the entity's existing `@TenantId` Hibernate filter rather than an explicit tenant-ownership check, following the identical pattern already used by `CategoryService`/`MenuItemService` for other `@TenantId`-scoped entities — `User` is the one exception in this codebase (deliberately excluded from `@TenantId`), which is why `UserAdminService.updateProfile` needed a manual re-check that `LoyaltyReward` does not.

## Verification

`cd backend && ./mvnw test` — 716/716 green (0 failures, 0 errors across `target/surefire-reports`).
