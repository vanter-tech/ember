# Report 522 — HUB-PRINT-2: receipts print on the requesting caja's printer

## 1. Identification
- **Report number:** 522
- **Task ID:** HUB-PRINT-2
- **Predecessor:** HUB-PRINT-1 (report 521, branch `feat/hub-print-agent-server`); branched from `main`, so it does not contain that change

## 2. Objective
With several cajas, each with its own agent and printer, a receipt must print on the printer of the caja that asked for it — not on every active `RECEIPT` printer of the restaurant (`PrintDispatchService` routed by role only).

## 3. Modified Files
- `backend/src/main/resources/db/migration/V14__print_job_target_agent.sql` (new)
- `backend/src/main/resources/application-hub.yml`
- `backend/src/main/java/com/vanter/ember/config/WebSocketSessionAttributes.java`
- `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentHandshakeInterceptor.java`
- `backend/src/main/java/com/vanter/ember/printing/config/PrintAgentChannelInterceptor.java`
- `backend/src/main/java/com/vanter/ember/printing/model/PrintJob.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PrintAgentConnectionRegistry.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PrintDispatchService.java`
- `backend/src/main/java/com/vanter/ember/printing/service/BillReceiptPrintService.java`
- `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java`
- `backend/src/main/java/com/vanter/ember/printing/service/SourceIps.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/service/PrintTargetResolver.java` (new)
- Tests: `SourceIpsTest`, `PrintAgentConnectionRegistryTest`, `PrintTargetResolverTest`, `PrintAgentHandshakeInterceptorTest` (new); `PrintDispatchServiceTest`, `BillReceiptPrintServiceTest`, `PrintingEventListenerTest`, `PrintAgentChannelInterceptorTest` (extended)
- `PROGRESS.md`, `reports/522-task-hub-print-2-receipt-routing-by-source-ip.md`

## 4. What Changed?
- **Where an agent comes from.** The `/ws/print-agent` handshake now stamps the peer address on the session; on CONNECT the channel interceptor passes it to `PrintAgentConnectionRegistry`, which keeps a normalized address per connected agent and drops it on disconnect (a stale session's disconnect no longer removes a newer connection). `SourceIps.normalize` makes `127.0.0.1`, `::1` and any of the machine's own addresses equal, so a browser and an agent on the Hub's own PC match however they reached it.
- **Who asked.** `PrintTargetResolver.resolveForCurrentRequest` reads the current HTTP request (`RequestContextHolder`) and returns the single connected agent at that address that has an active printer for the role. Zero matches, more than one agent on the PC, an agent without a printer for the role, no web request, or the feature disabled all return empty.
- **Both receipt paths use it:** the manual "print/reprint receipt" (`BillReceiptPrintService`) and the automatic receipt on `PaymentCompleted` (`PrintingEventListener`; the event is published synchronously inside the payment request, so the requester's address is available). Kitchen tickets are untouched.
- **Dispatch.** `PrintJob.targetAgentId` (new nullable column, `V14`). A targeted job is sent only to that agent's printers; if that agent is offline the job stays `PENDING` and goes out when it reconnects (no duplicate on another caja); if the target no longer has a printer for the role, it falls back to the role's printers rather than stay stuck.
- **Switch.** `ember.printing.route-by-source-ip` defaults to `false`; `application-hub.yml` turns it on. The cloud sits behind proxies where a source address means nothing.

## 5. Why It Changed?
Restaurants will have several printers and each caja should print on its own, but jobs carried only a role, so every receipt would print on all cajas. Routing by the requester's LAN address needs no per-user or per-device configuration: the waiter has no Settings screen, and the browser and its agent already arrive from the same PC.

Verification: backend `./mvnw test` **1385/1385** (85 in the printing package). Not verified on real hardware (two cajas with printers on a Hub).

**Limits (accepted):** a phone/tablet or any device without an agent still prints on all `RECEIPT` printers (old behavior); two agents on one PC are treated as ambiguous; there is no automatic fallback when a caja's agent is offline. **Local dev DB:** it is baselined past `V14`, so run `ALTER TABLE print_jobs ADD COLUMN IF NOT EXISTS target_agent_id uuid;` by hand there. Prod Flyway applies `V14` on the next deploy (idempotent).
