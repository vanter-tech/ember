# Restaurant deployment mode (Web vs Hub) — design spec

**Date:** 2026-09-20
**Status:** approved by user in conversation, pending written-spec review and implementation plan

## 1. Problem

Creating a restaurant in the Console (`PlatformRestaurantService.create`) always produces the restaurant **and** an ADMIN user in the cloud database, whatever the restaurant is going to use. Nothing records whether a restaurant runs on Ember Web (cloud) or on an on-premise Ember Hub:

- The cloud login (`AuthService.login`) only checks that the user is active, so a Hub-only customer can also sign in on the web.
- The Hub receives that very same account at activation (`HubActivationService.activate` returns the ADMIN's name, email and password hash and `HubProvisioningRunner` seeds it locally), so one credential works in both places.
- A Hub license can be issued (`issueHubLicense`) for any restaurant, whatever way it was created.

Wanted: a restaurant is **either** Web **or** Hub. A Hub customer cannot use the web, and a Web customer cannot get a Hub. Changing from one to the other is a deliberate operator action.

## 2. Goals and non-goals

**Goals**
- A per-restaurant `deploymentMode` (`CLOUD` | `HUB`), chosen in the Console at creation and changeable by the operator only, with an audit trail.
- The cloud refuses everything tenant-facing for a `HUB` restaurant (login, existing tokens, QR/guest join, public pages, websockets).
- License issuance, Hub activation and the heartbeat require `HUB`.
- When a restaurant moves from Hub to Web, its Hub keeps working for a courtesy period and then becomes **read-only** ("modo consulta"): the customer can still look at their history and download the Excel export, but cannot register anything new.

**Non-goals**
- **No data is migrated between modes.** The Web side starts empty; each side keeps its own data. The reasons are security: importing a customer-supplied database dump or archive into the shared multi-tenant cloud database is an attack surface (arbitrary SQL, decompression bombs, cross-tenant writes) that is not worth carrying.
- No importer of any kind now. A future, very small menu import (flat CSV: category, name, price) is a medium-term possibility, to be designed separately.
- No plan selection in the switch flow (see decision 6) and no retention policy for the data left behind in the cloud (deferred).

## 3. Decisions taken (with the user)

1. Mode lives on `Restaurant`; enforced at the existing tenant gate rather than by deleting or disabling users (reversible, one place).
2. The operator changes the mode from the Console, in both directions, with a confirmation and an audit record. The customer never chooses.
3. No data migration Hub↔Web. Web starts clean. Migration of data is not offered for security reasons.
4. After Hub→Web the Hub goes **read-only permanently** (license revoked so it cannot operate), after the same 48 h courtesy the suspension already uses.
5. The mode selector is required at creation, with no default.
6. Plan is not part of the switch: the Hub is functionally the top tier (`ember.plans.enforced: false`, everything unlocked) and the operator agrees the cloud plan with the customer and sets it with the existing "change plan" action. The confirmation dialog only shows the current plan as a reminder.
7. Existing restaurants: `HUB` when they already have a Hub activation or a license was issued; `CLOUD` otherwise.
8. The password seed is not an issue for now (the ADMIN password is unique and cannot be changed).

## 4. Design

### 4.1 Data
- `restaurants.deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD'` with `CHECK (deployment_mode IN ('CLOUD','HUB'))`; entity field `Restaurant.deploymentMode` (`@Enumerated(STRING)`, new enum `DeploymentMode`).
- Migration `V15__restaurant_deployment_mode.sql`, **idempotent** (production Flyway is not baselined): `ADD COLUMN IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS` + `ADD CONSTRAINT`, and the backfill
  `UPDATE restaurants SET deployment_mode = 'HUB' WHERE id IN (SELECT restaurant_id FROM hub_activations) OR id IN (SELECT restaurant_id FROM platform_audit_log WHERE action = 'HUB_LICENSE_ISSUED');`
  The second condition matters: a license may have been issued but not yet activated.
- The local development database is baselined past `V15`, so the column has to be added by hand there (same as `V14`). Adding an enum value backed by a CHECK-constrained column needs its migration in the same change (recurring trap, see PROGRESS).

### 4.2 Cloud enforcement
All of it is skipped in the Hub itself through `ember.deployment-mode.enforced` (default `true`, `false` in `application-hub.yml`), the same pattern as `ember.plans.enforced`. The Hub's own seeded restaurant row should carry `HUB` (native `insertWithId` sets it) so the data is not misleading.

| Where | Change |
|---|---|
| `AuthService.login`, `loginWithPin` | after credentials are verified, a `HUB` restaurant gets the same `BadCredentialsException("Invalid credentials")` as a wrong password, so the response does not reveal who is a Hub customer |
| `SecurityConfig` tenant gate (the filter that already answers 403 for a non-`ACTIVE` restaurant) | also 403 when `mode == HUB`, with an explicit detail ("not available on Ember Web"). Tokens are validated on every request, so an old token stops working the moment the mode changes |
| `JwtChannelInterceptor` (STOMP CONNECT) | reject a `HUB` restaurant. It checks no restaurant state today, so without this an old token could keep a websocket for up to its lifetime (24 h by default) |
| `SessionService.bindResolvedTenant` (QR / guest join) | same "not accepting orders" denial as a suspended restaurant |
| `PublicRestaurantController` (`/public/restaurants/{slug}/branding`) | 404 for `HUB` |
| `PlatformRestaurantService.issueHubLicense` | 409 unless mode is `HUB` |
| `HubActivationService.activate` | reject unless mode is `HUB` |
| `HubHeartbeatService.heartbeat` | status = `MIGRATED` if mode is `CLOUD`; otherwise `SUSPENDED` if the restaurant is not `ACTIVE`; otherwise `OK` (the answer is already RSA-signed, so no protocol change) |

Print agents in the cloud go through the same tenant gate (their tokens carry the tenant), so a `HUB` restaurant's agents are refused there too.

### 4.3 Console (`/console`)
- **Create** (`ConsoleRestaurantCreate`, `PlatformRestaurantCreateRequest`): required `deploymentMode` selector, no default.
- **List and detail** (`ConsoleRestaurants`, `ConsoleRestaurantDetail`, the summary/detail DTOs): a mode badge, plus a filter in the list.
- **Change mode:** `PATCH /platform/restaurants/{id}/mode` with `{ mode, confirmSlug }` (the server also checks the typed slug), implemented as `PlatformRestaurantService.updateDeploymentMode` in one transaction with a `PlatformAuditLog` row `RESTAURANT_MODE_CHANGED` (old → new). A no-op (same mode) is rejected. The dialog lists the consequences and shows "Plan actual: X — asigna el plan acordado después del cambio".
- `platformApi.ts` and `backend-types.ts` are updated (OpenAPI regeneration, as in report 505).
- Effects: `CLOUD→HUB` — cloud access ends immediately and the cloud data is left untouched and unreachable; `HUB→CLOUD` — the Hub learns it at its next heartbeat (≤ 5 min) and the cloud restaurant is empty.

### 4.4 Hub: read-only mode
- `HeartbeatScheduler` (after the existing signature check) handles `MIGRATED`: `LicenseService.recordMigrated(state)` stamps `HubState.migratedSince` once (idempotent, like `recordSuspended`). An `OK` heartbeat clears it (like `suspendedSince`), so flipping the mode back resumes the Hub.
- `HubState` gains `migratedSince`. The HMAC canonical string must only append that field when it is non-null, so the MACs of state files already written by installed Hubs stay valid (adding it unconditionally would fail-close every existing install until the next heartbeat).
- `LicenseService.isMigratedGraceExpired(state)` reuses the 48 h `ember.hub.suspended-grace-hours`.
- Once expired, an interceptor answers **403** `{"error":"license_migrated","message":"Tu restaurante ahora usa Ember Web. Este Hub está en modo consulta."}` to every write (POST/PUT/PATCH/DELETE) except `/auth/**`; reads, the Excel export and logout are untouched. Websocket SEND frames are not covered (documented limitation).
- `DefaultHubOrchestrator.toLicenseSnapshot` reports `MIGRATED`; `LicenseCard.tsx` shows "Migrada a Web (solo lectura)".
- Old Hubs (≤ 0.2.9) ignore the unknown status (they log it) and simply stop receiving `OK`, so their sales/payments block after the existing 4-day offline grace: they degrade safely.

### 4.5 Rollout
1. Deploy the cloud (`V15` + code). Backfilled modes are correct, so nothing changes until an operator uses the new action.
2. Release the Hub with the read-only mode. Backend and Hub version bump decided in the plan.
3. Nothing else is coupled: cloud-first is the only ordering rule.

## 5. Testing
- Backend: login/PIN, old token (gate), websocket CONNECT, QR join and public slug refuse a `HUB` restaurant and still serve `CLOUD`; the Hub profile ignores the flag; license issuance/activation require `HUB`; heartbeat returns `MIGRATED`/`SUSPENDED`/`OK` in that precedence; `updateDeploymentMode` (audit row, slug confirmation, same-mode rejection); migration backfill (activated, license-issued-only, neither).
- Hub: `MIGRATED` is recorded, grace elapses, writes are blocked and reads/export/login are not, an `OK` heartbeat resumes, old MACs stay valid, a forged unsigned `MIGRATED` is ignored.
- Frontend: Console create requires the selector; list badge/filter; the change-mode dialog (slug confirmation, plan reminder); Hub `LicenseCard` state.

## 6. Risks and open items
- **Operator mistake** (wrong restaurant or direction): confirmation by typed slug, audit log, and full reversibility (both sides keep their data).
- **Restaurants created before this with a license but no activation** are covered by the backfill's second condition.
- **Websocket window:** covered by the CONNECT check; its lifetime bound is 24 h otherwise.
- **Retention** of the cloud data left behind when a restaurant moves to Hub: deferred; needs a policy.
- **A future importer** stays out until there is a real customer and its own threat model (flat CSV, small limits, the same services as manual entry).

## 7. Likely files (for the plan)
Backend: `Restaurant`, new `DeploymentMode`, `V15`, `AuthService`, `SecurityConfig`, `JwtChannelInterceptor`, `SessionService`, `PublicRestaurantController`, `PlatformRestaurantService/Controller/DTOs`, `HubActivationService`, `HubHeartbeatService`, `HeartbeatScheduler`, `HubState`, `HubStateStore`, `LicenseService`, `GracePeriodInterceptor` (or a new read-only interceptor) + `HubWebConfig`, `DefaultHubOrchestrator`, `application-hub.yml`, `HubProvisioningRunner`/`RestaurantRepository.insertWithId`.
Frontend / Hub UI: `ConsoleRestaurantCreate`, `ConsoleRestaurants`, `ConsoleRestaurantDetail`, `platformApi.ts`, `backend-types.ts`, locales; `ember-hub/ui` `LicenseCard.tsx` and its types.
