# Report 689 — SHIFT-AUDIT-REFUNDS-AND-VOIDS

## 1. Identification
- **Report number:** 689
- **Task ID:** FRONTEND-FIELD-GAPS · A (voids and refunds)
- **Predecessor task:** report 688 — ADMIN-ZREPORT-DENOMINATIONS (branch stacked on `feat/zreport-denominations` → `feat/bill-numbering`, none merged)

## 2. Objective
`Bill.voidedBy/voidedAt/voidReason` and `Refund.reason/refundedBy` were recorded but shown nowhere, and no endpoint listed voided bills. Show them in the admin shift history and in the Corte Z, with their loading placeholders.

## 3. Modified Files
Backend: `billing/dto/{ShiftRefundResponse,VoidedBillResponse}.java` (new), `billing/service/BillingAuditService.java` (new), `billing/service/PaymentService.java` (`tablesBySession` extracted, `TableRef` package-visible), `billing/repository/{RefundRepository,BillRepository}.java`, `cashregister/dto/CashShiftDetailResponse.java`, `cashregister/service/CashShiftService.java`; tests `BillingAuditServiceTest`, `BillingAuditQueriesTest` (new), `CashShiftServiceTest`.
Frontend: `lib/{backend-types,api,format}.ts`, `pages/admin/cashRegister/components/{ShiftAuditDetail,ShiftAuditDetail.test,CashRegisterSkeletons,ShiftHistoryTable,DailyZReportPanel,DailyZReportPanel.test}.tsx`, `locales/{es,en}/admin.ts`.

## 4. What Changed?
- `GET /cash-shifts/{id}` (`CashShiftDetailResponse`) now also returns `refunds` and `voidedBills`.
  - Refunds: those of the payments taken in the shift (a late refund of an earlier shift's payment belongs to that shift, as the payment's `cashShiftId` says), with bill code, participant, amount, reason, who, when.
  - Voided bills: bills with `voidedAt` between the shift's `openedAt` and `closedAt` (now, for an open shift): code, total, table (merged label when joined), reason, who, when. Voids have no payment, so the time window is the only link to a shift.
- `BillingAuditService` (read-only) builds both lists; user ids resolve to names in one `findAllById`, an unknown id falls back to the stored id. Two repository queries: `RefundRepository.findByPaymentIdIn`, `BillRepository.findVoidedBetween` (both oldest-first, tenant-scoped; tenant-isolation tests).
- Frontend: `ShiftAuditDetail` renders a "Reembolsos" and a "Cuentas anuladas" table (or "no … in this shift"), sharing the `cashShiftDetail` query with the history table. Used in the shift-history expanded row and in the Corte Z expanded row (which now fetches the detail on expansion).
- Skeletons: `ShiftAuditSkeleton` (two titled tables as grey blocks, no real text, sr-only status) while the detail loads in the Corte Z; the history row's "Cargando pagos…" text became a `TableSkeleton`.

## 5. Why It Changed?
Refund reasons and voids are the evidence an audit asks for first; they were being recorded and then hidden. Placing voids by time window avoids a schema change.

## Verification
Backend `./mvnw test` 1793/1793. Frontend build exit 0, lint 0 errors, vitest 394/395 — the failure is the known `MenuJoin` "authenticated" test, which also fails alone and is unrelated. Not opened in a browser. Accountant has no past-shift view, so FINANCE-AUDIT item 2 (accountant past-shift detail) is still open.
