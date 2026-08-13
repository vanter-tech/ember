# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** task-2.1a (Repair backend test-compile baseline, discovered while verifying task-2.1) — report 09
- **Current Active Task:** none — task-2.1 and task-2.1a complete; next up task-2.2
- **Predecessor Task:** task-2.1
- **System Health:**
    - Frontend (`pnpm run build`): PASSING (0 TS errors)
    - Frontend (`pnpm run lint`): RUNS (19 pre-existing errors/6 warnings unrelated to config, tracked in later tasks)
    - Backend (`./mvnw test`): test-compile CLEAN; 274/284 tests passing (6 failures/4 errors — pre-existing, see task-2.2–2.5, task-2.10)

## Active Context & Recent Decisions
- Monolith root confirmed at `ember/`.
- Stack: Java 17 + Spring Boot 3.5.14 (Backend) / React 19 + TypeScript + pnpm (Frontend).
- Product: Multi-tenant restaurant platform (collaborative cart, KDS, floor/waiter management, admin analytics).
- Kafka dependency in `pom.xml` ignored; Spring `ApplicationEventPublisher` used for internal synchronous events.
- Backend test-compile was broken suite-wide (dead `RestaurantTable`/`OrderItemAdded` refs from historical refactors); repaired in task-2.1a — see report 09. Prior "39 tests passing" health claim was stale/inaccurate.
- Pending backlog unified 2026-08-12: former `EMB-MT-*` multi-tenancy tasks merged into the main `task-2.x`/`3.x`/`4.x` queue (prefix retired); task-2.9, 2.10, 2.13, 3.4, 3.5 rewritten to be tenant-aware from the start.

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
- [ ] **task-2.2:** Fix `SessionController.getSession` response drift (missing `$.id`, NPE on participants) vs. `SessionControllerTest`.
- [ ] **task-2.3:** Update `OrderItemTest` enum coverage to include `DRAFT` status.
- [ ] **task-2.4:** Fix `ImageUploadServiceTest` image fixture (`No suitable ImageReader found`).
- [ ] **task-2.5:** Align WebSocket topic naming: code sends `/topic/session/{id}`, tests expect `/topic/sessions/{id}`.
- [ ] **task-2.6:** Add `@Transactional` boundaries to multi-write operations in `BillingService` and `PaymentService`.
- [ ] **task-2.7:** Ensure atomic execution of `allPaid == true` check in `PaymentService` to reliably trigger `PaymentCompleted`.
- [ ] **task-2.8:** Ensure transactional safety in `confirmDraftsForUser` to prevent orphan items when table lookup fails.
- [ ] **task-2.9:** Rewrite `confirmMyOrder` validation to assert the path `userId` AND the resolved tenant both match the authenticated JWT context (not a trusted path parameter alone).
- [ ] **task-2.10:** Flesh out `Restaurant` entity (name, slug, plan, status, timezone, currency) + `RestaurantRepository`/`RestaurantService`; update `RegisterRequest`/`AuthService` to create-or-join a `Restaurant` at registration and add its id as a `rid` JWT claim, binding every `User.restaurantId` explicitly (real fix — supersedes a narrow test-only patch).
- [ ] **task-2.11:** Build `TenantContextHolder` (from the JWT `rid` claim) and wire into `jwtAuthFilter`/`JwtChannelInterceptor`; remove `SettingsController`'s ad hoc `getRestaurantIdFromAuth()`.
- [ ] **task-2.12:** Fix `DashboardController`'s client-supplied `restaurantId` `@RequestParam` — derive tenant from `TenantContextHolder`, not client input (closes a live cross-tenant IDOR).
- [ ] **task-2.13:** Fix `joinSession` capacity check to read live `session.getMaxParticipants()` instead of a stale QR-JWT claim, and scope `QrTokenService`/`SessionRepository.findByJoinCodeAndStatus` by tenant in the same pass.
- [ ] **task-2.14:** Configure Hibernate `DISCRIMINATOR` multi-tenancy (`CurrentTenantIdentifierResolver`) and add `@TenantId` to `Category`, `MenuItem`, `Bill`, `BillSplit`, `Payment`, `RestaurantSettings`, `DiningTables`, `User`.
- [ ] **task-2.15:** Migrate: backfill `tenant_id` on existing rows, add `unique(tenant_id, name)` on `Category` and `unique(tenant_id, sessionId)` on `Bill`, add tenant indexes.
- [ ] **task-2.16:** Add cross-tenant isolation regression tests for every JPA repository.
- [ ] **task-2.17:** Add `tenantId` to `Session`/`KitchenOrder`; scope `SessionRepository`/`KitchenOrderRepository` custom queries by tenant, including fixing `KitchenService.findDisplay()`'s untenanted `findAll()`.
- [ ] **task-2.18:** Backfill migration for existing Mongo documents; add cross-tenant isolation regression tests for Mongo repositories.
- [ ] **task-3.1:** Externalize sensitive default credentials (DB, JWT secret, MinIO) from `application.yml` to `.env` variables.
- [ ] **task-3.2:** Remove unused `spring-kafka` dependency from `backend/pom.xml`.
- [ ] **task-3.3:** Extend `GlobalExceptionHandler` to catch `Exception.class` using standardized `ProblemDetail` format.
- [ ] **task-3.4:** Rewrite CORS and WebSocket origin config to be dynamic, supporting tenant-specific subdomains/headers (replacing the hardcoded `localhost:5173` origin in `WebSocketConfig`).
- [ ] **task-3.5:** Fix the login rate limiter's memory leak and proxy IP resolution, and rescope its buckets to `(tenantId + IP)` instead of IP alone, so one tenant's traffic can't throttle another's.
- [ ] **task-3.6:** Implement pagination for `KitchenController` and `MenuItemController` endpoints, tenant-scoped (sequence after task-2.14/2.17 land tenant filtering).
- [ ] **task-3.7:** Add minimum length and complexity rules to user registration password validation.
- [ ] **task-3.8:** Extend `SettingsPayload` with `PaymentGatewaySettings` (secret-reference pattern, never raw secrets), structured `BusinessHoursSettings`, and list-based `TaxRules`.
- [ ] **task-4.1:** Setup Vitest and React Testing Library for frontend unit tests.
- [ ] **task-4.2:** Audit frontend `api.ts` for client-supplied tenant-id usage; ensure `restaurantId` is only ever read from authenticated session state.
- [ ] **task-4.3:** Build tenant onboarding UX (subdomain/slug-based routing for the pre-login branding/landing page) — depends on task-3.4's dynamic origin config.
- [ ] **task-4.4:** Wire `Restaurant.plan`/`status` to a subscription-billing integration (billing the tenant, distinct from the existing diner-facing `billing` module).