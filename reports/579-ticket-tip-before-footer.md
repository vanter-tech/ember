# Report 579 — TICKET-TIP-BEFORE-FOOTER

## 1. Identification
- **Report number:** 579
- **Task ID:** TICKET-TIP-BEFORE-FOOTER (commit 9; branch `feat/ticket-logo`)
- **Predecessor:** report 578 (TICKET-TIP-LINE-AND-PREVIEW-FIXES)

## 2. Objective
The footer message ("Gracias por visitarnos") must be the last line of the receipt, with the handwritten `PROPINA:` line before it; and make clear where the opening hours come from.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/service/ReceiptLayout.java` (+ `ReceiptLayoutTest`, `ReceiptRendererTest`)
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx` (+ test), `frontend/src/locales/{es,en}/admin.ts`

## 4. What Changed?
- **Order:** `PROPINA: ______` (with its blank spacer) is now printed right after the totals and before the rule + footer, so the ticket ends `TOTAL … / PROPINA: ___ / ----- / Gracias por visitarnos`. The Settings preview follows the same order.
- **Hours:** the receipt already reads the "Horario de atención" tab (`businessHours.schedule`). Read-only check of the real settings shows that schedule is still **empty**: the tab displays a suggested 09:00–18:00 for every day, but nothing is stored until **Guardar** is pressed, so there is nothing to print (Branding only has a closing time). The hint under the hours switch now explains exactly that. Deliberately not done: printing an unsaved suggested schedule, since it could tell customers hours the restaurant never confirmed.

## 5. Why It Changed?
Requested layout; and the hours issue was missing data, not a missing link between the tab and the ticket.

Verification: backend `./mvnw test` 1596/1596; frontend `build` clean, `lint` 0 errors, `test:run` 244/245 (the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not printed on a real printer.
