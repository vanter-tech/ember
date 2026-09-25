# Report 578 — TICKET-TIP-LINE-AND-PREVIEW-FIXES

## 1. Identification
- **Report number:** 578
- **Task ID:** TICKET-TIP-LINE-AND-PREVIEW-FIXES (commit 8; branch `feat/ticket-logo`)
- **Predecessor:** report 577 (TICKET-LOGO-UPLOAD-HARDENING)

## 2. Objective
The Settings ticket preview showed no opening hours, tax breakdown or tip although their switches were on; and the tip switch should add a hand-written "PROPINA:" line at the end of the receipt.

## 3. Modified Files
- Backend: `printing/service/ReceiptLayout.java`, `ReceiptRenderer.java`; tests `ReceiptLayoutTest`, `ReceiptRendererTest`
- Frontend: `pages/admin/components/settings/TicketSettings.tsx` (+ test), `locales/{es,en}/admin.ts`

## 4. What Changed?
- **Root cause (checked against the real settings, read-only):** the switches were on but there was nothing to show: no per-day schedule and only a closing time in Branding, `taxRate` 0 with no `taxRules`, and no suggested-tip percentages. The preview also computed tax from `taxRules` while the printed ticket uses `taxRate`, and the printed ticket never printed a tip at all.
- **Tip line:** with the tip switch on, the customer receipt now ends with a blank spacer and `PROPINA: ______` filled with underscores to the paper width (32 or 42 columns), the last line of the ticket, after the footer. Off = nothing. The old "suggested tip" row is gone from the preview.
- **Preview:** shows the `PROPINA:` line with a rule to write on; computes tax the way the printed ticket does (single tax rate, or the explicit rules when defined); when an active switch has no data behind it, it shows a muted italic line ("Horario: sin configurar", "Impuesto: sin configurar"), and the tax/hours switches show an amber hint saying where to configure it.
- The tip switch is now labeled "Línea de propina" with a description of what it prints.

## 5. Why It Changed?
Requested. The preview promised things the ticket couldn't print (or hid things without explanation), so paper and preview now agree, and missing configuration is explained.

Verification: backend `./mvnw test` 1596/1596 (+5); frontend `build` clean, `lint` 0 errors, `test:run` 244/245 (+4 new pass; the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not printed on a real printer and not checked visually. To see hours/tax on a real ticket the data must exist: opening AND closing time (or the per-day schedule) and a tax rate.
