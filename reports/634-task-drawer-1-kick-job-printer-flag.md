# Report 634

## 1. Identification
- **Report Number:** 634
- **Task ID:** EMB-DRAWER task-drawer-1 — backend cash-drawer kick print job + `cashDrawer` printer flag
- **Predecessor Task:** report 633 (LANDING-META-PIXEL). First task of EMB-DRAWER (spec `docs/superpowers/specs/2026-09-29-cash-drawer-design.md`, plan `docs/superpowers/plans/2026-09-29-cash-drawer.md`).

## 2. Objective
Give the backend a way to send a "open the cash drawer" job to the printing agent, delivered only to the printer the drawer is wired to, and never left to replay later.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V19__cash_drawer_kick_print_jobs.sql` (new)
- `backend/src/main/java/com/vanter/ember/printing/model/PrintJobSourceType.java`, `PrinterConfig.java`, `DrawerKickState.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/dto/CreatePrinterConfigRequest.java`, `UpdatePrinterConfigRequest.java`, `PrinterConfigResponse.java`, `PrintJobMessage.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PrintDispatchService.java`, `PrinterConfigService.java`, `CashDrawerKickService.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentSelfController.java`
- `backend/src/test/java/com/vanter/ember/printing/service/PrintDispatchServiceTest.java`, `PrinterConfigServiceTest.java`, `CashDrawerKickServiceTest.java` (new)
- `docs/superpowers/plans/2026-09-29-cash-drawer.md` (risk note corrected), `PROGRESS.md`

## 4. What Changed?
- **V19:** widens `print_jobs_source_type_check` with `CASH_DRAWER_KICK` (drop-if-exists + re-add, idempotent) and adds `printer_configs.cash_drawer boolean NOT NULL DEFAULT false`.
- **`PrinterConfig.cashDrawer`** flows through create/update requests (old-arity constructors kept so existing callers compile) and both response builders.
- **`PrintJobMessage.sourceType`** (extra record component, old constructors kept) is sent to the agent so it can tell a kick from a ticket; `PrintDispatchService.sendTo` fills it.
- **`PrintDispatchService.dispatch`:** for `CASH_DRAWER_KICK` only printers with `cashDrawer=true` are eligible, and a kick that cannot be delivered (no drawer printer, or agent offline) is marked `ERROR` immediately instead of `PENDING`, because `flushPendingFor` replays PENDING jobs on agent reconnect and would open the drawer long after it was requested.
- **`CashDrawerKickService`:** `kick(tenantId, sourceId)` saves (`saveAndFlush`, dispatching the managed copy) and dispatches a `RECEIPT`-role kick job; `stateOf(printJobId)` derives `NONE/OPENING/OPENED/FAILED` from the job (a `SENT` job with no ack after 15 s counts as failed so it can be retried).
- Four existing dispatch tests were updated to expect the message's new `sourceType` (real value instead of `null`).

## 5. Why It Changed?
The accountant must be able to open the drawer from Ember (spec). `PrintTargetResolver` only routes by source IP when `ember.printing.route-by-source-ip` is on (off by default), so in cloud mode role alone would send the pulse to every receipt printer — hence the explicit printer flag. The no-`PENDING` rule prevents a stale kick from firing on reconnect.

## 6. Verification
- `cd backend && ./mvnw test` — **1606/1606**, 0 failures (was 1540 at r548 plus other work since; +6 new tests here).
- TDD: new tests written first, confirmed failing to compile for the right reason (`CashDrawerKickService`, `DrawerKickState`, `cashDrawer` missing), then green.
- **V19 verified on real Postgres 16** (scratch container, V1…V19 applied in order; the dev DB was not touched): a `CASH_DRAWER_KICK` job inserts, an unknown `source_type` is rejected by the CHECK, `cash_drawer` is `NOT NULL DEFAULT false`, and re-running V19 is a no-op. The H2 test suite cannot exercise migrations, so this manual check is the only coverage.
- **Finding (not fixed, out of scope):** on that migrations-built schema `print_jobs_status_check` rejects `CANCELED` although `PrintJobStatus.CANCELED` exists and `PrintDispatchService.cancel` sets it. Prod may differ (Flyway not baselined); check `\d print_jobs` in prod.
- Not done: nothing exercises the kick end to end yet (the agent side is task-drawer-4); an agent built before task-drawer-4 must not receive kick jobs on a flagged printer (it would print the JSON).
