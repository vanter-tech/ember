# EMB-PRINT — Hardware Bridge (Real ESC/POS Printing) — Design Spec

**Date:** 2026-08-22
**Backlog prefix:** `EMB-PRINT`
**Status:** Approved, pending implementation plan
**Related:** `docs/superpowers/specs/vanter_hub.md` §2.4 (this module is shared, unmodified, by the future Vanter Hub offline product)

## 1. Purpose

Ember has no real thermal-printer integration today. The admin "Ticket" subtab
(`RestaurantSettings.ticket`) only renders a **preview** of the receipt/kitchen
comanda inside a modal — there is no code path that sends bytes to a physical
printer. `HardwareSettings.autoPrintTickets`/`printCustomerReceipt` exist as
flags with no consumer.

The cloud backend cannot reach a printer inside a restaurant's LAN directly
(NAT/firewall), so this spec introduces a **Hardware Bridge**: a local agent
process, installed on a PC inside the restaurant, that holds an outbound
WebSocket connection to the backend, receives print jobs, and drives the
physical printer(s) over the local network or USB.

This is gap #1 from the SaaS feature audit (2026-08-22) and a prerequisite
for Vanter Hub — both the cloud SaaS and the future offline Hub reuse this
exact module so printing is never solved twice.

## 2. Scope decisions (confirmed with user)

1. **Agent authentication: static API key**, generated per agent from the
   admin UI, shown once. No pairing-code flow in v1.
2. **Printer roles: `KITCHEN` and `RECEIPT`**, each restaurant can configure
   an arbitrary number of printers per role (not 1:1).
3. **N printers per role → broadcast.** A `PrintJob` for a role prints on
   every active, configured printer of that role (duplicated), not routed
   selectively by menu category/station. Selective routing is deferred.
4. **Connection types: network (TCP:9100) and USB/serial**, both in v1.
5. **Multiple agent processes per restaurant are supported.** A restaurant
   may run more than one agent (e.g. one PC at the register, one in the
   kitchen) — required because a USB printer is only reachable from the PC
   it's physically plugged into. Each agent has its own name and API key.
6. **Communication channel: an isolated WebSocket endpoint dedicated to
   agents** (`/ws/print-agent`), separate from the existing `/ws` endpoint
   used by customer/waiter/kitchen/admin sessions. `JwtChannelInterceptor`
   (the existing WS auth) is **not modified** — agents are a fundamentally
   different actor type with no `User`/email, and touching shared WS auth
   code risks regressing every real user session. This mirrors the
   isolation precedent already set by the `EMB-PC` platform-operator
   console (`/platform/**`, its own filter chain, never touches
   `TenantContextHolder` directly).
7. **`PrintJob` payload is structured/plain text, not raw ESC/POS bytes.**
   The backend renders ticket content from the existing `TicketSettings`;
   the agent's own ESC/POS library does the byte-level formatting. This
   keeps the backend printer-model-agnostic.

## 3. Backend design

### 3.1 New module `printing` (mirrors `cashregister`'s package layout)

```
printing/
  model/       PrintAgent, PrinterConfig, PrintJob, PrinterRole, ConnectionType, PrintJobStatus, SourceType
  repository/  PrintAgentRepository, PrinterConfigRepository, PrintJobRepository
  service/     PrintAgentService, PrintDispatchService
  listener/    PrintingEventListener (@EventListener on PaymentCompleted / KitchenItemsConfirmed)
  controller/  PrintAgentAuthController (agent-facing), PrintAgentAdminController (admin-facing)
  config/      PrintAgentWebSocketConfig, PrintAgentChannelInterceptor
  dto/         request/response records
  package-info.java
```

### 3.2 Entities & migration (`V11__printing.sql`)

**`print_agents`**

| column | type | notes |
|---|---|---|
| `id` | `uuid PK` | |
| `tenant_id` | `uuid` | `@TenantId`, auto-filtered like every other tenant entity |
| `name` | `varchar(100)` | admin-chosen label, e.g. "Agente Caja" |
| `api_key_hash` | `varchar(255)` | BCrypt hash, same encoder already used for `User.password` — never stored plaintext |
| `status` | `varchar(20)` | `ACTIVE`, `REVOKED` |
| `last_seen_at` | `timestamp`, nullable | updated on token exchange and on WS CONNECT |
| `created_at` | `timestamp` | |

**`printer_configs`**

