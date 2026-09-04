# Report 351 — EMB-FEAT-16: on-demand `POST /printing/bills/{billId}/receipt`

## 1. Identification
- **Report number:** 351
- **Current Task ID:** EMB-FEAT-16 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 5)
- **Predecessor Task:** EMB-FEAT-15 (report 350 — extract `ReceiptRenderer` from the payment listener)

## 2. Objective
Add a WAITER/ADMIN endpoint to print (or reprint) the receipt for a paid bill on
demand. Builds a `BILL_RECEIPT` `PrintJob` at `PENDING` and dispatches it, reusing the
`ReceiptRenderer` extracted in EMB-FEAT-15 so the on-demand copy is byte-for-byte
identical to the automatic `PaymentCompleted` path. Rejects with `409 BILL_NOT_PAID`
unless `Bill.status == PAID`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/exception/BillNotPaidException.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/service/BillReceiptPrintService.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/controller/BillReceiptController.java` (new)
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- `backend/src/test/java/com/vanter/ember/printing/service/BillReceiptPrintServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/printing/controller/BillReceiptControllerTest.java` (new)

## 4. What Changed?
- **`BillNotPaidException.java`** — new `RuntimeException`, message `"Bill <id> is not
  fully paid"`.
- **`BillReceiptPrintService.java`** — new `@Service` (`@RequiredArgsConstructor`,
  deps `BillRepository` / `SettingService` / `ReceiptRenderer` / `PrintJobRepository` /
  `PrintDispatchService`). `enqueue(Long billId): PrintJob`:
  `billRepository.findById` → `ResourceNotFoundException` (404) when absent; throws
  `BillNotPaidException` unless `status == BillStatus.PAID`; resolves the tenant via
  `TenantContextHolder.requireTenantId()`, renders through
  `receiptRenderer.render(billId, settingService.getSettings(tenantId).getPayload())`;
  builds a `PrintJob` (`id` random UUID, `role = RECEIPT`, `sourceType = BILL_RECEIPT`,
  `sourceId = String.valueOf(billId)`, `status = PENDING`, `attempts = 0`, `createdAt` /
  `updatedAt` now); `printJobRepository.saveAndFlush(job)` then
  `printDispatchService.dispatch(job)` — same `saveAndFlush` reasoning as
  `PrintingEventListener` (`@TenantId` generated at flush, `dispatch` reads it
  immediately); returns the job.
- **`BillReceiptController.java`** — new `@RestController` at `/printing/bills`.
  `POST /{billId}/receipt` `@PreAuthorize("hasAnyRole('WAITER','ADMIN')")` →
  `PrintReceiptResponse(UUID jobId, PrintJobStatus status)` (nested record).
- **`GlobalExceptionHandler.java`** — new `@ExceptionHandler(BillNotPaidException.class)`
  → `409 CONFLICT` `ProblemDetail` with `code = "BILL_NOT_PAID"` (mirrors the existing
  `CashShiftOverdueException` / `PinNotSetException` coded-conflict handlers).
- **`BillReceiptPrintServiceTest.java`** — 3 Mockito unit tests:
  `enqueue_buildsPendingReceiptJob_whenBillPaid` (asserts role / sourceType / sourceId /
  status / payload and both `saveAndFlush` + `dispatch`),
  `enqueue_throwsBillNotPaid_whenBillOpen`, `enqueue_throwsNotFound_whenBillMissing`
  (both assert no dispatch). Tenant set/cleared per test.
- **`BillReceiptControllerTest.java`** — 3 `@WebMvcTest(BillReceiptController.class)`
  tests matching `PrintJobControllerTest`'s conventions (`@Import({SecurityConfig,
  CorsConfig})`, `@MockBean` `JwtService` / `UserDetailsService` / `UserRepository` /
  `RestaurantRepository`, `.with(csrf())`): WAITER → 200 with `$.jobId` / `$.status`;
  WAITER + service throws `BillNotPaidException` → 409 with `$.code == BILL_NOT_PAID`;
  CUSTOMER → 403.

## 5. Why It Changed?
The waiter table-detail view's "Imprimir cuenta" button needs a backend to hit once a
table is paid and closed. Reprinting must not re-run payment, so it is a dedicated
endpoint rather than a side effect of `PaymentCompleted`. The PAID gate prevents a
waiter printing a "receipt" for a bill still being settled. Reusing `ReceiptRenderer`
(EMB-FEAT-15) guarantees the manual copy matches the auto-printed one. The coded 409
lets the frontend show a specific "bill not paid yet" message instead of a generic
conflict toast.

## 6. Plan Deviations
- Plan Task 5 Step 2's service-test snippet mocks `com.vanter.ember.settings.model.Settings`;
  the real `SettingService.getSettings(UUID)` returns `RestaurantSettings`, so the test
  mocks `RestaurantSettings` instead. Assertion contract unchanged.
- Plan named the commit `feat(printing): on-demand bill receipt endpoint gated on paid
  status` — used verbatim.

## 7. Verification
- `./mvnw test -Dtest=BillReceiptPrintServiceTest,BillReceiptControllerTest` → PASS
  (3/3 + 3/3).
- `./mvnw test` → **977/977** BUILD SUCCESS, 0 failures / 0 errors (971 + 6 new).
