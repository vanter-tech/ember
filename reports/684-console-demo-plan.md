# Report 684 — CONSOLE-DEMO-PLAN

## 1. Identification
- **Report number:** 684
- **Task ID:** CONSOLE-DEMO-PLAN (ad hoc, Enterprise cloud demo for the Facebook Ads campaign)
- **Predecessor task:** report 683 — RELEASE-V0.3.6

## 2. Objective
Let the operator create a demo tenant from the Console: a "Demo" entry in the Plan dropdown that creates an ENTERPRISE restaurant with a 25-day subscription window, on Ember Web (cloud), so prospects see the full product without any installer or Hub license.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantCreateRequest.java`
- `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`
- `frontend/src/lib/platformApi.ts`
- `frontend/src/pages/console/ConsoleRestaurantCreate.tsx`
- `frontend/src/pages/console/ConsoleRestaurantCreate.test.tsx`
- `PROGRESS.md`

## 4. What Changed?
- `PlatformRestaurantCreateRequest` gains an optional `boolean demo`.
- `PlatformRestaurantService.create`: when `demo` is true, the plan is forced to `ENTERPRISE`, `planStartedAt = now` and `planPeriodEnd = now + 25 days` (`DEMO_DAYS`). Otherwise the behavior is unchanged (no window set, plan defaults to FREE).
- Console create form: the Plan dropdown has a fifth entry "Demo (Enterprise, 25 días)". `DEMO` exists only in the form schema; the mutation sends `plan: 'ENTERPRISE', demo: true`. `PlatformRestaurantCreateRequest` (TS) mirrors the new `demo?` field.
- Tests: two service tests (demo forces ENTERPRISE + exactly 25 days; non-demo leaves the window null) and one form test (Demo submits ENTERPRISE + `demo: true` + `CLOUD`).
- Verification: backend `./mvnw test` 1758/1758; frontend `pnpm run build` exit 0, `pnpm run lint` 0 errors (15 existing warnings); `ConsoleRestaurantCreate` vitest 3/3.

## 5. Why It Changed?
The campaign leads need a safe way to try Ember. A cloud tenant lives on our server and can be suspended from the Console, so there is no installer or license to pirate (unlike an Ember Hub demo). `DEMO` was deliberately not added to the `RestaurantPlan` enum: that would need a migration widening `restaurants_plan_check` and a change in `PlanGateService` (the recurring CHECK-constraint trap); a UI-only option plus the existing subscription fields needs neither.

Known limits: the 25 days are informational — plan expiry is still not enforced, so the operator suspends the tenant by hand. A demo is not distinguishable from a real ENTERPRISE tenant in the list except by its end date. No migration; V24 is the latest on prod (verified before starting).
