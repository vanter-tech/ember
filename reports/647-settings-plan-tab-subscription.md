# Report 647 — SETTINGS-PLAN-TAB-SUBSCRIPTION

## 1. Identification
- Report: 647
- Task ID: SETTINGS-PLAN-TAB-SUBSCRIPTION
- Predecessor: 646 (MONEY-CENT-ROUNDING-FIXES)

## 2. Objective
Add a read-only "Plan" tab to Settings showing the restaurant's subscription (plan, start, billing period, next renewal, what the plan includes), with the dates recorded by the platform operator.

## 3. Modified Files
Backend:
- `backend/src/main/resources/db/migration/V23__restaurant_subscription.sql` (new)
- `restaurant/model/BillingPeriod.java` (new), `restaurant/model/Restaurant.java`
- `restaurant/model/dto/SubscriptionResponse.java` (new)
- `restaurant/service/RestaurantService.java`, `restaurant/controller/RestaurantAdminController.java`
- `platform/model/dto/PlatformRestaurantSubscriptionUpdateRequest.java` (new), `PlatformRestaurantDetailResponse.java`
- `platform/service/PlatformRestaurantService.java`, `platform/controller/PlatformRestaurantController.java`
- Tests: `RestaurantAdminControllerTest`, `RestaurantServiceTest`, `PlatformRestaurantServiceTest`, `PlatformRestaurantControllerTest`, `SecurityAuditTest`

Frontend:
- `pages/admin/components/settings/PlanSettings.tsx` (+ `PlanSettings.test.tsx`), `pages/admin/Settings.tsx`
- `components/SettingsBar.tsx`, `components/GlobalSearchResults.tsx`, `store/uiStore.ts`
- `components/console/SubscriptionCard.tsx` (new), `pages/console/ConsoleRestaurantDetail.tsx`
- `lib/api.ts`, `lib/platformApi.ts`, `locales/{es,en}/admin.ts`

## 4. What Changed?
- `V23` adds nullable `plan_started_at`, `billing_period`, `plan_period_end` to `restaurants` (idempotent, no CHECK constraint).
- `GET /admin/restaurant/subscription` (ADMIN) returns plan, status and the three dates; it carries no price.
- `PATCH /platform/restaurants/{id}/subscription` (SUPER_ADMIN) records the dates, rejects an end not after the start, and writes a `RESTAURANT_SUBSCRIPTION_UPDATED` audit row. The platform detail response also exposes the fields.
- Settings > Plan (hidden in the Hub build and its global search): plan, status, start, period, next renewal with days left, amber notice at <=30 days and red once expired, feature list mirroring `PlanGateService`, and a contact button (`https://ember.vanter.net/contacto`). No pay button (renewal is manual) and no prices (founder discount is never public).
- Console restaurant detail gets a "Suscripción" card (start date, period, end date) to fill it in.

## 5. Why It Changed?
Customers could not see what they contracted or when it renews, and the operator had nowhere to record it. Enforcement on expiry was intentionally NOT added: what happens when a plan expires (grace period, read-only, nothing) is an open business decision.

## Verification
- Backend full `./mvnw test`: 1686/1686, BUILD SUCCESS.
- Frontend: `pnpm run build` clean, lint 0 errors, `src/pages/admin` + `src/pages/console` + `src/components` 108/108 (5 new `PlanSettings` tests).
- Not verified in a browser. `V23` runs automatically on deploy; never pre-run it on prod.
