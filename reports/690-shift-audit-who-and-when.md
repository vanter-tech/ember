# Report 690 — SHIFT-AUDIT-WHO-AND-WHEN

## 1. Identification
- **Report number:** 690
- **Task ID:** FRONTEND-FIELD-GAPS · B (cash shift: who and when)
- **Predecessor task:** report 689 — SHIFT-AUDIT-REFUNDS-AND-VOIDS (branch stacked on `feat/audit-voids-refunds` → r688 → r687, none merged)

## 2. Objective
`CashShiftResponse.closedAt` / `prolongCount` and `Payment.processedBy` / `gatewayRef` were returned or stored but shown nowhere: no close time, no count of extensions, and nothing said who registered each payment.

## 3. Modified Files
Backend: `billing/dto/PaymentResponse.java`, `billing/service/PaymentService.java`; tests `PaymentServiceTest`, `BillingControllerTest`, `CashShiftServiceTest`.
Frontend: `lib/backend-types.ts`, `pages/admin/cashRegister/components/{ShiftHistoryTable,DailyZReportPanel,CashRegisterSkeletons}.tsx` and their tests, `pages/accountant/cashRegister/{CashRegister.tsx,CashRegister.test.tsx,components/CashRegisterSkeleton.tsx}`, `locales/{es,en}/{admin,waiter}.ts`.

## 4. What Changed?
- `PaymentResponse` gains `processedByName` (the registering user's name; the stored id when it no longer resolves; null when none) and `gatewayRef`. `toResponses` resolves all names in one `findAllById`; `listRefunds` reuses the same private helper.
- Admin shift history and Corte Z: new "Cierre" (`closedAt`, `dd/MM/yyyy HH:mm`) and "Prórrogas" (`prolongCount`) columns.
- Payments tables (admin shift detail, accountant current shift): new "Registró" column; a digital payment shows its gateway reference under the method.
- Accountant open-shift meta line shows "Prórrogas: N" only when the shift was extended.
- Skeletons follow the new shapes: history 9 columns, Corte Z 10, accountant payments table 10 and a fourth meta block.
- `Payment.cashShiftId` is intentionally not shown: a payment is already listed inside the shift it belongs to. Digital `gatewayRef` is still the `STUB-…` value until a real gateway exists.

## 5. Why It Changed?
An auditor needs to know who handled each payment and when and how a shift was closed or stretched; the data was recorded and discarded at the API/UI boundary.

## Verification
Backend `./mvnw test` 1797/1797. Frontend build exit 0, lint 0 errors, vitest 399/400 (known `MenuJoin` failure, unrelated). Not opened in a browser.
