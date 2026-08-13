# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** task-3.8 (SettingsPayload: PaymentGatewaySettings/BusinessHoursSettings/TaxRules) — report 34
- **Current Active Task:** none — next up task-4.1
- **Predecessor Task:** task-3.7
- **System Health:** Frontend `pnpm run build` PASSING (0 TS errors, but see below — `/kitchen/orders` & `/catalog/items` now return `Page<T>`, frontend not yet updated); `pnpm run lint` runs with 19 pre-existing errors/6 warnings (tracked in later tasks); Backend `./mvnw test` 431/431 passing.

## Active Context & Recent Decisions
- Monolith root at `ember/`; Java 17 + Spring Boot 3.5.14 / React 19 + TS + pnpm. Product: multi-tenant restaurant platform (collaborative cart, KDS, floor/waiter management, admin analytics). task-3.2 deleted `spring-kafka` — Spring `ApplicationEventPublisher` is the only event bus, do not reintroduce a broker.
- task-3.3: `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` (mandatory — a bare `Exception.class` catch-all in a `@RestControllerAdvice` would outrank `DefaultHandlerExceptionResolver` and degrade 400s to 500). All handlers emit RFC 7807 via `problem(status, detail, path)`; the 500 path logs at ERROR with a `traceId` and NEVER echoes `ex.getMessage()`. Don't re-declare `@ExceptionHandler(MethodArgumentNotValidException.class)` — it's an override, so a duplicate is an ambiguous-mapping startup failure.
- task-2.15 introduced **Flyway** (`baseline-on-migrate`, existing schema = V1, migrations start at V2; disabled in tests, where H2 `create-drop` builds from entities). Every future schema change goes in `backend/src/main/resources/db/migration/` AND the entity mapping, so H2 tests and prod `ddl-auto=validate` agree.
- Mongo has no replica set/`MongoTransactionManager`, so `@Transactional` isn't viable there — use fail-fast validation-before-mutation ordering instead. `Session`/`KitchenOrder` carry `tenantId` (task-2.17, no `@TenantId` equivalent in Mongo — every finder is tenant-first); route session reads through `SessionService.findById`, never the bare repository. `config/MongoTenantBackfill` (task-2.18, idempotent `ApplicationRunner`) backfilled existing docs; Mongo isolation tests do NOT extend `AbstractTenantIsolationTest` (no ambient filter to defeat).
- task-3.4: `config/CorsProperties` (`ember.cors.*`) is the ONE origin policy — `CorsConfig` and `WebSocketConfig`'s `/ws` endpoint both read it. Tenant subdomains go in `allowed-origin-patterns`, never `allowed-origins` (`allowCredentials=true` makes `*` illegal). task-3.5's `AuthRateLimiterFilter` buckets on `(tenant, clientIp)`, tenant from the `Host` header's leading label; `X-Forwarded-For/-Host` only trusted when the peer matches `trusted-proxies` (empty by default).
- Hibernate `DISCRIMINATOR` multi-tenancy (task-2.14): `config/TenantIdentifierResolver` reads `config/TenantContextHolder` (ThreadLocal, bound/cleared in `jwtAuthFilter` + `JwtChannelInterceptor` from the JWT `rid` claim — task-2.11/2.12, never trust client-supplied tenant ids). `@TenantId` is on `Category`/`MenuItem`/`Bill`/`BillSplit`/`Payment`/`DiningTables`/`RestaurantSettings`; `User` is deliberately excluded (looked up by email pre-tenant-bind, see report 22). Tenant-isolation repo tests MUST extend `config/AbstractTenantIsolationTest` (task-2.16).
- task-3.1: `application.yml`/`application-dev.properties` hold NO credentials — they read `${VAR}` from the gitignored root `.env` (`spring.config.import`, `optional:`, `.env.local` wins last). Core secrets have NO fallback (fail-fast boot); add any new secret to `.env.example` too. Old secrets remain in git history — rotation still pending.
- task-3.6: `GET /kitchen/orders` and `GET /catalog/items` are paginated and now return `Page<T>`, NOT a bare array — **BREAKING for frontend `api.ts`**, deferred to task-4.2 by explicit user decision. `GET /kitchen/display` and single-entity GETs are unchanged.
- task-3.7 added password length/complexity validation on `RegisterRequest` (DTO-only, via existing `@Valid`). task-3.8 extended `SettingsPayload` (JSON column, so the DTO is the schema) with `paymentGateway` (secret-reference only — no field shaped to hold a raw secret), `businessHours`, and `billing.taxRules`, all additive so `BrandingSettings.tsx`/`billing.taxRate` are untouched.

