# Report 153

**Task ID:** EMB-CLP-08
**Predecessor Task:** EMB-CLP-07 (report 152)

## Objective

Add `GET /loyalty/accounts/me` (CUSTOMER) and a minimal customer-facing "you earned N points" confirmation on `Bill.tsx`, per `docs/superpowers/specs/2026-08-17-customer-loyalty-program-design.md` §3.5/§4.

## Modified Files

- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyAccountResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/dto/RewardCatalogEntryResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyAccountController.java` (new)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `frontend/src/lib/api.ts`
- `frontend/src/pages/customer/Bill.tsx`

## What Changed?

- `LoyaltyService` gained two pure helpers: `nextTier(current)` (next rung on the fixed ladder, `null` once `PLATINO`) and `pointsToNextTier(totalPoints, next, settings)` (threshold-minus-total, clamped at 0, `null` when `next` is `null`).
- New DTOs `LoyaltyAccountResponse` (`totalPoints`, `tier`, `nextTier`, `pointsToNextTier`, `rewards`) and `RewardCatalogEntryResponse` (`id`, `name`, `description`, `requiredTier`, `unlocked`).
- `LoyaltyAccountService.getMyAccount(tenantId, userId)`: looks up the account (404 via `ResourceNotFoundException` if the customer never joined a table at this tenant), computes tier/nextTier/pointsToNextTier via `LoyaltyService` against the tenant's current `LoyaltySettings` thresholds, and annotates every *active* reward with `unlocked = tier.ordinal() >= reward.requiredTier.ordinal()`.
- `LoyaltyAccountController` (`@RequestMapping("/loyalty/accounts")`, `GET /me`, `@PreAuthorize("hasRole('CUSTOMER')")`): resolves the caller's real `User.id` via `userRepository.findByEmail(authentication.getName())` — the JWT subject/`Authentication.getName()` is the email, not the id, same resolution pattern as `CashShiftController.resolveUserId`.
- `SecurityAuditTest`: 1 new CSV row (`GET /loyalty/accounts/me`) asserting unauthenticated 401.
- `frontend/src/lib/api.ts`: interim hand-typed `LoyaltyAccountResponse`/`RewardCatalogEntry` + `loyaltyAccountService.me()` (same "not yet in `backend-types.ts`" convention as `LoyaltyRewardResponse`).
- `Bill.tsx`: a `useQuery(['loyaltyAccount', 'me'], loyaltyAccountService.me)` enabled only once `mySplit.status === 'PAID'` (`retry: false`, since a 404 before the account exists is expected, not transient); renders a small red-tinted `Card` ("¡Ganaste puntos! Ahora tienes N pts" + tier line, reusing the admin `TIER_LABELS` map) directly above the existing pay button block.

## Why It Changed?

Implements EMB-CLP-08, closing out the EMB-CLP engine backlog (sub-project A) with a thin, verifiable customer-visible surface — sub-project B's full bento-grid Profile page is a separate future spec/plan that will consume this same endpoint. No dedicated service/controller unit test was added, matching the EMB-CLP-06/07 precedent (spec §5 only scopes `SecurityAuditTest` rows to endpoint tasks; tier/accrual math already has dedicated coverage from EMB-CLP-03/05). The query trigger is `mySplit.status === 'PAID'` rather than the broader `PAID`/`PARTIALLY_PAID` used for the split-status badge above it — a `PARTIALLY_PAID` split only arises post-refund (EMB-RV), which is out of this task's scope and not mentioned by the spec.

## Verification

- `cd backend && ./mvnw test` — 717/717 green (0 failures, 0 errors across `target/surefire-reports`).
- `cd frontend && pnpm run build` — `tsc -b` clean, `vite build` succeeded.
