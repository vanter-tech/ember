# Report 637

## 1. Identification
- **Report Number:** 637
- **Task ID:** EMB-DRAWER task-drawer-4 — printing-agent `DrawerKicker` + kick routing
- **Predecessor Task:** report 636 (task-drawer-3, receive/manual-open API). Tasks 1-3 still uncommitted at the time of writing.

## 2. Objective
Make the printing agent open the cash drawer when the backend sends a `CASH_DRAWER_KICK` job, on the printer flagged `cashDrawer`, without printing anything and without needless delay.

## 3. Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/DrawerKicker.java` (new)
- `printing-agent/src/main/java/com/vanter/emberagent/AgentConnection.java` (`PrintJobPayload.sourceType`)
- `printing-agent/src/main/java/com/vanter/emberagent/PrinterConfigClient.java` (`PrinterConfigDto.cashDrawer`)
- `printing-agent/src/main/java/com/vanter/emberagent/PrintJobDispatcher.java`, `PrintJobHandler.java`
- `printing-agent/src/test/java/com/vanter/emberagent/DrawerKickerTest.java` (new), `PrintJobDispatcherTest.java`, `PrintJobLogoTest.java`, `PrintJobHandlerTest.java`
- `PROGRESS.md`

## 4. What Changed?
- `DrawerKicker.kick(printer)` sends the ESC/POS drawer pulse `1B 70 00 32 FA` (pin 2, 100 ms on / 500 ms off) over TCP:9100 (`NETWORK`, synchronous connect with 5 s timeout, same rationale as `NetworkPrinterSender`) or the serial port (`USB`). `WINDOWS_QUEUE` throws "no es compatible con impresoras por cola de Windows" (acked as `ERROR`); any other type is rejected.
- **Bytes written by hand.** The plan used `EscPos#pulsePin`, but the test showed escpos-coffee 4.1.0 emits `1B 70 30 32 FA` (the pin as ASCII `'0'`). Epson accepts `m = 0/1/48/49`, but the classic `m = 0` is what generic/clone printers document, so the agent writes `ESC p 0 t1 t2` itself.
- `PrintJobPayload` gained `sourceType` and `PrinterConfigDto` gained `cashDrawer` (old-arity constructors kept; JSON from an older backend deserializes to `null`/`false`, covered by tests).
- `PrintJobDispatcher`: a job with `sourceType == "CASH_DRAWER_KICK"` goes to `kickDrawer`, which pulses only printers with `cashDrawer=true` and the job's role, acks `PRINTED`/`ERROR` per printer, and acks `ERROR` with a null printer id if none matches. It never prints. New 4-arg constructor injects the `DrawerKicker`; the 3-arg one still works.
- `PrintJobHandler`: a kick never fetches the ticket logo. The backend flags every job of a tenant with a logo, kicks included; the test showed the extra fetch delaying the handler by 5 s when the logo server does not answer.

## 5. Why It Changed?
The backend (task-drawer-1) now sends kick jobs only to the printer with the drawer; the agent must recognize them, pulse the drawer instead of printing a ticket, report the real outcome, and do it fast (the accountant is waiting at the counter).

## 6. Verification
- `cd printing-agent && mvn test` (the agent has no wrapper; its build script uses `mvn`) — **108/108**, 0 failures (baseline 99, +9: 4 `DrawerKickerTest`, 2 dispatcher, 2 JSON compat in `PrintJobLogoTest`, 1 handler). RED first: compile failure, then two assertion failures that led to the hand-written bytes and the handler fix.
- The network path is tested against a local `ServerSocket` (the pulse bytes arrive; no printable text).
- **Not verified (needs hardware):** that a real printer + drawer opens with `ESC p 0 50 250` (pin 2 vs pin 5, 12 V vs 24 V timing), the serial (`USB`) path, and that drawer-kick jobs work through a printer on its RJ11 kick-out port. Also `WINDOWS_QUEUE` printers are unsupported by design.
- **Rollout:** the agent (and the Hub sidecar, which reuses this build) must be rebuilt and published (`printing-agent/build-installer.ps1` → `deploy/publish-installer.sh agent <version> <path>`, manual) **before** any printer is flagged `cash_drawer`. An older agent ignores `sourceType` and would print the kick JSON on the receipt printer. Not done in this task.