## Task Queue Status
- [x] **task-1.1:** Fix `tsc -b` compilation errors (`TS6133`/`TS6192`) in frontend (`pages/kitchen/`, `ComandaView.tsx`, `Menu.tsx`, `ItemsFloatingIsland.tsx`, `Tables.tsx`).
- [x] **task-1.2:** Repair `eslint.config.js` by installing `eslint-plugin-prettier` or removing broken import.
- [x] **task-1.3:** Replace hardcoded WebSocket `localhost` URL in `store/websocket.ts` with environment variable resolution.
- [x] **task-1.4:** Deduplicate WebSocket subscriptions in `FloatingNav.tsx` and centralize `SESSION_CLOSED` handling in `store/websocket.ts`.
- [x] **task-1.5:** Fix WebSocket state handling by adding `onDisconnect`/`onStompError` handlers to reset `isConnected`.
- [x] **task-1.6:** Add Global Error Boundary to React app and fix missing `key` props and loading states in `OrdersDisplay.tsx`.
- [x] **task-1.7:** Remove leftover `console.log` and `console.error` statements from production frontend code.
- [x] **task-2.1:** Add `@Version` optimistic locking to MongoDB `Session.java` model to prevent collaborative cart race conditions.
- [x] **task-2.1a:** Repair backend test-compile baseline (dead `RestaurantTable`/`OrderItemAdded` refs, `Long`→`UUID` tableId in tests).
- [x] **task-2.2:** Fix `SessionController.getSession` response drift (missing `$.id`, NPE on participants) vs. `SessionControllerTest`.
- [x] **task-2.3:** Update `OrderItemTest` enum coverage to include `DRAFT` status.
- [x] **task-2.4:** Fix `ImageUploadServiceTest` image fixture (`No suitable ImageReader found`).
- [x] **task-2.5:** Align WebSocket topic naming: code sends `/topic/session/{id}`, tests expect `/topic/sessions/{id}`.
- [x] **task-2.6:** Add `@Transactional` boundaries to multi-write operations in `BillingService` and `PaymentService`.
- [x] **task-2.7:** Ensure atomic execution of `allPaid == true` check in `PaymentService` to reliably trigger `PaymentCompleted`.
- [x] **task-2.8:** Ensure transactional safety in `confirmDraftsForUser` to prevent orphan items when table lookup fails.
- [x] **task-2.9:** Rewrite `confirmMyOrder` validation to assert the path `userId` AND the resolved tenant both match the authenticated JWT context (not a trusted path parameter alone).
- [x] **task-2.10:** Flesh out `Restaurant` entity (name, slug, plan, status, timezone, currency) + `RestaurantRepository`/`RestaurantService`; update `RegisterRequest`/`AuthService` to create-or-join a `Restaurant` at registration and add its id as a `rid` JWT claim, binding every `User.restaurantId` explicitly (real fix — supersedes a narrow test-only patch).
- [x] **task-2.11:** Build `TenantContextHolder` (from the JWT `rid` claim) and wire into `jwtAuthFilter`/`JwtChannelInterceptor`; remove `SettingsController`'s ad hoc `getRestaurantIdFromAuth()`.
- [x] **task-2.12:** Fix `DashboardController`'s client-supplied `restaurantId` `@RequestParam` — derive tenant from `TenantContextHolder`, not client input (closes a live cross-tenant IDOR).
- [x] **task-2.13:** Fix `joinSession` capacity check to read live `session.getMaxParticipants()` instead of a stale QR-JWT claim, and scope `QrTokenService`/`SessionRepository.findByJoinCodeAndStatus` by tenant in the same pass.
- [x] **task-2.14:** Configure Hibernate `DISCRIMINATOR` multi-tenancy (`CurrentTenantIdentifierResolver`) and add `@TenantId` to `Category`, `MenuItem`, `Bill`, `BillSplit`, `Payment`, `RestaurantSettings`, `DiningTables` (`User` excluded — see report 22).
- [x] **task-2.15:** Migrate: backfill `tenant_id` on existing rows, add `unique(tenant_id, name)` on `Category` and `unique(tenant_id, sessionId)` on `Bill`, add tenant indexes.
- [x] **task-2.16:** Add cross-tenant isolation regression tests for every JPA repository.
- [x] **task-2.17:** Add `tenantId` to `Session`/`KitchenOrder`; scope `SessionRepository`/`KitchenOrderRepository` custom queries by tenant, including fixing `KitchenService.findDisplay()`'s untenanted `findAll()`.
- [x] **task-2.18:** Backfill migration for existing Mongo documents; add cross-tenant isolation regression tests for Mongo repositories.
- [x] **task-3.1:** Externalize sensitive default credentials (DB, JWT secret, MinIO) from `application.yml` to `.env` variables.
- [x] **task-3.2:** Remove unused `spring-kafka` dependency from `backend/pom.xml`.
- [x] **task-3.3:** Extend `GlobalExceptionHandler` to catch `Exception.class` using standardized `ProblemDetail` format.
- [x] **task-3.4:** Rewrite CORS and WebSocket origin config to be dynamic, supporting tenant-specific subdomains/headers (replacing the hardcoded `localhost:5173` origin in `WebSocketConfig`).
- [x] **task-3.5:** Fix the login rate limiter's memory leak and proxy IP resolution, and rescope its buckets to `(tenantId + IP)` instead of IP alone, so one tenant's traffic can't throttle another's.
- [x] **task-3.6:** Implement pagination for `KitchenController` and `MenuItemController` endpoints, tenant-scoped (sequence after task-2.14/2.17 land tenant filtering).
- [x] **task-3.7:** Add minimum length and complexity rules to user registration password validation.
- [x] **task-3.8:** Extend `SettingsPayload` with `PaymentGatewaySettings` (secret-reference pattern, never raw secrets), structured `BusinessHoursSettings`, and list-based `TaxRules`.
- [ ] **task-4.1:** Setup Vitest and React Testing Library for frontend unit tests.
- [ ] **task-4.2:** Audit frontend `api.ts` for client-supplied tenant-id usage (ensure `restaurantId` only from session state) AND update `kitchenService.getAllOrders`/`menuItemService.getAll` to consume the `Page<T>` envelope task-3.6 introduced.
- [ ] **task-4.3:** Build tenant onboarding UX (subdomain/slug-based routing for the pre-login branding/landing page) — depends on task-3.4's dynamic origin config.
- [ ] **task-4.4:** Wire `Restaurant.plan`/`status` to a subscription-billing integration (billing the tenant, distinct from the existing diner-facing `billing` module).