| column | type | notes |
|---|---|---|
| `id` | `uuid PK` | |
| `tenant_id` | `uuid` | `@TenantId` |
| `agent_id` | FK → `print_agents(id)` | real JPA `@ManyToOne` — `PrintAgent` carries no LAZY hazard like `User` does |
| `role` | `varchar(20)` | `KITCHEN`, `RECEIPT` |
| `connection_type` | `varchar(20)` | `NETWORK`, `USB` |
| `host`, `port` | nullable | used when `NETWORK` |
| `com_port` | `varchar(20)`, nullable | used when `USB` (e.g. `COM3`); entered manually by whoever installs the agent — no auto-discovery in v1 |
| `label` | `varchar(100)` | e.g. "Impresora Cocina 1" |
| `active` | `boolean` | soft on/off without deleting the row |

**`print_jobs`**

| column | type | notes |
|---|---|---|
| `id` | `uuid PK` | |
| `tenant_id` | `uuid` | `@TenantId` |
| `role` | `varchar(20)` | `KITCHEN`, `RECEIPT` |
| `source_type` | `varchar(20)` | `BILL_RECEIPT`, `KITCHEN_TICKET` |
| `source_id` | `varchar(64)` | `billId` or `sessionId` — plain reference, not a FK, same convention as `processed_by`/`opened_by` elsewhere |
| `payload` | `text` | rendered structured content (JSON: header/footer/paperWidth/lines) |
| `status` | `varchar(20)` | `PENDING`, `SENT`, `PRINTED`, `ERROR` |
| `attempts` | `int` | incremented on every dispatch attempt |
| `last_error` | `varchar(255)`, nullable | first failure reason, not a full log |
| `created_at`, `updated_at` | `timestamp` | |

### 3.3 Event flow

Both trigger events already exist and are **not modified**:

- `PaymentCompleted(sessionId, tableId, billId)` (billing) — fires once every
  `BillSplit` on the bill is `PAID`. Carries no `tenantId`; the listener
  resolves it via `TenantContextHolder.requireTenantId()`, same pattern as
  `LoyaltyAccrualListener`.
- `KitchenItemsConfirmed(tenantId, sessionId, tableNumber, confirmedItems)`
  (session) — already carries `tenantId` and the item list needed for the
  comanda body.

`PrintingEventListener` flow, per event:

1. Load `RestaurantSettings` via `SettingService.getSettings(tenantId)`.
2. Gate: kitchen tickets print whenever `hardware.autoPrintTickets` is
   `true`; the customer receipt additionally requires
   `hardware.printCustomerReceipt`. (Exact flag semantics confirmed against
   current code when the implementation plan is written — no other consumer
   exists today to cross-check against.)
3. If gated off, no-op.
4. Render `payload` from `ticket.headerMessage`/`footerMessage`/
   `paperWidth`/`showTaxBreakdown`/`showTip` plus the event's own data
   (bill totals for `RECEIPT`, `confirmedItems` for `KITCHEN`).
5. Persist `PrintJob(status=PENDING)`.
6. Call `PrintDispatchService.dispatch(job)`.

`PrintDispatchService.dispatch(job)`:

1. Look up active `PrinterConfig`s for `(tenantId, job.role)`.
2. None configured → leave `PENDING` (not an error — surfaced in the admin
   UI as "sin impresora configurada", distinct from a print failure).
3. Group by `agentId`. For each agent: if a live WS session exists (tracked
   in-memory by `PrintAgentChannelInterceptor`/a connect listener), send the
   job over `/topic/print-agent/{agentId}`; otherwise leave it queued.
4. On an agent's WS CONNECT, flush: resend every `PENDING` job whose target
   printers belong to that agent.
5. Agent replies via `/app/print-agent/ack` with
   `{jobId, printerConfigId, result: PRINTED|ERROR, error?}` per printer it
   holds for that role (broadcast means one ACK per printer).
6. `PrintJob` becomes `PRINTED` once every involved agent/printer has
   confirmed `PRINTED`; `ERROR` (storing the first `error`) if any printer
   reports failure. `attempts` increments per dispatch attempt, not per ACK.

### 3.4 Agent authentication & WS isolation

