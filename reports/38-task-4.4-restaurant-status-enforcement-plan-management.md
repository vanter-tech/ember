# Report 38 — task-4.4

## 1. Identification
- **Report:** 38
- **Task ID:** task-4.4
- **Predecessor Task:** task-4.3 (report 37)

## 2. Objective
Wire `Restaurant.plan`/`status` — set at registration but never read anywhere — into real
behavior: enforce account status on every authenticated request, and give a tenant's ADMIN a
self-service way to change their subscription plan. Distinct from the diner-facing `billing`
package (`Bill`/`Payment`), this is billing *the tenant* for their platform subscription.

## 3. Modified Files
**Backend — main**
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java`
- `backend/src/main/java/com/vanter/ember/restaurant/controller/RestaurantAdminController.java` (new)
- `backend/src/main/java/com/vanter/ember/restaurant/model/dto/UpdateRestaurantPlanRequest.java` (new)

**Backend — test**
- `backend/src/test/java/com/vanter/ember/restaurant/service/RestaurantServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/restaurant/controller/RestaurantAdminControllerTest.java` (new)
- `backend/src/test/java/com/vanter/ember/restaurant/RestaurantStatusEnforcementTest.java` (new)
- `AuthControllerTest.java`, `KitchenControllerTest.java`, `MenuItemControllerTest.java`,
  `SessionControllerTest.java`, `DashboardControllerTest.java`, `CategoryControllerTest.java`,
  `BillingControllerTest.java`, `UserAdminControllerTest.java` — added a `RestaurantRepository`
  mock bean (mechanical ripple from `SecurityConfig`'s new constructor dependency).

## 4. What Changed?
- `SecurityConfig.jwtAuthFilter`: after resolving the tenant id from the JWT, it now loads the
  `Restaurant` row and rejects the request (403, RFC 7807 body) if the account is missing or its
  `status` isn't `ACTIVE`. Applies only to requests presenting a valid Bearer token — `/auth/**`
  and `/public/**` traffic (no token) is unaffected.
- `RestaurantService`: added `getCurrent`, `updatePlan` (tenant ADMIN self-service upgrade/
  downgrade), and `updateStatus` (service-layer only, not wired to any controller — see rationale
  below).
- `RestaurantAdminController` (`GET /admin/restaurant`, `PATCH /admin/restaurant/plan`, both
  `hasRole('ADMIN')`): lets a tenant view/change its own plan.
- No `PATCH /admin/restaurant/status` endpoint was added, despite being in the original plan
  sketch — see §5.

## 5. Why It Changed?
`Restaurant.plan`/`status` were dead data: set once at registration, read nowhere. That meant a
`SUSPENDED`/`INACTIVE` tenant had full, unimpeded access — the actual gap this task closes.

Status changes were deliberately **not** exposed to the tenant's own ADMIN: if a suspended
tenant's admin could `PATCH` their own status back to `ACTIVE`, the enforcement added to
`jwtAuthFilter` would be self-defeating (a suspended tenant's admin can still log in — nothing
currently blocks authentication itself, only post-auth requests — and would immediately unsuspend
on the first call). Real SaaS platforms follow the same split: plan changes (upgrade/downgrade)
are self-service, but suspension for non-payment is billing-system-driven. `updateStatus` exists
as the hook a future billing webhook will call once one exists; wiring an actual payment provider
(Stripe, etc.) was out of scope — none exists anywhere in the codebase today (confirmed by
research: no "stripe"/"subscription" references outside `SettingsPayload.PaymentGatewaySettings`,
which is the tenant's own diner-payment config, not platform billing).

## Verification
`cd backend && ./mvnw test` — **423/423 passing**, including 12 new tests: `RestaurantServiceTest`
(3), `RestaurantAdminControllerTest` (6), `RestaurantStatusEnforcementTest` (3). Frontend
untouched this task, not rebuilt.
