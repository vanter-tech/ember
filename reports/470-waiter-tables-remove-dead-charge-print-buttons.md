# Report 470

## 1. Identification
- **Report Number:** 470
- **Task ID:** `/waiter/tables` side panel: remove dead "Cobrar mesa"/"Imprimir cuenta" buttons (ad-hoc)
- **Predecessor Task:** report 469 (KDS focused-ticket layout)

## 2. Objective
User asked whether the "Cobrar mesa"/"Abrir mesa" and "Imprimir cuenta" buttons shown in the table-details side panel of `/waiter/tables` should be wired up or removed in favor of sending the waiter into the individual table view. Investigation confirmed both had no `onClick` (dead stubs) and fully duplicate functionality that already works correctly in `TableInformation.tsx` (reached via the existing, already-functional "Ver información" link) — so the agreed fix is removal, not wiring them up.

## 3. Modified Files
- Modify: `frontend/src/pages/waiter/Tables.tsx`
- Modify: `frontend/src/pages/waiter/components/WaiterTour.tsx`
- Modify: `frontend/src/locales/es/waiter.ts`
- Modify: `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
Removed the "Cobrar mesa"/"Abrir mesa" `Button` (id `waiter-tour-action`, no `onClick`) and the "Imprimir cuenta" `Button` (no `onClick`) from `Tables.tsx`'s side panel. The already-working "Ver información" `Link` (→ `TableInformation.tsx`, where billing/printing are real) stays, still gated on `tableDetails.isOccupied`, and "Asignar mesa" (unconditional, already wired to `createSession` via the `PARTICIPANTS_QR` modal) stays untouched — that's already the "open table" action for an unoccupied table, so nothing was lost.

The removed button carried the `waiter-tour-action` anchor id used by `WaiterTour`'s guided-tour step 3 (`tourActionTitle`/`tourActionContent`) — since that id is now only present in the DOM when a table happens to be occupied (the tour always starts from `dashboardData[0]`, which may or may not be occupied), leaving the step in would silently break the tour on a free first table. Removed that tour step entirely (grid → panel → assign now, 3 steps instead of 4) rather than repoint it to a conditionally-rendered element.

Removed the now-unused i18n keys `chargeTableButton`, `printBillButton`, `tourActionTitle`, `tourActionContent` from both `waiter.ts` locales (confirmed via grep each was used nowhere else — `openTableButton` stays, still used by `ParticipantsQrModal.tsx`; `printBillLabel`, a distinct key with the same Spanish text, stays — it's what `TableInformation.tsx`'s real print button uses).

## 5. Why It Changed?
Direct user request, reached after confirming in code that both buttons were non-functional duplicates of an already-correct flow rather than a missing feature — removing them is strictly safer than wiring a second, parallel billing/printing implementation in this panel.

## 6. Verification
- `pnpm run test:run` (full suite) — **135/135**, unchanged (no test previously covered these dead buttons).
- `pnpm run build` / `pnpm run build:hub` — both clean.
