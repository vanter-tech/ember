# Report 580 — KITCHEN-REMOVE-VIEW-DETAILS-BUTTON

## 1. Identification
- **Report number:** 580
- **Task ID:** KITCHEN-REMOVE-VIEW-DETAILS-BUTTON
- **Predecessor:** report 579 (TICKET-TIP-BEFORE-FOOTER)

## 2. Objective
Remove the "Ver detalles" button from the kitchen order queue cards; it had no action.

## 3. Modified Files
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/locales/es/kitchen.ts`
- `frontend/src/locales/en/kitchen.ts`

## 4. What Changed?
Deleted the `CardFooter` holding the button (and its unused import) in `QueueCard`, and the now-unused `viewDetails` i18n key in both locales.

## 5. Why It Changed?
The button had no onClick handler or behavior; it was dead UI. `pnpm run build` clean, lint 0 errors.
