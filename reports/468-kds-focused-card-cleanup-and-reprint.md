# Report 468

## 1. Identification
- **Report Number:** 468
- **Task ID:** KDS focused-ticket cleanup: remove "Cliente" placeholder + "Anular", wire "Imprimir" as manual reprint (ad-hoc)
- **Predecessor Task:** report 467 (KDS-BULK-STATUS-UPDATE Task 4, plan complete)

## 2. Objective
User flagged, while looking at the focused kitchen ticket card, that it shows an irrelevant dev placeholder ("Cliente: #-Por-iterar") and asked about the "Imprimir"/"Anular" buttons. Investigation found both buttons had no `onClick` at all — pure unwired stubs — and that the backend already auto-prints the kitchen ticket on order confirmation (`PrintingEventListener.onKitchenItemsConfirmed`). Agreed fix: drop the placeholder and "Anular" (neither did anything and a one-click full-order void is risky to ever wire unconfirmed), and give "Imprimir" a real job — a manual reprint, for paper-jam recovery, independent of the automatic pipeline.

## 3. Modified Files
- Create: `backend/src/main/java/com/vanter/ember/printing/service/KitchenTicketPrintService.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/controller/KitchenTicketController.java`
- Create: `backend/src/test/java/com/vanter/ember/printing/service/KitchenTicketPrintServiceTest.java`
- Create: `backend/src/test/java/com/vanter/ember/printing/controller/KitchenTicketControllerTest.java`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/locales/es/kitchen.ts`
- Modify: `frontend/src/locales/en/kitchen.ts`
- Modify: `frontend/src/pages/kitchen/components/FocusedCard.tsx`
- Modify: `frontend/src/pages/kitchen/components/FocusedCard.test.tsx`

## 4. What Changed?
**Backend:** new `KitchenTicketPrintService.enqueue(orderId)`, mirroring the existing `BillReceiptPrintService.enqueue(billId)` pattern exactly — loads the `KitchenOrder` (tenant-scoped), renders a `KITCHEN_TICKET` payload from its current items/modifiers, builds a `PENDING` `PrintJob` (`role=KITCHEN`), `saveAndFlush` + `PrintDispatchService.dispatch` (same tenantId-at-flush-time reasoning already documented on the bill-receipt path). Deliberately does **not** check `hardware.autoPrintTickets` — that setting only gates the *automatic* ticket on order confirmation; a manual reprint button should always be able to fire. New `POST /printing/kitchen-orders/{orderId}/ticket` (`hasRole('KITCHEN')`, matching this view's other write actions — `updateItemStatus`/`updateItemsStatus` — not the broader `KITCHEN,ADMIN` used by reads), returning `{jobId, status}` like `BillReceiptController`.

**Frontend:** `printingService.printKitchenTicket(orderId)` added next to `printBillReceipt`. `FocusedCard.tsx`: removed the `<UserCheck/>` "Cliente: #-Por-iterar" span and the unwired "Anular" `Button` entirely; "Imprimir" now calls a `printTicketMutation` (mirrors the waiter's `printBillMutation` exactly — success toasts "Ticket enviado a la impresora" or, if no agent is connected, "Ticket en cola…"; failure toasts "No se pudo imprimir el ticket"). Removed the now-orphaned `clientPlaceholder`/`voidButton` i18n keys from `kitchen.ts` (both locales — confirmed via grep they were used nowhere else; `waiter.ts`'s own `voidButton` key is unrelated) and added `printSentToast`/`printQueuedNoAgentToast`/`printFailedToast`.

3 new tests in `FocusedCard.test.tsx` (mirroring `TableInformation.printbill.test.tsx`'s mocking of `printingService` + `react-hot-toast`): reprint calls `printKitchenTicket('ko-1')` and toasts success; a `PENDING` result toasts the no-agent message; the placeholder text and "Anular" are confirmed gone.

## 5. Why It Changed?
Direct result of the user's own read of the KDS UI plus my investigation confirming both buttons were dead code and that ticket auto-print already exists server-side — so the fix is cleanup (remove dead/risky UI) plus giving the one button worth keeping ("Imprimir") a genuine, low-risk job (manual reprint) instead of leaving it decorative.

## 6. Verification
- `cd backend && ./mvnw test -Dtest=KitchenTicketPrintServiceTest,KitchenTicketControllerTest` — **6/6**.
- `cd backend && ./mvnw test` (full suite) — **1267/1267**.
- `cd frontend && pnpm exec vitest run src/pages/kitchen/components/FocusedCard.test.tsx` — **7/7** (4 pre-existing + 3 new).
- `cd frontend && pnpm run test:run` (full suite) — **135/135** (+3).
- `cd frontend && pnpm run build` / `pnpm run build:hub` — both clean.
