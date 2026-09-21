# Report 534 — Restaurant deployment mode (Web vs Hub)

## 1. Identification
- **Report number:** 534
- **Task ID:** RESTAURANT-DEPLOYMENT-MODE (plan tasks 1–9)
- **Predecessor:** report 533 (FIX-FORM-MESSAGE-BODY, branch `fix/form-message-body`) — chronologically 532 → 533 → 534
- **Spec / plan:** `docs/superpowers/specs/2026-09-20-restaurant-deployment-mode-design.md`, `docs/superpowers/plans/2026-09-20-restaurant-deployment-mode.md`
- **Branch:** `feat/restaurant-deployment-mode` (not pushed)

## 2. Objective
Creating a restaurant in the Console always produced one cloud ADMIN account that worked on Ember Web **and** on the Hub. A restaurant must be either Web (`CLOUD`) or Hub (`HUB`): a Hub customer cannot use the web and vice-versa; only an operator can switch it; no data is migrated between modes; a Hub whose restaurant moved to Web becomes read-only ("modo consulta") after a 48 h courtesy.

## 3. Modified Files
- **Model/DB:** `restaurant/model/DeploymentMode.java` (new), `Restaurant.java`, `RestaurantRepository.java`, `db/migration/V15__restaurant_deployment_mode.sql` (new), `application-hub.yml`
- **Cloud gates:** `identity/service/AuthService.java`, `config/SecurityConfig.java`, `config/JwtChannelInterceptor.java`, `session/service/SessionService.java`, `restaurant/controller/PublicRestaurantController.java`
- **Licensing:** `licensing/service/HubActivationService.java`, `HubHeartbeatService.java`, `platform/service/PlatformRestaurantService.java`
- **Console backend:** `platform/controller/PlatformRestaurantController.java`, `platform/model/dto/{PlatformRestaurantModeUpdateRequest (new),PlatformRestaurantCreateRequest,PlatformRestaurantSummaryResponse,PlatformRestaurantDetailResponse}.java`, `restaurant/service/RestaurantService.java`
- **Hub:** `hub/license/{HubState,HubStateStore,LicenseService,ReadOnlyModeInterceptor (new)}.java`, `hub/sync/HeartbeatScheduler.java`, `hub/config/HubWebConfig.java`, `hub/control/{HubOrchestrator,DefaultHubOrchestrator}.java`
- **Console frontend:** `frontend/src/lib/platformApi.ts`, `pages/console/{ConsoleRestaurantCreate,ConsoleRestaurants,ConsoleRestaurantDetail}.tsx`
- **Hub UI:** `ember-hub/ui/src/lib/types.ts`, `components/LicenseCard.tsx`
- **Tests:** new/extended in every area above (list in the commits)
- `backend/pom.xml` (0.2.9 → 0.3.0), `PROGRESS.md`, `reports/534-restaurant-deployment-mode.md`

## 4. What Changed?
- **Data:** `restaurants.deployment_mode` (`CLOUD` default, CHECK `CLOUD|HUB`), idempotent `V15` with backfill: `HUB` when the restaurant has a Hub activation **or** a `HUB_LICENSE_ISSUED` audit row, else `CLOUD`. The Hub's own seeded restaurant (`insertWithId`) is `HUB`.
- **Cloud enforcement** (all skipped inside the Hub through `ember.deployment-mode.enforced=false`): staff login and PIN login of a Hub restaurant answer exactly like a wrong password; the JWT filter returns 403 for any token of a Hub restaurant (old tokens die the moment the mode changes); STOMP CONNECT, QR/code join and the public branding slug refuse it (403 / 403 / 404).
- **Licensing:** issuing a license and activating require `HUB` (409); the signed heartbeat returns `MIGRATED` (mode `CLOUD`) > `SUSPENDED` > `OK`.
- **Console:** mode is a required, no-default selector at creation; badge + filter in the list; detail page shows the mode and a "Cambiar modo" dialog (consequences per direction, "Plan actual" reminder, typed slug). `PATCH /platform/restaurants/{id}/mode` re-checks the slug server-side, rejects a no-op, and writes a `RESTAURANT_MODE_CHANGED` audit row in the same transaction; `GET ?mode=` filters. The Hub license button is disabled for Web restaurants.
- **Hub:** `HubState.migratedSince` (omitted from JSON while null); the MAC canonical string appends it only when set, so every `hub-state.json` already written by an installed Hub still verifies; `HeartbeatScheduler` records a *signed* `MIGRATED` once (courtesy clock is not reset); an `OK` heartbeat clears it. After `ember.hub.suspended-grace-hours` (48 h) `ReadOnlyModeInterceptor` answers 403 `license_migrated` to every POST/PUT/PATCH/DELETE except `/auth/**`, `/app/**`, `/actuator/**`, `/error`, `/ws/**`; reads and the Excel export keep working. The license card shows "Migrada a Web (solo lectura)".
- **Two defects found on the way and fixed inside the work:** (1) task 2 first read the mode from `User.restaurantId`, a LAZY proxy → `LazyInitializationException` (500) on every staff login outside a transaction; mocked unit tests could not see it, `RestaurantStatusEnforcementTest` did; it now goes through `RestaurantRepository` (commit `cde06f13`). (2) `/auth/login` is rate limited to 10 per IP across the whole shared Spring test context, so the new integration test mints its token with `JwtService` instead of logging in per test. A third, unrelated defect (`FormMessage` never rendering error text) was fixed separately in report 533.

## 5. Why It Changed?
So the cloud and the Hub stop sharing one credential: a Hub customer must not be reachable on the web and a Web customer must not get a Hub, the switch is a deliberate, audited operator action, and no customer-supplied data is ever imported into the shared multi-tenant database (security). Read-only mode lets the customer keep consulting and exporting their history after they leave the Hub without letting two systems of record diverge.

**Verification (all on this branch):** backend `./mvnw test` **1479/1479**; frontend `pnpm run test:run` **179/179**, `lint` 0 errors (15 pre-existing warnings), `build` clean; `ember-hub/ui` tests **30/30**, build clean. **V15 was run against a real Postgres 16** (scratch database, V1–V14 then V15, dropped afterwards): backfill gave activated=`HUB`, license-only=`HUB`, neither=`CLOUD`; a second run was a no-op; the CHECK rejected an invalid value; a row inserted without the column defaulted to `CLOUD`.

**Not verified (needs the real stack):** the end-to-end manual acceptance (Console create Web/Hub → web login refused for the Hub restaurant → switch mode → license card on a real installed Hub → write blocked after the courtesy, reads/Excel still working → switching back resumes). The interceptor registration in `HubWebConfig` has no integration test (the class only loads with the `hub` profile).

**Rollout:** 1) tag `v0.3.0`, deploy the **cloud first** (`./deploy/deploy.sh 0.3.0`; V15 runs by itself, prod Flyway is not baselined and V15 is idempotent; never pre-run it by hand); 2) then build and publish the Hub installer. The local dev DB is baselined past V15: add `ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS deployment_mode varchar(10) NOT NULL DEFAULT 'CLOUD';` by hand. Old Hubs (≤ 0.2.9) ignore `MIGRATED` and simply stop receiving `OK`, so their sales and payments block after the existing 4-day offline grace.

**Open / deferred:** retention policy for cloud data left behind when a restaurant moves to Hub; a future flat-CSV menu importer (medium term, own threat model); websocket SEND frames are not covered by read-only mode.
