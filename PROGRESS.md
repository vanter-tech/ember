# PROGRESS.md — Active Execution State

## Current Execution State
- **Last Completed Task:** task-1.3 (Replace hardcoded WebSocket localhost URL) — report 03
- **Current Active Task:** task-1.4 (Deduplicate WebSocket subscriptions in FloatingNav.tsx)
- **Predecessor Task:** task-1.3
- **System Health:**
    - Frontend (`pnpm run build`): PASSING (0 TS errors)
    - Frontend (`pnpm run lint`): RUNS (19 pre-existing errors/6 warnings unrelated to config, tracked in later tasks)
    - Backend (`./mvnw test`): PASSING (39 tests passing)

## Active Context & Recent Decisions
- Monolith root confirmed at `ember/`.
- Stack: Java 17 + Spring Boot 3.5.14 (Backend) / React 19 + TypeScript + pnpm (Frontend).
- Product: Multi-tenant restaurant platform (collaborative cart, KDS, floor/waiter management, admin analytics).
- Kafka dependency in `pom.xml` ignored; Spring `ApplicationEventPublisher` used for internal synchronous events.

## Task Queue Status
- [x] **task-1.1:** Fix `tsc -b` compilation errors (`TS6133`/`TS6192`) in frontend (`pages/kitchen/`, `ComandaView.tsx`, `Menu.tsx`, `ItemsFloatingIsland.tsx`, `Tables.tsx`).
- [x] **task-1.2:** Repair `eslint.config.js` by installing `eslint-plugin-prettier` or removing broken import.
- [x] **task-1.3:** Replace hardcoded WebSocket `localhost` URL in `store/websocket.ts` with environment variable resolution.
- [ ] **task-1.4:** Deduplicate WebSocket subscriptions in `FloatingNav.tsx` and centralize `SESSION_CLOSED` handling in `store/websocket.ts`.
- [ ] **task-1.5:** Fix WebSocket state handling by adding `onDisconnect`/`onStompError` handlers to reset `isConnected`.
- [ ] **task-1.6:** Add Global Error Boundary to React app and fix missing `key` props and loading states in `OrdersDisplay.tsx`.
- [ ] **task-1.7:** Remove leftover `console.log` and `console.error` statements from production frontend code.
- [ ] **task-2.1:** Add `@Version` optimistic locking to MongoDB `Session.java` model to prevent collaborative cart race conditions.
- [ ] **task-2.2:** Add `@Transactional` boundaries to multi-write operations in `BillingService` and `PaymentService`.
- [ ] **task-2.3:** Add unique constraint on `Bill.sessionId` to prevent duplicate billing.
- [ ] **task-2.4:** Ensure atomic execution of `allPaid == true` check in `PaymentService` to reliably trigger `PaymentCompleted`.
- [ ] **task-2.5:** Validate JWT `userId` against path parameter `userId` in `confirmMyOrder` endpoint.
- [ ] **task-2.6:** Fix `joinSession` participant limit validation to check live session capacity instead of stale JWT claims.
- [ ] **task-2.7:** Ensure transactional safety in `confirmDraftsForUser` to prevent orphan items when table lookup fails.
- [ ] **task-3.1:** Externalize sensitive default credentials (DB, JWT secret, MinIO) from `application.yml` to `.env` variables.
- [ ] **task-3.2:** Remove unused `spring-kafka` dependency from `backend/pom.xml`.
- [ ] **task-3.3:** Extend `GlobalExceptionHandler` to catch `Exception.class` using standardized `ProblemDetail` format.
- [ ] **task-3.4:** Configure dynamic CORS and WebSocket origins for production environments.
- [ ] **task-3.5:** Fix memory leak and proxy IP resolution in login rate limiter.
- [ ] **task-3.6:** Implement pagination for `KitchenController` and `MenuItemController` endpoints.
- [ ] **task-3.7:** Add minimum length and complexity rules to user registration password validation.
- [ ] **task-4.1:** Setup Vitest and React Testing Library for frontend unit tests.