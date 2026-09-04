# Report 347 — EMB-FEAT-12: `SessionService.addItemAsWaiter`

## 1. Identification
- **Report number:** 347
- **Task ID:** EMB-FEAT-12 (Plan C — `docs/superpowers/plans/2026-09-03-waiter-table-detail-actions.md`, Task 1)
- **Predecessor Task:** EMB-FEAT-11 (report 346 — Plan B waiter quick-login wrap-up / verification)

## 2. Objective
Give a waiter a service method that puts a menu item on a table straight at the kitchen: create
the `OrderItem` at `PENDING` (skipping the customer `DRAFT` → confirm step), attribute it to a
named participant or to `"Mesa"`, and fire the same events a customer confirm does so the KDS
picks it up. Reject the call when the session is not `OPEN` or a non-voided bill already exists.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/dto/AddWaiterItemRequest.java` (created)
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` (modified)
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java` (modified)

## 4. What Changed?
- **`AddWaiterItemRequest`** — record `{ @NotNull Long menuItemId, List<Long> selectedOptionIds,
  String participantName }`; compact constructor defaults a null `selectedOptionIds` to
  `List.of()`.
- **`SessionService`** — new `BillRepository billRepository` `@RequiredArgsConstructor` field
  (imports `billing.repository.BillRepository`, `billing.model.BillStatus`) and a new
  `addItemAsWaiter(String sessionId, Long menuItemId, List<Long> selectedOptionIds,
  String participantName): Session`:
  - Loads the tenant-scoped session via `findById`; throws `IllegalStateException`
    (`"Cannot add items to a session that is not open"`) unless `status == OPEN` — stricter than
    `addItem`'s `CLOSED`-only guard, so `BILLING` is rejected too.
  - Throws `IllegalStateException` (`"Bill already requested for this session"`) when
    `billRepository.findBySessionIdAndStatusNot(sessionId, BillStatus.VOIDED)` is present. Both
    messages map to HTTP 409 via the existing `IllegalStateException` handler.
  - Resolves the menu item through `menuItemService.findById` (availability check reused from
    `addItem`), loads the `DiningTables` row for the table number.
  - Attribution: if `participantName` matches a current `Participant` by name, uses that
    participant's name + `userId`; otherwise name `"Mesa"` with `participantId = null`.
  - Builds the `OrderItem` at `OrderItemStatus.PENDING` with modifiers resolved by the existing
    private `resolveSelectedModifiers`, price = base + summed `priceDelta`; appends a
    `SessionActivity(type = ITEM_SENT, itemName, participantName, timestamp = now)`.
  - After `sessionRepository.save`, publishes `ItemAdded(id, name, price, participantName, status,
    items)` and `KitchenItemsConfirmed(tenantId, id, tableNumber, List.of(newItem))` — arg order
    copied verbatim from the `addItem` / `confirmDraftsForUser` call sites in this same class.
- **`SessionServiceTest`** — `@Mock BillRepository billRepository` (auto-wired via the existing
  `@InjectMocks SessionService`), imports for `Bill` / `eq` / `isA`, and 4 tests:
  `PENDING` + `"Mesa"` + null `participantId` + `ITEM_SENT` activity + both events published;
  attribution to a named participant (`"Alice"` → `userId "user-1"`); reject when session
  `CLOSED`; reject when a non-voided `Bill` exists (message assertion).
  - **Plan deviation:** the plan's Task-1 test snippet stubs `sessionRepository.findById(...)` and
    uses helpers `openSessionWithNoParticipants` / `availableMenuItem(id,name,price)` /
    `diningTable(n)` that don't exist here. This repo's `SessionService.findById` goes through
    `sessionRepository.findByIdAndTenantId(id, tenant)`, so the tests stub that and reuse the
    file's real helpers (`openSessionWithParticipant`, `availableMenuItem()`, `diningTable()`),
    consistent with prior EMB-FEAT plan-vs-repo test adaptations.

## 5. Why It Changed?
The three header buttons on the waiter table-detail view (`TableInformation.tsx`) have been dead
since the view was built. "Add item" is the first: a waiter taking an order at the table needs a
one-step path that lands the item in the kitchen queue, not the customer's two-step DRAFT/confirm
flow. Routing it through a dedicated service method (rather than reusing `addItem` +
`confirmDraftsForUser`) keeps the waiter path free of the participant-membership and
requester-identity checks that only make sense for a diner confirming their own cart, and lets the
bill-exists guard live in one place. The `PENDING` status + `ItemAdded` + `KitchenItemsConfirmed`
pairing is exactly what `confirmDraftsForUser` emits, so KDS and the realtime session views need
no changes. The HTTP endpoint that exposes this is EMB-FEAT-13 (Plan C Task 2).

## Verification
- `./mvnw test -Dtest=SessionServiceTest` → `Tests run: 60, Failures: 0, Errors: 0` (56 baseline + 4 new).
- `./mvnw test` → **BUILD SUCCESS**, `Tests run: 966, Failures: 0, Errors: 0, Skipped: 0`
  (962 baseline + 4 new).
