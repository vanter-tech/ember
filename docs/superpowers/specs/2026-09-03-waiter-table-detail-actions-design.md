# Waiter Table-Detail Action Buttons (add item / print paid bill / transfer) — Design

- **Date:** 2026-09-03
- **Branch:** `feat/waiter-quick-login-table-actions` off `main`
- **Status:** design approved in chat 2026-09-03, pending spec review → `writing-plans`
- **Related:** `[[2026-09-03-waiter-quick-login-design]]` (sibling workstream, same branch)

## 1. Objective

Give behavior to the three header buttons in `frontend/src/pages/waiter/TableInformation.tsx`
(`:194-211`) that today render but do nothing:

1. **Agregar platillo** — waiter adds a menu item to the open table.
2. **Imprimir cuenta** — print the bill receipt, enabled **only once the table is paid & closed**.
3. **Transferir** — hand the table off to another waiter.

Reports 331–334 already translated and sized these placeholder buttons; this adds the wiring.

## 2. Current state (verified)

- `SessionService.addItem(sessionId, participantId, menuItemId, selectedOptionIds)` looks up a
  **participant**, resolves modifiers + price, creates an `OrderItem` at `OrderItemStatus.DRAFT`.
  `POST /sessions/{id}/items` is `@PreAuthorize("hasRole('CUSTOMER')")`.
- `confirmDraftsForUser(...)` flips a user's `DRAFT` items → `PENDING` and publishes the per-item
  kitchen events (`KitchenItemsConfirmed` etc.) that feed KDS and kitchen-ticket printing.
- Billing: `PaymentService.settleAndClose(billId)` requires every split `PAID`, then sets
  `Bill.status = PAID`, publishes `PaymentCompleted(billId, …)`, and its listener closes the
  session (`SessionStatus.CLOSED`). `TableInformation.tsx` currently `navigate('/waiter/tables')`
  on `sessionData.status === 'CLOSED'` (`:76-81`).
- Printing: `printing` module exists. `PrintingEventListener.onPaymentCompleted` builds a
  `BILL_RECEIPT` `PrintJob` **only** when `hardware.autoPrintTickets && hardware.printCustomerReceipt`
  are both set. `PrintDispatchService.dispatch(job)` sends it to a connected agent or leaves it
  `PENDING`. `PrintJobController` (`/printing/jobs`) has list (ADMIN) + retry (ADMIN/WAITER).
- `Session.waiterId` is a single `varchar` column (`Session.java:58`).
- Realtime: `/topic/waiter/{tenantId}` invalidates `['dashboardData']` on every waiter client;
  `/topic/session/{id}` invalidates `['sessionDetails', id]`. Both already wired in
  `store/websocket.ts`.
- `UserAdminController` `/admin/staff` is `hasRole('ADMIN')` — **not** usable by a waiter.

## 3. C1 — Add item (waiter)

### 3.1 Backend

New method `SessionService.addItemAsWaiter(sessionId, waiterEmail, menuItemId, selectedOptionIds, participantName)`:

- Load session; reject if not `OPEN`.
- Reuse `resolveSelectedModifiers` + the existing price calc.
- Attribution: if `participantName` is non-blank and matches a current participant, use it;
  otherwise attribute to the literal `"Mesa"` (a pseudo-diner, not added to the participant list).
- **Item is created at `OrderItemStatus.PENDING`** (not `DRAFT`) and the same per-item kitchen
  events `confirmDraftsForUser` publishes are emitted in this call, so KDS and kitchen-ticket
  printing behave exactly as for a customer-confirmed item. Rationale: the waiter *is* the
  confirmation step — a waiter-added item with no "send to kitchen" affordance would strand it.
- Append a session activity-log entry (same shape the view already renders under "Actividad").
- Broadcast on `/topic/session/{id}` (existing) so the customer cart and the waiter view refresh.

New endpoint on `SessionController`:

```
POST /sessions/{id}/waiter-items      @PreAuthorize("hasRole('WAITER')")
body: { menuItemId: number, selectedOptionIds: number[], participantName?: string }
-> SessionDetailResponseDto  (same shape as GET /sessions/{id})
```

Bill re-computation: if a non-voided `Bill` already exists for the session, `addItemAsWaiter`
rejects with `409 BILL_ALREADY_REQUESTED` — the waiter voids and re-requests the bill to pick up
new items. (Recomputing an existing bill's total + splits in place is deliberately out of scope;
revisit only if the user reports it as a real friction point.)

### 3.2 Frontend

`AddItemModal.tsx` (new, `pages/waiter/components/`), opened via `useUIStore` modal (same pattern
as `ChargeTableModal` / `VoidBillModal`):

- Menu list: reuse `menuItemService` bulk fetch (`getAllMenuItems`, `size: 500`), a search box
  filtering by name, grouped by category if cheap.
