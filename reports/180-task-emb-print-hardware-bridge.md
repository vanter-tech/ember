# Report 180 — EMB-PRINT (Hardware Bridge for real ESC/POS printing)

## Identification
- **Report number:** 180
- **Task ID:** EMB-PRINT (backlog PRINT-01..08, executed via the 15-task implementation plan in `docs/superpowers/plans/2026-08-22-emb-print.md`)
- **Predecessor Task:** refactor-category-menu-modals-grid (report 179)

**Process note:** this backlog was implemented across 15 granular commits on branch `emb/print` (one per plan task), each independently TDD-verified, but no per-task report was written as each landed — a process gap versus CLAUDE.md §4's per-task report requirement. This single report consolidates all 15 instead of fabricating retroactive per-task ones. `PROGRESS.md`'s Task Queue was also left unchecked during implementation and is corrected alongside this report.

## Objective
Give Ember real thermal-printer output. Until now, the admin "Ticket" settings subtab only rendered a preview — no code path sent bytes to a physical printer. This was gap #1 from the 2026-08-22 SaaS feature audit (`docs/superpowers/specs/vanter_hub.md`) and a prerequisite for Vanter Hub, since both the cloud SaaS and the future offline Hub need to share the same printing mechanism.

## Modified Files
**Backend (`backend/src/main/java/com/vanter/ember/printing/`, new module):**
- `model/`: `PrintAgent`, `PrinterConfig`, `PrintJob`, `PrintAgentStatus`, `PrinterRole`, `ConnectionType`, `PrintJobStatus`, `PrintJobSourceType`
- `repository/`: `PrintAgentRepository`, `PrinterConfigRepository`, `PrintJobRepository`
- `service/`: `PrintAgentService`, `PrinterConfigService`, `PrintDispatchService`, `PrintJobQueryService`, `PrintAgentConnectionRegistry`
- `controller/`: `PrintAgentAuthController`, `PrintAgentAdminController`, `PrintAgentAckController`, `PrintJobController`, `PrintAgentSelfController`
- `config/`: `PrintAgentWebSocketConfig`, `PrintAgentChannelInterceptor`
- `listener/`: `PrintingEventListener`
- `event/`: `PrintAgentConnected`
- `dto/`: request/response records for all of the above
- `backend/src/main/resources/db/migration/V12__printing.sql`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java` (permit `/printing/agents/token`, `/printing/agents/me/**`)
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` (new routes added to the 401 matrix)
- Corresponding test files under `backend/src/test/java/com/vanter/ember/printing/`

**Frontend:**
- `frontend/src/lib/api.ts` (`printingService`), `frontend/src/lib/backend-types.ts` (regenerated)
- `frontend/src/pages/admin/components/settings/PrintingSettings.tsx` (new "Impresoras" tab)
- `frontend/src/pages/admin/components/settings/printing/CreateAgentModal.tsx`, `AddPrinterModal.tsx` (new)
- `frontend/src/pages/admin/Settings.tsx`, `frontend/src/components/SettingsBar.tsx`, `frontend/src/store/uiStore.ts`
- `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts` (new `printing*` keys)

**New standalone project `printing-agent/`** (separate Maven build, not part of the backend reactor):
- `pom.xml`, `.gitignore`
- `src/main/java/com/vanter/emberagent/`: `AgentConfig`, `AuthClient`, `PrinterConfigClient`, `AgentConnection`, `NetworkPrinterSender`, `UsbPrinterSender`, `AckSender`, `Main`
- `src/main/resources/agent.properties.example`
- Corresponding tests under `src/test/java/com/vanter/emberagent/`

**Docs:**
- `docs/superpowers/specs/vanter_hub.md`, `docs/superpowers/specs/2026-08-22-emb-print-design.md`, `docs/superpowers/plans/2026-08-22-emb-print.md` (new)
- `CLAUDE.md`, `PROGRESS.md` (task-pool policy update, this report's checkbox/status correction)

## What Changed?

**Backend module (`printing`).** `PrintAgent` (a registered local Hardware Bridge process, API key hashed with the existing `PasswordEncoder`), `PrinterConfig` (a physical printer, `role` KITCHEN/RECEIPT, `connectionType` NETWORK/USB, owned by one agent — a role can have N printers across N agents), and `PrintJob` (structured/plain-text payload, never raw ESC/POS bytes — the agent does that conversion locally, keeping the backend printer-model-agnostic). Existing domain events `PaymentCompleted` and `KitchenItemsConfirmed` are consumed as-is by the new `PrintingEventListener`, gated on `RestaurantSettings.hardware.autoPrintTickets`/`printCustomerReceipt` — no changes to either event's publisher.

**Isolated agent channel.** Agents authenticate with a static API key exchanged for a short-lived JWT (`POST /printing/agents/token`, `permitAll`), then connect to a **second, fully isolated STOMP endpoint** `/ws/print-agent` with its own `PrintAgentChannelInterceptor` — the existing tenant-facing `/ws` endpoint and its `JwtChannelInterceptor` are untouched, mirroring the isolation precedent set by the `/platform/**` console (EMB-PC).

**Circular bean dependency, found and fixed.** Wiring `PrintDispatchService` (which needs `SimpMessagingTemplate`) directly into `PrintAgentChannelInterceptor` (which lives inside a `WebSocketMessageBrokerConfigurer`) created a genuine circular dependency: `SimpMessagingTemplate`'s creation transitively needs every `WebSocketMessageBrokerConfigurer` to exist first, one of which needs the interceptor, which needed the dispatch service, which needed `SimpMessagingTemplate`. Fixed by having the interceptor publish an internal `PrintAgentConnected` event (`ApplicationEventPublisher`) instead of calling the dispatch service directly; `PrintDispatchService.onPrintAgentConnected` (`@EventListener`) reacts to it. Caught by `E2EOrderFlowTest` failing to load the full application context — not by any unit test in isolation.

**Migration numbering surprise.** The plan assumed `V11` was free (last file on disk was `V10`), but the real dev Postgres still had a successful `flyway_schema_history` row for `V11` from the EMB-ACC backlog (built, then reverted via `git reset --hard` per an earlier session — the revert removed the migration file but never rolled back the database). Boot failed with a checksum mismatch, then with "Detected applied migration not resolved locally: 11" once the stale compiled copy in `target/classes` was also cleared. Resolved via `./mvnw flyway:repair` (adds a `DELETE`-type row marking V11 resolved — touches only Flyway's own metadata table, no application data) and renaming the new migration to `V12__printing.sql`.

**Local agent (`printing-agent/`).** A separate Maven project (own `pom.xml`, own `.gitignore`) — deliberately not a module of `backend/`'s single-module POM. Fetches its own printer list from a new agent-facing endpoint (`GET /printing/agents/me/printers`, also `permitAll` — safe because `agentId` is derived strictly from the signed JWT the caller presents), connects over STOMP via `spring-websocket`/`spring-messaging` (no full Spring Boot), renders ESC/POS via `escpos-coffee` (verified real Maven Central coordinates `com.github.anastaciocintra:escpos-coffee:4.1.0` — class signatures checked directly against the downloaded jar), and dispatches over a raw TCP socket (`TcpIpOutputStream`, network printers) or serial (`jSerialComm`, USB printers). Packaged as a runnable shaded jar (`mvn clean package` → `target/printing-agent-0.1.0-SNAPSHOT.jar`, `Main-Class` verified in the manifest).

**Admin UI.** New "Impresoras" subtab under Facturación (alongside the existing Ticket subtab): agent list with connection status, "Generar agente" flow showing the plaintext API key exactly once, per-agent printer management, and a recent-jobs panel with a retry action on `ERROR` jobs. Wired through the existing `useUIStore` modal/settings pattern (`CREATE_PRINT_AGENT`/`ADD_PRINTER` modal types, `PRINTING` settings type), not a bespoke local-state modal.

## Why It Changed?
Requested by the user as gap #1 of the SaaS feature-gap initiative (brainstormed 2026-08-22, see `docs/superpowers/specs/vanter_hub.md`): no restaurant can print a real receipt or kitchen comanda today, cloud or offline. Full brainstorming → spec → plan → implementation cycle, matching this repo's established convention for backlogs of this size (EMB-CR, EMB-RV, EMB-i18N).

## Verification
- Backend: `cd backend && ./mvnw test` — full suite green, including new `printing` module tests (repository, service, controller, listener, dispatch) and `SecurityAuditTest`'s expanded 401 matrix. Full-context boot verified via `E2EOrderFlowTest` (this is what caught the circular-dependency bug).
- Frontend: `cd frontend && pnpm run build` (`tsc -b && vite build`) — clean.
- Agent: `cd printing-agent && mvn test` / `mvn clean package` — clean, produces the runnable jar.
- No `claude-in-chrome` browser tool available this session — the "Impresoras" UI was not click-through tested, consistent with prior sessions' disclosed gaps.
- PRINT-07 (physical printer end-to-end) is a manual step with no hardware available in this environment — documented in the plan, not attempted.

## Branch & PR
Implemented on `emb/print`, branched off `main` after PR #46 (`refactor/admin-modals-grid`) merged. Pushed and opened as **PR #47** (`emb/print` → `main`), not yet merged.
