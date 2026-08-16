# Report 104 — docs-architecture-diagram

## 1. Identification
- **Report:** 104
- **Task ID:** docs-architecture-diagram
- **Predecessor Task:** bugfix-revert-frontend-dark-mode (report 103)

## 2. Objective
Produce a root-level `ARCHITECTURE.md` documenting the full monolith
architecture (backend modules, frontend, landing, PostgreSQL, MongoDB,
WebSockets, internal event bus) with Mermaid diagrams: a static system
connection diagram and a sequence diagram for the core order lifecycle.

## 3. Modified Files
- `ARCHITECTURE.md` (new, project root)

## 4. What Changed?
Added `ARCHITECTURE.md` containing:
1. A `flowchart TD` connection diagram: the React SPA and standalone Astro
   landing site as clients; REST + STOMP/SockJS edges into the backend; all
   9 backend modules (`identity, catalog, session, kitchen, billing,
   restaurant, settings, analytics, platform`) wired through an in-process
   `ApplicationEventPublisher`/`@EventListener` event bus; PostgreSQL vs
   MongoDB ownership per module; the in-memory STOMP broker fanning out to
   `/topic/session/{id}` and `/topic/kitchen/{tenantId}`.
2. A `sequenceDiagram` tracing the real event chain for one order lifecycle
   — join session → add/send items → kitchen order creation → KDS status
   transitions → bill request → payment → session close/order retirement —
   pulled directly from `SessionService`, `KitchenService`,
   `PaymentService`, and the `*WebSocketListener`/`*EventListener` classes
   (verified via grep, not inferred).
3. A data-store ownership table (PostgreSQL vs MongoDB, per module).

## 5. Why It Changed?
User requested a complete architecture/connection diagram for the monolith
to document how backend, frontend, landing, both databases, and WebSockets
fit together. Scoped via `superpowers:brainstorming` (bounded path): confirmed
with the user to produce two diagrams (connection + lifecycle), core systems
only (no ops/infra containers), and all 9 backend modules shown individually
rather than grouped, before writing the file.
