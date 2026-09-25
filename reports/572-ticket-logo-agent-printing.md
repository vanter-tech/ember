# Report 572 — TICKET-LOGO-AGENT-PRINTING

## 1. Identification
- **Report number:** 572
- **Task ID:** TICKET-LOGO-AGENT-PRINTING (commit 2 of 2; branch `feat/ticket-logo`)
- **Predecessor:** report 571 (TICKET-LOGO-UPLOAD)

## 2. Objective
Make the print agent print the restaurant's receipt logo above the bill ticket, on every connection type it supports.

## 3. Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/`: `TicketLogoClient.java` (new), `TicketLogoRenderer.java` (new), `AgentConnection.java`, `PrintJobHandler.java`, `PrintJobDispatcher.java`, `NetworkPrinterSender.java`, `UsbPrinterSender.java`, `WindowsPrintQueueSender.java`
- Tests: `TicketLogoClientTest`, `TicketLogoRendererTest`, `PrintJobLogoTest` (new), `PrintJobHandlerTest`
- `printing-agent/VERIFY.md` (new manual item 14)

## 4. What Changed?
- `PrintJobPayload` gains `logo` (a 3-arg constructor is kept; a message without the field deserializes to `false`, so a newer agent works against an older backend).
- `PrintJobHandler` downloads the logo only when the job carries `logo=true`, via `TicketLogoClient`: one conditional request per ticket with `ETag`/304 and an in-memory copy; 404 forgets the copy; any failure falls back to the cached copy or to no logo. It never throws, so a logo problem cannot cost a customer their ticket.
- `PrintJobDispatcher.dispatch(..., logoPng, ack)` hands the bytes to the senders. `NETWORK`, `USB` and `WINDOWS_QUEUE` in `RAW` mode print the logo as an ESC/POS raster image (`GS v 0`) centered, then send `ESC a 0` to restore left alignment (the raster wrapper otherwise leaves the whole ticket centered), and the text follows. `WINDOWS_QUEUE` in `DRIVER` mode draws it centered at the top of the page, scaled from printer dots to page points. An undecodable image is skipped. The old `print(printer, payload)` signatures remain and delegate with no logo.

## 5. Why It Changed?
Second half of the ticket-logo feature: the backend (r571) already stores, dithers and flags the logo; the agent is the only component that can actually put it on paper.

Verification: `printing-agent` `mvnw test` 93/93 (new tests cover the raster bytes appearing before the text over a real TCP socket, alignment restore, undecodable image, ETag/304/404/offline behavior, payload compatibility with and without the flag, and the handler fetching the logo only when flagged). **Not verified on a real printer** — manual item 14 in `VERIFY.md` covers it; dot density other than ~203 dpi may need the width constants adjusted.

Rollout: deploy the backend (`deploy.sh`), then build and publish the agent installer (manual, `publish-installer.sh`). Until an updated agent is installed tickets print text-only exactly as before.
