# Report 575 — TICKET-BUSINESS-INFO

## 1. Identification
- **Report number:** 575
- **Task ID:** TICKET-BUSINESS-INFO (commit 5; branch `feat/ticket-logo`)
- **Predecessor:** report 574 (TICKET-LOGO-SMALLER)

## 2. Objective
The printed bill ticket showed no opening hours. Print them under the header, and optionally the RUC, address and phone (which the Settings preview already showed but the paper never printed).

## 3. Modified Files
- Backend: `printing/service/ReceiptBusinessInfo.java` (new), `ReceiptLayout.java`, `ReceiptRenderer.java`, `settings/model/SettingsPayload.java`; tests `ReceiptBusinessInfoTest` (new), `ReceiptLayoutTest`, `ReceiptRendererTest`
- Frontend: `lib/receiptBusinessInfo.ts` (+ test, new), `lib/backend-types.ts`, `pages/admin/components/settings/TicketSettings.tsx` (+ test), `locales/{es,en}/admin.ts`

## 4. What Changed?
- `TicketSettings` gains `showBusinessHours` (default **true**) and `showBusinessInfo` (default **false**); no migration (settings are JSON), so existing restaurants start printing their hours and nothing else changes.
- `ReceiptBusinessInfo` builds the block: opening hours from the per-day schedule, collapsed into runs of consecutive days with identical hours (`Lun-Vie 12:00-22:00`, `Sab 12:00-23:30`, `Dom cerrado`; a single run becomes `Horario: Lun-Dom 12:00-23:00`). If no per-day schedule exists it falls back to the Branding opening/closing range, else prints nothing. RUC / address / `Tel:` come first when the info toggle is on. Plain ASCII (no accents) for thermal code pages.
- `ReceiptLayout` gains `infoLines`, rendered centered and word-wrapped right under the header, never wider than the paper. `ReceiptRenderer` feeds them from the settings. Only the customer receipt is affected; the kitchen ticket is untouched.
- Settings → Ticket: two switches (hours, RUC/address/phone), saved with the form; the receipt preview now uses the same formatting (`receiptBusinessInfo.ts` mirrors the backend), so paper and preview agree. The preview no longer shows RUC/address/phone unconditionally.

## 5. Why It Changed?
The hours were configured in two places but never reached the paper; the preview also promised RUC/address/phone that were never printed.

Verification: backend `./mvnw test` 1575/1575 (+11 new); frontend `build` clean, `lint` 0 errors, `test:run` 240/241 (+8 new pass; the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not printed on a real printer and not checked visually in a browser.
