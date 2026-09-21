# Report 532 — Receipts stayed "en cola" until the agent reconnected (dispatch without tenant id)

## 1. Identification
- **Report number:** 532
- **Task ID:** FIX-PRINT-DISPATCH-TENANT
- **Predecessor:** report 531 (stacked branch `fix/printing-ux`, not pushed)

## 2. Objective
After installing the rebuilt Hub and agent, printing a receipt still answered "Cuenta en cola (sin impresora conectada)" and only printed after re-pairing the agent with a code. Find the real cause.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/service/BillReceiptPrintService.java`, `KitchenTicketPrintService.java`, `PrintDispatchService.java`
- `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java`
- Tests: `PrintJobRepositoryTest`, `BillReceiptPrintServiceTest`, `KitchenTicketPrintServiceTest`, `PrintingEventListenerTest`, `PrintDispatchServiceTest`
- `PROGRESS.md`, `reports/532-fix-print-dispatch-tenant-id.md`

## 4. What Changed?
**Evidence (from the Hub database, not a guess).** Every receipt was created minutes before it printed, and the ones that printed did so together, 0.35 s apart, at the moment of a re-pair (`created 19:25:48 / 19:27:40 / 19:27:43`, all `updated 19:29:15.5–.9`). They all show `attempts = 1`. A job sent live is `SENT` with the ACK arriving within a second; these were flushed by the "agent connected" event, which reloads the pending jobs from the database. So **no live dispatch had ever worked** — the earlier "PRINTED, attempts 1" jobs (e.g. created 18:53:48, printed 18:54:40) were also flushes at a re-pair.

**Root cause (reproduced with a test).** `PrintJob` has an assigned id and no version, so Spring Data treats it as not new and *merges* it: on `saveAndFlush(job)` the `@TenantId` is filled on the managed **copy** that is returned, while the instance the caller passed in keeps `tenantId == null`. The three creators (`BillReceiptPrintService`, `KitchenTicketPrintService`, `PrintingEventListener`) ignored the returned copy and called `dispatch(job)` with the original; `dispatch` then looked up the printers of "tenant null", found none, and returned leaving the job `PENDING` — which the web shows as "en cola (sin impresora conectada)". An earlier comment in the code blamed a missing flush; the existing test only checked the returned value, so it never saw this. The agent, the WebSocket and the routing by IP were fine.

**Fix.**
- The three creators dispatch (and return) the instance `saveAndFlush` returns.
- `PrintDispatchService.dispatch` falls back to the tenant bound to the current request/event when the job has no tenant id.
- Tests: a repository test that documents the trap (the caller's instance stays without a tenant id); one test per creator whose `saveAndFlush` returns a *copy* and asserts that the copy is what gets dispatched; a dispatch test with a tenant-less job. All four failed before the fix.

## 5. Why It Changed?
A receipt must print the moment it is requested, not when someone re-pairs the agent. This also removes the wrong impression that the agent lost its connection.

Verification: backend `./mvnw test` **1429/1429**. Not yet checked on the real printer: install the rebuilt Hub, print a receipt with the agent already connected (no re-pairing) and expect "enviado" and paper within a second or two.

**Note:** the same code runs in the cloud, so it very likely had the same delay there (jobs going out only when an agent reconnects); worth confirming after the next backend deploy. Data left over from testing (old `PENDING` jobs aimed at deleted agents, printers of agents deleted before report 531) can be cleared from the admin ("Limpiar pendientes").
