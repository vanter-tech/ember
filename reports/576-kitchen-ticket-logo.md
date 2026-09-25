# Report 576 — KITCHEN-TICKET-LOGO

## 1. Identification
- **Report number:** 576
- **Task ID:** KITCHEN-TICKET-LOGO (commit 6; branch `feat/ticket-logo`)
- **Predecessor:** report 575 (TICKET-BUSINESS-INFO)

## 2. Objective
The kitchen ticket must carry the restaurant logo too, not only the bill receipt.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/service/PrintDispatchService.java`, `printing/dto/PrintJobMessage.java`, `printing/logo/TicketLogoController.java` (docs), `backend/src/test/java/.../PrintDispatchServiceTest.java`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx` (+ test), `frontend/src/locales/{es,en}/admin.ts`
- `printing-agent/VERIFY.md` (item 14d)

## 4. What Changed?
- `sendTo` flags `logo` on every job of a tenant that has a logo (previously only `BILL_RECEIPT`). The agent needed no change: it already prints the logo whenever the job is flagged, whatever the role.
- The kitchen-ticket preview in Settings renders the same logo image as the receipt preview (one shared `logoImage` element); the logo field description now says it applies to both tickets.
- Tests: the kitchen job is flagged when a logo exists and not when none is configured; the kitchen preview test flips from "never" to "shows the logo".

## 5. Why It Changed?
Requested. Kitchen tickets print on the same thermal hardware, so the same server-prepared bitmap works unchanged.

Verification: backend `./mvnw test` 1576/1576; frontend `build` clean, `lint` 0 errors, `test:run` 240/241 (the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not printed on a real printer.
