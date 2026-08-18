# Report 160 — Task feature-loyalty-tier-progress-and-restaurant-name

## Identification
- **Report number:** 160
- **Task ID:** feature-loyalty-tier-progress-and-restaurant-name
- **Predecessor Task:** bugfix-home-bento-grid-layout (report 159)

## Objective
Add a visual progress bar showing how many points are left to reach the next loyalty tier, and attribute the loyalty dashboard's points to the restaurant they belong to, per user request.

## Modified Files
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/service/LoyaltyAccountService.java`
- `backend/src/main/java/com/vanter/ember/loyalty/dto/LoyaltyAccountResponse.java`
- `backend/src/test/java/com/vanter/ember/loyalty/service/LoyaltyServiceTest.java`
- `frontend/src/lib/backend-types.ts` (regenerated)
- `frontend/src/pages/customer/Home.tsx`

## What Changed?
- New `LoyaltyService.tierFloor(tier, settings)`: the point value a tier itself begins at (BRONCE always 0, others their configured threshold) — unit-tested at each tier.
- `LoyaltyAccountResponse` gained two fields: `tierProgressPercent` (0–100, `null` once already PLATINO) and `restaurantName`.
- `LoyaltyAccountService.getMyAccount` now computes `tierProgressPercent` as the percent of the way from the current tier's floor to the next tier's floor (clamped 0–100), and resolves `restaurantName` via a new `RestaurantRepository` dependency (`findById(tenantId)`, matching `TenantContextHolder`'s tenant = `Restaurant.id`).
- `Home.tsx`: the points card now shows a small "Fidelización en {restaurantName}" label (with a `Store` icon) above the points/tier/last-visit row, and the tier column renders an actual progress bar (a filled `div` sized to `tierProgressPercent`) under the tier badge instead of just the "N pts para Tier" text — falls back to "Nivel máximo alcanzado" once there's no next tier.

## Why It Changed?
User-requested: a visual progress indicator toward the next tier (previously only text), and visible attribution of which restaurant the shown points belong to (loyalty accounts are per-restaurant, and nothing on Home previously named the restaurant).

## Verification
- `cd backend && ./mvnw test` — full suite green.
- `cd frontend && pnpm run build` — `tsc -b` and `vite build` both passed.
- Regenerated `backend-types.ts` against a temporary backend instance on port 8081 (the user's own IntelliJ debug session on 8080 was left untouched throughout, same approach as reports 157/160's predecessors).