- `POST /printing/agents/token` — body `{apiKey}` (no tenant/user JWT
  required, this is the agent's own auth entry point). Looks up
  `PrintAgent` by hash comparison, rejects if `status != ACTIVE`, issues a
  short-lived JWT (~15–30 min) carrying `{agentId, tenantId}` claims, signed
  with the existing JWT infra. Updates `last_seen_at`.
- `PrintAgentWebSocketConfig` registers a **second** STOMP endpoint,
  `/ws/print-agent` (SockJS, same CORS policy as `/ws`), with its own
  `PrintAgentChannelInterceptor` — validates the agent JWT on CONNECT,
  rejects anything without valid `{agentId, tenantId}` claims. This
  interceptor never touches `UserDetailsService` and is not shared with the
  tenant-facing `/ws` pipeline.
- A revoked agent (`status=REVOKED`) is denied at the next token exchange;
  an already-issued JWT expires within the short TTL, forcing re-validation
  — same "re-check DB state per-request" convention already used for
  `User.active`/`Restaurant.status`.

### 3.5 Admin API surface

| Method & path | Role | Purpose |
|---|---|---|
| `POST /printing/agents` | ADMIN | create a `PrintAgent`, returns the plaintext API key **once** |
| `POST /printing/agents/{id}/regenerate-key` | ADMIN | invalidates the old key, returns a new plaintext key once |
| `PATCH /printing/agents/{id}` | ADMIN | rename, or set `status=REVOKED` |
| `GET /printing/agents` | ADMIN | list agents with `status`/`lastSeenAt`/live-connected flag |
| `POST /printing/agents/{id}/printers` | ADMIN | add a `PrinterConfig` under that agent |
| `PATCH /printing/printers/{id}` | ADMIN | edit/toggle `active` |
| `GET /printing/jobs?status=&role=` | ADMIN | recent jobs for diagnostics |
| `POST /printing/jobs/{id}/retry` | ADMIN, WAITER | re-dispatch an `ERROR`/stale `PENDING` job |

All routes added to `SecurityAuditTest`'s 401 matrix, per existing convention.

## 4. Agent design (separate project, not part of `backend/`)

- Standalone Java process (plain JAR, no Spring Boot required — keeps it
  light; final packaging choice made in the implementation plan).
- Config file holds: backend URL, API key, and its own `PrinterConfig` list
  is read from the backend at startup (`GET` via the same `/ws/print-agent`
  channel's REST auth token) — the agent does not need a local copy of
  printer config beyond what it fetches after authenticating.
- On startup: exchange API key for JWT → connect
  `/ws/print-agent` → subscribe `/topic/print-agent/{agentId}`.
- On job received: for every `PrinterConfig` it owns matching `job.role`,
  render `payload` to ESC/POS bytes (library choice: `escpos-coffee` or
  equivalent, decided in the implementation plan) and send:
  - `NETWORK`: raw TCP socket to `host:port` (typically `9100`).
  - `USB`: serial write to `comPort` via a Java serial library (e.g.
    `jSerialComm`).
- ACKs each printer's outcome independently back to the backend.
- Reconnects with backoff on disconnect; re-authenticates (new JWT) each
  time since the token is short-lived.

## 5. Admin UI

New "Impresoras" section (under Settings, alongside the existing Ticket
subtab):

- Agent list: name, status, connection indicator (live/last seen), actions
  (regenerate key, rename, revoke).
- "Generar agente" flow: name it → API key shown once in a copyable box
  with an explicit "won't be shown again" warning.
- Per-agent printer list: add/edit `PrinterConfig` (role, connection type,
  host+port or COM port, label, active toggle).
- Recent jobs panel: status, role, source, timestamp, retry button on
  `ERROR` rows.

## 6. Testing

- `PrintDispatchServiceTest`: mocks `SimpMessagingTemplate`/the in-memory
  agent-session map; verifies role→agent routing, the "no printer
  configured" no-op path, and the settings gate — no physical printer
  involved.
- `PrintingEventListenerTest`: verifies the gate logic against
  `HardwareSettings` combinations and correct `payload` rendering from
  `TicketSettings`.
- PRINT-07 (physical printer) stays a manual verification step, documented
  in its own task report — no CI hardware available.
- New routes added to `SecurityAuditTest`.

## 7. Deferred (explicitly out of scope for v1)

- Selective routing by menu category/station within a role.
- USB printer auto-discovery (COM port is entered manually).
- Pairing-code agent onboarding (static API key only).
- Any change to Vanter Hub itself — this module is designed to be consumed
  unmodified by the Hub later; the Hub's own packaging is out of scope here.

## 8. Task breakdown (reference — full task list lives in `PROGRESS.md`)

PRINT-01 contract & protocol · PRINT-02 backend module & queue · PRINT-03
wire existing events · PRINT-04 local agent (network+USB) · PRINT-05 admin
UI · PRINT-06 retry/offline queue · PRINT-07 physical printer e2e ·
PRINT-08 agent WS isolation/auth (this spec folds PRINT-08 into §3.4 as a
design decision rather than a separate mechanism bolted on afterward).