- On selecting an item with modifier groups, show a minimal modifier picker driven by
  `menuItem.modifierGroups` (radio for single-select groups, checkbox for multi). Reuse the
  customer selector component only if it lifts out without dragging in cart/session state.
- Quantity stepper: the client loops the POST N times (keeps the endpoint single-item and
  matches how the customer flow adds repeats).
- Participant selector: `sessionData.participants` + a default **"Mesa"** option.
- Submit → `SessionTableService.addWaiterItem(id, { menuItemId, selectedOptionIds, participantName })`
  → `queryClient.invalidateQueries({ queryKey: ['sessionDetails', id] })` and `['bill', id]`.
- Button (`:208-210`) gets `onClick={() => openModal('ADD_ITEM', { sessionId: id })}`; disabled
  when `sessionData.status !== 'OPEN'`.

## 4. C2 — Print bill (paid & closed)

### 4.1 Stay-on-page when the table closes

In `TableInformation.tsx`:

- **Remove** the `useEffect` at `:76-81` that navigates away on `status === 'CLOSED'`.
- Track `const wasOpenRef = useRef(false)`; set it `true` in an effect whenever
  `sessionData?.status === 'OPEN'`.
- Derived state:
  - `sessionData?.status === 'CLOSED' && wasOpenRef.current` → **closed stay-state**: render a
    prominent banner (`t('tablePaidClosedBanner')`), keep the bill summary card visible, keep
    **only** "Imprimir cuenta" enabled, disable/hide "Agregar platillo", "Transferir", "Cobrar
    mesa", per-item delete, and the split action buttons.
  - `sessionData?.status === 'CLOSED' && !wasOpenRef.current` → the waiter navigated back in via
    URL after leaving: `navigate('/waiter/tables', { replace: true })`. Re-entry is blocked, as
    specified.
- The existing `tableClosedPaidToast` / `tableClosedToast` toasts: keep one success toast on the
  transition, drop the navigation.

### 4.2 Backend — on-demand receipt

Extract the receipt-rendering logic from `PrintingEventListener.renderReceiptPayload` into a
reusable `ReceiptRenderer` (or a `PrintingService.enqueueBillReceipt(billId)`), used by **both**
the existing `onPaymentCompleted` listener and the new endpoint (no behavior change to the
listener).

```
POST /printing/bills/{billId}/receipt   @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
-> { jobId: UUID, status: PrintJobStatus }
```

- `404` if the bill does not exist for the tenant.
- `409 BILL_NOT_PAID` unless `Bill.status == PAID` (server-side gate — the button is only the
  first line of defense).
- Builds a `BILL_RECEIPT` `PrintJob` via the shared renderer and calls
  `printDispatchService.dispatch(job)`. Reprintable — calling twice makes two jobs.
- Independent of the `hardware.autoPrintTickets` / `printCustomerReceipt` settings gates (those
  gate the *automatic* receipt; an explicit waiter action is always allowed).

### 4.3 Frontend

- `billingService`/`printingService.printBillReceipt(billId)` in `api.ts`.
- `printBillMutation` in `TableInformation.tsx`: `onSuccess` → if `status === 'PENDING'` toast
  `t('printQueuedNoAgentToast')`, else `t('printSentToast')`; `onError` (409/other) →
  `t('printFailedToast')`.
- "Imprimir cuenta" button (`:195-200`): `onClick={() => printBillMutation.mutate(billData!.id)}`,
  `disabled={!billData || sessionData?.status !== 'CLOSED' || printBillMutation.isPending}`.

## 5. C3 — Transfer table

### 5.1 Backend

**Waiter list** — new lightweight waiter-accessible endpoint on a new
`identity/controller/WaiterDirectoryController`:

```
GET /identity/waiters            @PreAuthorize("hasAnyRole('WAITER','ADMIN')")
-> [ { id: string, name: string, email: string } ]
```

Query: `UserRepository` finder for `role = WAITER AND active = true` in the current tenant
(`@TenantId` scoping already applies). New `UserRepository.findByRoleAndActiveTrue(Role)` or a
projection query; returns a DTO, never the full `User` (no `passwordHash`/`pinHash` leakage).

**Transfer** — new endpoint on `SessionController`:

```
POST /sessions/{id}/transfer     @PreAuthorize("hasRole('WAITER')")
body: { targetWaiterId: string }
-> SessionDetailResponseDto
```

`SessionService.transferTable(sessionId, callerEmail, targetWaiterId)`:

