# Report 571 — TICKET-LOGO-UPLOAD

## 1. Identification
- **Report number:** 571
- **Task ID:** TICKET-LOGO-UPLOAD (commit 1 of 2; branch `feat/ticket-logo`)
- **Predecessor:** report 570 (MODIFIER-COLORS-BRAND-ACCENT)

## 2. Objective
Let the restaurant admin upload a logo to be printed at the top of the customer bill ticket. This commit covers storing/serving the logo and flagging it in the print job; the agent that actually prints it is commit 2.

## 3. Modified Files
- Backend: `printing/logo/{TicketLogoProcessor,TicketLogoService,TicketLogoController}.java` (new), `printing/controller/PrintAgentLogoController.java` (new), `printing/dto/PrintJobMessage.java`, `printing/service/PrintDispatchService.java`
- Backend tests: `printing/logo/{TicketLogoProcessorTest,TicketLogoServiceTest,TicketLogoControllerTest}.java`, `printing/controller/PrintAgentLogoControllerTest.java`, `printing/service/PrintDispatchServiceTest.java`
- Frontend: `lib/api.ts` (`ticketLogoService`), `pages/admin/components/settings/{TicketLogoField.tsx,TicketLogoField.test.tsx,TicketSettings.tsx}`, `locales/{es,en}/admin.ts`

## 4. What Changed?
- **Processing** (`TicketLogoProcessor`): on upload the image is flattened onto white, capped at 1024 px and stored as PNG; when served it is scaled down (never up) to the paper width (384 dots for 58 mm, 576 for 80 mm, max 300 high) and Floyd-Steinberg dithered to 1-bit, since thermal heads have no grays. Because dithering happens at read time, changing the paper width needs no re-upload.
- **Storage** (`TicketLogoService`): one object per restaurant in the existing MinIO bucket, key `ticket-logos/{tenantId}.png` derived only from the tenant (never client input). The object's existence is the "has logo" flag, so no settings field is added and nothing can be tampered with through `PUT /settings`. Upload limit 2 MB; non-images rejected.
- **API**: `POST/DELETE/GET /settings/ticket-logo` (ADMIN; POST gated by `PlanGateService` STARTER "branding" like branding; GET returns the exact printed bitmap, 404 when none). Agent-facing `GET /printing/agents/me/ticket-logo` (agent JWT, tenant taken from the agent record, `ETag`/304, tenant context bound only while reading tenant-scoped settings).
- **Job flag**: `PrintJobMessage` gains `logo` (true only for `BILL_RECEIPT` jobs of a tenant with a logo); a 3-arg constructor is kept. Older agents ignore the unknown field (Spring's default Jackson mapper does not fail on unknown properties) and print text only, so rollout order is safe.
- **UI**: `TicketLogoField` in Settings → Ticket: preview of the logo as it prints, upload/replace/remove (independent of the form's Save), 2 MB client check, plan-gate toast. The preview refetches after saving settings (paper width may have changed).

## 5. Why It Changed?
Requested feature: admin-uploaded receipt logo. Server-side dithering keeps the agent simple, and existence-as-flag avoids trusting client-supplied keys.

Verification: backend `./mvnw test` 1564/1564 (one earlier full run aborted with a surefire fork error and passed unchanged on re-run); frontend `build` clean, `lint` 0 errors, `test:run` 229/230 (+5 new; the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not exercised end to end against a real MinIO or printer, and the Settings UI wasn't checked visually in a browser.
