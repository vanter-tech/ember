# Report 687 — BILL-NUMBERING

## 1. Identification
- **Report number:** 687
- **Task ID:** BILL-NUMBERING (per-tenant consecutive bill and kitchen-ticket codes)
- **Predecessor task:** report 686 — LANDING-MOBILE-TABLE-AND-TRAILING-SLASHES (executed ahead of FINANCE-AUDIT-VISIBILITY, so it takes the next chronological number)

## 2. Objective
Receipts printed `Cuenta #<Bill.id>` — a DB identity shared by every tenant — and the KDS showed `Ticket: #` plus the first 6 characters of a UUID that was never printed anywhere. Give every bill and every kitchen order a gap-free per-tenant code (`ELPO-000123`, `ELPO-KDS-000045`), print exactly that code on paper, and show the same code on every screen so the physical document and the software always name the same thing.

## 3. Modified Files
Backend (new package `numbering`):
- `backend/src/main/java/com/vanter/ember/numbering/{DocumentSeries,DocumentCodes,IssuedNumber,DocumentCounter,DocumentCounterRepository,DocumentCounterInitializer,DocumentNumberService}.java` (new)
- `backend/src/main/resources/db/migration/V25__document_numbering.sql` (new)
- `backend/src/main/java/com/vanter/ember/billing/model/Bill.java`
- `backend/src/main/java/com/vanter/ember/billing/service/BillingService.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/main/java/com/vanter/ember/billing/dto/{PaymentResponse,WaiterBillStateResponse,BillReadyMessage}.java`
- `backend/src/main/java/com/vanter/ember/billing/listener/BillingEventListener.java`
- `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java`
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`
- `backend/src/main/java/com/vanter/ember/printing/service/{ReceiptLayout,ReceiptRenderer,KitchenTicketPrintService}.java`
- `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java`
- `backend/src/main/java/com/vanter/ember/export/service/ExportService.java`
- Tests: `numbering/{DocumentCodesTest,DocumentNumberServiceIntegrationTest}`, `BillingServiceTest`, `KitchenServiceTest`, `BillingEventListenerTest`, `BillingControllerTest`, `CashShiftServiceTest`, `ReceiptLayoutTest`, `ReceiptRendererTest`, `KitchenTicketPrintServiceTest`, `PrintingEventListenerTest`, `ExportServiceTest`

Frontend:
- `frontend/src/lib/documentCodes.ts` + `documentCodes.test.ts` (new)
- `frontend/src/lib/backend-types.ts` (`billCode`/`billNumber`, `ticketCode`/`ticketNumber`, `PaymentResponse.billCode`, `WaiterBillStateResponse.code`)
- `frontend/src/store/websocket.ts`
- `frontend/src/pages/kitchen/components/{QueueCard,FocusedCard,FocusedCard.test}.tsx`
- `frontend/src/pages/waiter/TableInformation.tsx`, `frontend/src/pages/waiter/components/RefundPaymentModal.tsx`
- `frontend/src/pages/accountant/cashRegister/CashRegister.tsx`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- `frontend/src/pages/admin/components/settings/TicketSettings.tsx`
- `frontend/src/pages/customer/Bill.tsx`
- `frontend/src/locales/{es,en}/{kitchen,waiter,admin}.ts`

## 4. What Changed?
- **Series and counters.** `document_counters (tenant_id, series, prefix, last_number)`. `DocumentNumberService.next` locks the tenant's row (`PESSIMISTIC_WRITE`) inside the caller's transaction and bumps it: a rolled-back bill gives its number back, and concurrent issuers queue instead of reading the same value. Two independent series per tenant: `BILL` and `KDS`. The row is created on first use in a `REQUIRES_NEW` transaction; a racing first insert fails only that transaction and the loser reuses the winner's row (the concurrency test caught this: catching the violation inside the `REQUIRES_NEW` method left it rollback-only).
- **Code format.** Prefix = first 4 letters/digits of the tenant slug, upper-case, separators dropped (`el-pollo-loco` → `ELPO`; shorter slugs keep what they have; fallback `EMBR`), frozen in the counter row at first use so renaming the slug never changes issued codes. `ELPO-000123` for bills, `ELPO-KDS-000045` for kitchen; zero-padded to 6 digits, growing past 999999 without truncation. The code string is stored on the document (`bills.bill_code`, `kitchen_orders.ticket_code`) so print, screens and export read the same frozen value.
- **Bills.** `BillingService.calculateBill` issues the number only after the "already billed / not open / nothing billable" checks pass, so a rejected request consumes nothing. A voided bill keeps its number; the recalculated bill gets a new one.
- **Kitchen.** A `KitchenOrder` is one per session (later sends append items to it), so the KDS code is issued when the order is created and every ticket of that table visit — automatic, per send, and reprint — carries the same code as the KDS card. `KitchenService.handleOrderItemAdded` is `@Order(HIGHEST_PRECEDENCE)` so the order and its code exist before `PrintingEventListener` prints the ticket for the same event.
- **Printing.** `ReceiptLayout` prints `Cuenta ELPO-000123` (legacy bills without a code still print `Cuenta #id`). Both kitchen ticket paths print the code as the first line, above `Mesa N`.
- **API/WS.** `PaymentResponse.billCode`, `WaiterBillStateResponse.code`, `BillReadyMessage.billCode`; the `Bill` and `KitchenOrder` entities expose the new fields.
- **Frontend.** `billCode()` / `kitchenTicketCode()` show the stored code and fall back to the old identifier for documents that predate numbering. Shown on the KDS queue card and focused card, waiter table bill card, refund modal, accountant payments table (new "Cuenta" column), admin shift-detail payments table (new column), customer bill page, and the Settings → Ticket preview (format-only samples `ABCD-000123` / `ABCD-KDS-000045`; the real prefix comes from the slug, which the frontend does not hold). `Ticket: #{{code}}` became `Ticket: {{code}}` because the code now carries its own marker.
- **Export.** Ventas "ID Cuenta" column renamed "Cuenta"; shows the bill code (raw id for legacy bills).
- **Migration `V25`.** Adds the four columns and the counters table; backfills existing bills per tenant in `created_at, id` order after any number already assigned, seeds the `BILL` counter with the tenant's prefix, fills `bill_code`, then adds the unique index `(tenant_id, bill_number)`. Idempotent. Run twice against a scratch Postgres 16 database (interleaved tenants, a 2-letter slug): numbering 1..n per tenant in creation order, prefixes `ELPO`/`AB`, second run a no-op. Kitchen orders are NOT backfilled (decision): old orders keep the short-id code.

## 5. Why It Changed?
An accountant verifies completeness by a consecutive series per restaurant; a shared `IDENTITY` id shows other tenants' gaps and a missing number could never be told from another tenant's bill. On the floor, staff match a paper ticket to a screen by its number; the KDS code was derived from a UUID that never appeared on paper. Race-safety is a CLAUDE.md priority, hence the row lock, the transaction-scoped increment and the concurrency test (8 threads × 5 numbers → exactly 1..40, no duplicates, no gaps).

## Verification
- Backend `./mvnw test` 1780/1780 (was 1744 + new numbering tests; first rerun exposed the shared-H2-context trap described in PROGRESS.md System Health).
- Frontend `pnpm run build` clean, `pnpm run lint` 0 errors (15 pre-existing warnings), `pnpm run test:run` 385/385 before the 2 KDS tests and the helper tests were added; those pass, and a final build + lint + 136 targeted tests (kitchen, waiter, accountant, admin cash register, documentCodes) are clean.
- Not done: real-printer check of the new first line, merged/pushed branch, `deploy.sh` (user-gated; prod runs `V25` on boot — never pre-run).
- Known limits: one KDS code can appear on several physical tickets of the same table visit (one `KitchenOrder` per session); legacy bills/orders keep the old identifier.
