# Report 156 — Task EMB-CLH-02

## Identification
- **Report number:** 156
- **Task ID:** EMB-CLH-02
- **Predecessor Task:** EMB-CLH-01 (report 155)

## Objective
Add `GET /loyalty/accounts/me/visits`, the tenant-nullable-safe read endpoint the customer Home loyalty dashboard uses both to fetch recent visit history and to safely detect whether a customer has ever joined a table anywhere, per `docs/superpowers/specs/2026-08-17-customer-home-loyalty-dashboard-design.md` §3.4.

## Modified Files
- `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyVisitResponse.java` (new)
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/controller/LoyaltyAccountController.java`
- `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyAccountServiceTest.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

## What Changed?
- New `LoyaltyVisitResponse(LocalDateTime visitedAt, BigDecimal amountPaid, int pointsEarned)` record.
- `LoyaltyAccountService.getMyVisits(UUID tenantId, String userId)` reuses the existing `LoyaltyTransactionRepository.findByLoyaltyAccountIdOrderByCreatedAtDesc`, caps the result at the most recent 20 rows, and maps each to a `LoyaltyVisitResponse`.
- `LoyaltyAccountController.myVisits` reads `TenantContextHolder.getTenantId()` — the **nullable** getter, not the existing `/me` endpoint's `requireTenantId()` — and throws the existing `ResourceNotFoundException` (404) when no tenant is bound, instead of letting an unhandled `IllegalStateException` (500) escape. `GET /loyalty/accounts/me` itself is untouched.
- Added two service tests (`getMyVisits_returnsMostRecentTwentyNewestFirst`, `getMyVisits_noAccountForTenant_throwsResourceNotFound`) and a `SecurityAuditTest` row for the new route.

## Why It Changed?
`Home.tsx` (EMB-CLH-04) needs to distinguish "customer has never joined any table, ever" (show today's join-table card) from "customer has joined this tenant, just hasn't settled a bill yet" (show the dashboard with an empty history) — and it needs to make that determination without ever risking a 500 on a perfectly normal first-time-visitor page load. The existing `/me` endpoint's `requireTenantId()` throws for exactly that first case, so a new endpoint with nullable-safe tenant resolution was needed rather than changing `/me`'s established contract.

## Verification
`cd backend && ./mvnw test` — 720/720 passing.
