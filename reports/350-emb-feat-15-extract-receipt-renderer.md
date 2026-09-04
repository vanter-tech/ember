# Report 350 — EMB-FEAT-15: extract `ReceiptRenderer` (no behavior change)

## 1. Identification
- **Report number:** 350
- **Current Task ID:** EMB-FEAT-15 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 4)
- **Predecessor Task:** EMB-FEAT-14 (report 349 — frontend add-item modal on the waiter table view)

## 2. Objective
Pull the plain-text `BILL_RECEIPT` payload builder out of `PrintingEventListener`'s
private `renderReceiptPayload` into a standalone `@Component`, so the on-demand reprint
endpoint (EMB-FEAT-16) can render byte-for-byte identically to the automatic
`PaymentCompleted` path. Pure refactor — zero behavior change — locked by a
characterization test.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/printing/service/ReceiptRenderer.java` (new)
- `backend/src/main/java/com/vanter/ember/printing/listener/PrintingEventListener.java`
- `backend/src/test/java/com/vanter/ember/printing/service/ReceiptRendererTest.java` (new)
- `backend/src/test/java/com/vanter/ember/printing/listener/PrintingEventListenerTest.java`

## 4. What Changed?
- **`ReceiptRenderer.java`** — new `@Component` with `render(Long billId, SettingsPayload
  settings): String`. Body copied verbatim from `PrintingEventListener.renderReceiptPayload`,
  the only change being `event.billId()` → the `billId` parameter: optional header line
  (when `ticket.headerMessage` non-blank), `"Bill #" + billId`, optional footer line
  (when `ticket.footerMessage` non-blank), each `'\n'`-terminated.
- **`PrintingEventListener.java`** — added `import ...printing.service.ReceiptRenderer;`
  and a `private final ReceiptRenderer receiptRenderer;` field (class is
  `@RequiredArgsConstructor`). `onPaymentCompleted` now calls
  `receiptRenderer.render(event.billId(), settings)` in place of the private helper.
  The private `renderReceiptPayload(PaymentCompleted, SettingsPayload)` method is
  deleted. `renderKitchenPayload` and `createAndDispatch` are untouched.
- **`ReceiptRendererTest.java`** — 2 plain-JUnit characterization tests (no Spring, no
  Mockito): `render_includesHeaderBillLineAndFooter` asserts
  `"Gracias por su visita\nBill #42\nVuelva pronto\n"`;
  `render_omitsBlankHeaderAndFooter` asserts `"Bill #7\n"` from a bare
  `new SettingsPayload()` (its `ticket` field is initialised non-null with null
  header/footer).
- **`PrintingEventListenerTest.java`** — added `import ...printing.service.ReceiptRenderer;`
  and `@Mock ReceiptRenderer receiptRenderer;` so `@InjectMocks` supplies the new
  constructor arg. The two existing kitchen-path tests exercise `onKitchenItemsConfirmed`
  only, which never touches the renderer, so they are otherwise unchanged and still pass.

## 5. Why It Changed?
EMB-FEAT-16 adds `POST /printing/bills/{billId}/receipt` for on-demand reprints of a
paid bill. That endpoint and the existing `PaymentCompleted` listener must produce the
exact same receipt text; the safest way to guarantee that is a single shared renderer
rather than two copies of the format logic. Extracting it now, as an isolated
no-behavior-change commit with a characterization test pinning the output format, keeps
the subsequent feature commit small and makes any future format drift a test failure.

## 6. Verification
- `./mvnw test -Dtest=ReceiptRendererTest,PrintingEventListenerTest` → PASS
  (`ReceiptRendererTest` 2/2, `PrintingEventListenerTest` 2/2).
- `./mvnw test` → **971/971** BUILD SUCCESS, 0 failures / 0 errors (969 + 2 new).