- Session must be `OPEN` (`409` otherwise).
- Caller must currently own the session (`session.waiterId` resolves to the caller's user id) —
  `403 NOT_SESSION_OWNER` otherwise. (ADMIN calling is out of scope here; this endpoint is
  `WAITER`-only. An admin reassignment path can be added later if wanted.)
- `targetWaiterId` must be an active `WAITER` in the tenant and `!= current` (`400`).
- Set `session.waiterId = targetWaiterId`; save (respect `@Version` if present on `Session`).
- Append an activity-log entry (`TABLE_TRANSFERRED`, "Mesa transferida a {name}").
- Publish an event handled by the existing WS listeners so **both** waiters' floor lists refresh:
  broadcast on `/topic/waiter/{tenantId}` (invalidates `['dashboardData']`) and
  `/topic/session/{id}` (invalidates `['sessionDetails', id]`).

### 5.2 Frontend

`TransferTableModal.tsx` (new, `pages/waiter/components/`):

- On open, `useQuery(['waiters'], SessionTableService.listWaiters)`.
- List selectable waiters, excluding the current session owner; single select; confirm button.
- Submit → `SessionTableService.transferTable(id, targetWaiterId)` → on success toast
  `t('tableTransferredToast', { name })` and `navigate('/waiter/tables')` (the table is no longer
  the caller's).
- The receiving waiter's `Tables.tsx` list updates via the existing `/topic/waiter/{tenantId}`
  invalidation — no extra client wiring.
- "Transferir" button (`:201-206`): `onClick={() => openModal('TRANSFER_TABLE', { sessionId: id })}`,
  `disabled={sessionData?.status !== 'OPEN'}`.

## 6. Files touched (anticipated)

**Backend**
- `session/service/SessionService.java` — `addItemAsWaiter`, `transferTable`, activity-log entries.
- `session/controller/SessionController.java` — `POST /{id}/waiter-items`, `POST /{id}/transfer`.
- `session/dto/` — `AddWaiterItemRequest`, `TransferTableRequest`, `WaiterSummary`.
- `session/event/` — `TableTransferred` (+ listener wiring in the session/waiter WS listeners).
- `identity/…` — `GET /identity/waiters` controller + `UserRepository` finder + `WaiterSummary` DTO.
- `printing/listener/PrintingEventListener.java` + new `ReceiptRenderer`/`PrintingService` —
  extract shared render; `printing/controller/` — `POST /printing/bills/{billId}/receipt`.
- `billing` — read `Bill.status` for the paid gate (no schema change).
- Flyway: **none** expected (no new columns).

**Frontend**
- `pages/waiter/TableInformation.tsx` — wire 3 buttons, closed stay-state, remove auto-redirect.
- `pages/waiter/components/AddItemModal.tsx`, `TransferTableModal.tsx` — new.
- `store/uiStore.ts` — `ADD_ITEM`, `TRANSFER_TABLE` modal keys.
- `lib/api.ts` — `addWaiterItem`, `listWaiters`, `transferTable`, `printBillReceipt`.
- `store/websocket.ts` — handle `TABLE_TRANSFERRED` frame (invalidate dashboards + session).
- `locales/{es,en}/waiter.ts` — new keys (parity via `satisfies`).

## 7. Testing

**Backend**
- `SessionServiceTest`: `addItemAsWaiter` — OPEN-only guard; attribution to a real participant vs
  `"Mesa"`; item lands `PENDING`; kitchen events published; `409` when a live bill exists (option a).
- `SessionServiceTest`: `transferTable` — happy path swaps `waiterId` + activity log + event;
  `409` when not OPEN; `403` when caller isn't the owner; `400` for unknown/inactive/self target.
- `SessionControllerTest` (`@WebMvcTest`): role gating (`CUSTOMER`/`KITCHEN` → 403) and status codes
  for both new endpoints; `GET /identity/waiters` → 403 for non-WAITER/ADMIN, DTO shape (no hashes).
- Printing: `ReceiptRenderer` unit test (same output as the pre-extraction listener for a fixed
  `PaymentCompleted`); controller test — `409 BILL_NOT_PAID` when `Bill.status != PAID`, `200` +
  job id when PAID, second call makes a second job.
- Full `./mvnw test` green.

**Frontend**
- `TableInformation` RTL: closed stay-state shows the banner and only enables "Imprimir cuenta";
  fresh mount on a `CLOSED` session redirects; print button disabled until `CLOSED`.
- `AddItemModal` RTL: search filters; submit calls `addWaiterItem` with the right payload;
  invalidates `sessionDetails`.
- `TransferTableModal` RTL: excludes self; submit calls `transferTable` then navigates.
- `pnpm run build` + `pnpm run lint` clean; `pnpm run test:run` green.

## 8. Decisions locked (were the open questions during design)

1. **Bill desync on waiter add-item** — `409 BILL_ALREADY_REQUESTED` when a non-voided bill
   exists; no in-place recompute.
2. **Quantity** — client loops the single-item POST N times; no `quantity` field on the endpoint.
3. **Modifier picker** — minimal waiter-local list from `menuItem.modifierGroups`; reuse the
   customer component only if it detaches cleanly from cart/session state.
4. **`GET /identity/waiters`** — its own `identity/controller/WaiterDirectoryController`.
</content>
