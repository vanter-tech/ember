# Report 654 — SUBSCRIPTION-MONTHLY-RENEWAL-CONSOLE

## 1. Identification
- Report: 654
- Task ID: SUBSCRIPTION-MONTHLY-RENEWAL-CONSOLE
- Predecessor: 653 (PLAN-TAB-LAYOUT-AND-FEATURES); builds on uncommitted r647 (SETTINGS-PLAN-TAB-SUBSCRIPTION)

## 2. Objective
Give Settings > Plan sensible default dates and let the /console operator renew each client's subscription month by month (or semestral/annual); the restaurant's "next payment" comes from that renewal.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/restaurant/model/BillingPeriod.java`
- `backend/src/main/java/com/vanter/ember/restaurant/model/Restaurant.java`
- `backend/src/main/java/com/vanter/ember/restaurant/model/dto/SubscriptionResponse.java`
- `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantDetailResponse.java`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantSubscriptionRenewRequest.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- Tests: `RestaurantServiceTest`, `PlatformRestaurantServiceTest`, `PlatformRestaurantControllerTest`
- `frontend/src/lib/platformApi.ts`, `frontend/src/lib/api.ts`
- `frontend/src/components/console/SubscriptionCard.tsx`, `frontend/src/pages/console/ConsoleRestaurantDetail.tsx` (+ test)
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx` (+ test), `frontend/src/locales/{es,en}/admin.ts`

## 4. What Changed?
- `BillingPeriod` gains `MONTHLY` (flat 30 days); `endFrom(start)` computes the period end (SEMESTRAL +6 months, ANNUAL +1 year, UTC). No migration (varchar, no CHECK).
- `Restaurant.effectivePlanStart/BillingPeriod/PlanEnd`: start defaults to account creation (truncated to the UTC day), period to MONTHLY, end to start + period. Used by `SubscriptionResponse` (restaurant view) and `PlatformRestaurantDetailResponse` (console), so both see the same dates.
- `POST /platform/restaurants/{id}/subscription/renew {billingPeriod}` (SUPER_ADMIN): new end = max(current end, today) + period; fills the start if empty; audited as `RESTAURANT_SUBSCRIPTION_RENEWED`.
- Console `SubscriptionCard`: "Renovar por" selector (Mensual/Semestral/Anual), Renovar button with a confirmation that previews the next payment; manual date form kept, now with a Mensual option. Card re-keys on `planPeriodEnd` so it refreshes after renewal.
- Settings > Plan: "Próximo pago" label, Mensual period shown, the "no dates" message removed. Dates now render in UTC.

## 5. Why It Changed?
The operator manually keeps each client active month by month; the restaurant must see a coherent start date and next payment without the operator hand-entering dates first. Dates are stored as UTC midnight, so showing them in local time displayed a day early in UTC-6; they are now shown (and truncated) in UTC.

Verification: `./mvnw test` 1696/1696 (one earlier full run failed once on an unidentified test, rerun passed with no changes), frontend `pnpm run build` clean, lint 0 errors, console + settings tests 46/46. Not exercised in a browser. NOT committed: depends on uncommitted r647 files.
