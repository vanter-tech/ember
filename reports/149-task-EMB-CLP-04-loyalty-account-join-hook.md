# Report 149

## 1. Identification
- **Report number:** 149
- **Task ID:** EMB-CLP-04
- **Predecessor Task:** EMB-CLP-03

## 2. Objective
Create a customer's `LoyaltyAccount` lazily the first time they join a table at a tenant, per design decision #2 (`docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` §3.4) — the join is what links a customer to a tenant's loyalty program, not registration or login.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java` (new)
- `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListenerTest.java` (new)

## 4. What Changed?
- `LoyaltyAccountService.findOrCreate(tenantId, userId)`: idempotent get-or-create against `LoyaltyAccountRepository.findByTenantIdAndUserId`, mirroring `SettingService.getSettings`/`creatorDefaultSettings`'s existing find-or-default pattern. Builds `LoyaltyAccount` with `totalPoints = 0`; `tenantId` is not set on the builder — Hibernate's `@TenantId` auto-stamps it from `TenantContextHolder` on save (confirmed by the existing `LoyaltyAccountRepositoryTenantIsolationTest`'s identical builder usage).
- `LoyaltyAccountJoinListener`: a `@Component`/`@EventListener` (same shape as `PaymentCompletedListener`) on the **existing** `ParticipantJoined` event, delegating to `findOrCreate(event.tenantId(), event.userId())`. No new event type or change to `SessionService` — `ParticipantJoined` already fires exactly once per true first-join from both `joinSession` (QR) and `joinSessionCode`, and `TenantContextHolder` is already bound by `SessionService.bindResolvedTenant` in the same request/thread before the event publishes synchronously.
- Two new unit test classes (Mockito, no DB): `LoyaltyAccountServiceTest` covers create-when-absent (asserts zero points, non-null `createdAt`) and return-existing-without-saving; `LoyaltyAccountJoinListenerTest` verifies the listener delegates the event's `tenantId`/`userId` to the service.

## 5. Why It Changed?
`LoyaltyAccount` is keyed on `(tenantId, userId)`, and `User.restaurantId` is null for customers by design — a customer's tenant is only known once they join a table (see PROGRESS.md's "CUSTOMERS ARE NOT TENANT-BOUND" note). Hooking off the already-published `ParticipantJoined` event, rather than adding creation logic inline in `SessionService`, covers both join paths (QR and join-code) from one place and keeps `SessionService` unchanged. `findOrCreate` being idempotent also lets EMB-CLP-05's upcoming `LoyaltyAccrualListener` reuse it as a safety net (per spec §3.3 step 4) without duplicating the get-or-create logic.

## Verification
`cd backend && ./mvnw test` — **709/709 green** (706 + 3 new).
