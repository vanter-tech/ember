# Report 573 — TICKET-LOGO-PREVIEW

## 1. Identification
- **Report number:** 573
- **Task ID:** TICKET-LOGO-PREVIEW (commit 3; branch `feat/ticket-logo`)
- **Predecessor:** report 572 (TICKET-LOGO-AGENT-PRINTING)

## 2. Objective
Show the receipt logo in the ticket preview in Settings → Ticket.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx`
- `frontend/src/pages/admin/components/settings/TicketSettings.test.tsx` (new)

## 4. What Changed?
- The customer-receipt preview renders the logo centered above the header message, as it prints (`max-h-24`, `max-w-full`). It reads the same `['ticketLogo']` query as `TicketLogoField`, so there is no extra request and an upload/remove/paper-width change is reflected right away. With no logo the preview is unchanged.
- The kitchen-ticket preview is untouched: kitchen tickets never carry the logo.
- 3 tests: logo shown in the customer preview, absent when none is set, never in the kitchen preview.

## 5. Why It Changed?
The preview should match what actually prints; it was missing the logo added in r571/r572.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 232/233 (+3 new pass); the 1 failure is the pre-existing `MenuJoin.test.tsx` (see r550). Not checked visually in a browser.
