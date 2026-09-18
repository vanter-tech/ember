# Report 492

## 1. Identification
- **Report number:** 492
- **Task ID:** LIVE-BUG-BATCH 5/7 — cash-shift payments should show the table, not the person
- **Predecessor task:** report 491 (bug 4 — Settings tab cards grid layout)

## 2. Objective
Live user bug report: expanding a closed shift in admin Corte Z shows its payments as a plain list
of "participant name — amount"; should instead show which table each payment covered, as a real
table (columns). User-confirmed scope: Mesa + Monto + Método + Estado.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/billing/dto/PaymentResponse.java`
- `backend/src/main/java/com/vanter/ember/billing/service/PaymentService.java`
- `backend/src/test/java/com/vanter/ember/billing/service/PaymentServiceTest.java`
- `backend/src/test/java/com/vanter/ember/billing/controller/BillingControllerTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`
- `frontend/src/lib/backend-types.ts`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.tsx`
- `frontend/src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx` (new)
- `frontend/src/locales/es/admin.ts`
- `frontend/src/locales/en/admin.ts`

## 4. What Changed?
`PaymentResponse` gains a `tableNumber` field. `PaymentService.toResponses` now resolves it via a
new private `resolveTableNumbers` helper: collects each payment's bill's `sessionId` (distinct),
looks up each session's `tableId` via the already-injected `SessionService`, then does a single
batch lookup (`DiningTableRepository.findByRestaurantIdAndIdIn`) to map table ids to table numbers
— one query per distinct session plus one batch query, not one query per payment. New
`DiningTableRepository` dependency added to `PaymentService`'s constructor (Lombok
`@RequiredArgsConstructor`).

`ShiftHistoryTable.tsx`'s expanded-payments block changed from a `<div>` list showing
`participantName — amount` to a real `<Table>` with 4 columns: Mesa (`#<tableNumber>` or `—`),
Monto (amount + refunded suffix, unchanged from before), Método (Digital/Efectivo), Estado
(Reembolsado / Confirmado / Pendiente — refund still takes priority over the raw status, matching
the old behavior's refund-first check). 7 new i18n keys added (`paymentTableColumnLabel`,
`paymentAmountColumnLabel`, `paymentMethodColumnLabel`, `paymentMethodDigitalLabel`,
`paymentMethodPhysicalLabel`, `paymentStatusConfirmedLabel`, `paymentStatusPendingLabel`) — the
existing `statusColumnLabel`/`refundedLabel`/`refundedAmountSuffix` were reused as-is.

Fixed 3 existing backend tests' positional `PaymentResponse` constructor calls (now 10 args, was 9)
and added `PaymentServiceTest.toResponses_resolvesTheTableNumberFromEachPaymentsSession` covering
the new resolution path end to end (session lookup + batch table lookup, both mocked).
`ShiftHistoryTable.test.tsx` didn't exist on this branch before — created fresh, asserting the new
table renders `#5` and never renders the participant's name.

## 5. Why It Changed?
A single cash shift can span many different tables; knowing *who* paid is far less useful to an
admin reviewing a shift than knowing *which table* the payment was for — the participant's name
alone doesn't identify a table, especially for guest sessions with generated names.

## Verification
- `cd backend && ./mvnw -Dtest=PaymentServiceTest,BillingControllerTest,CashShiftServiceTest test` → 102/102 pass.
- `cd backend && ./mvnw test` → **1302/1302**, BUILD SUCCESS.
- `cd frontend && pnpm vitest run src/pages/admin/cashRegister/components/ShiftHistoryTable.test.tsx` → 1/1 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **149/149**.
