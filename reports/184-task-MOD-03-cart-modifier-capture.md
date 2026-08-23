# Report 184 — MOD-03: Backend cart/order captures modifiers + price adjustment

## 1. Identification
- **Report:** 184
- **Task ID:** MOD-03 (EMB-MOD backlog)
- **Predecessor Task:** MOD-02 (report 183)

## 2. Objective
Let the customer's add-to-cart flow accept a menu item's selected modifier option IDs, validate them against that item's active modifier groups, price the resulting `OrderItem` (base price + option deltas), and snapshot the chosen modifiers (name + price) onto the cart/order document so later catalog edits never retroactively change a placed order.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/model/SelectedModifier.java` (new)
- `backend/src/main/java/com/vanter/ember/session/exception/InvalidModifierSelectionException.java` (new)
- `backend/src/main/java/com/vanter/ember/session/model/OrderItem.java`
- `backend/src/main/java/com/vanter/ember/session/dto/AddItemRequest.java`
- `backend/src/main/java/com/vanter/ember/session/dto/OrderItemDto.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java` (not in original plan's file list — found by grepping live `addItem(`/`AddItemRequest(` call sites)
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java` (not in original plan's file list — same reason)
- `frontend/src/lib/backend-types.ts` (regenerated)

## 4. What Changed?
- `SelectedModifier{groupName, optionName, priceDelta}` — the Mongo-embedded snapshot of one chosen option.
- `InvalidModifierSelectionException` — new `RuntimeException`, mapped to HTTP 400 in `GlobalExceptionHandler` (cart-selection errors are the one case in this backlog that isn't a 409, per spec §5; CRUD-side rules stay `IllegalArgumentException`/`IllegalStateException` → 409).
- `OrderItem` gained `modifiers: List<SelectedModifier>` (`@Builder.Default`, empty list).
- `AddItemRequest` gained `selectedOptionIds: List<Long>` (defaults to `List.of()` in a compact constructor when the client omits it).
- `OrderItemDto` gained `modifiers` as its 7th component (before `addedAt`).
- `SessionService.addItem` signature changed to `(sessionId, participantId, menuItemId, selectedOptionIds)`. It now calls a new private `resolveSelectedModifiers(menuItem, selectedOptionIds)` that: (1) rejects any option ID not present in the item's active modifier groups, (2) enforces each group's `minSelections`/`maxSelections` against the selected count, (3) builds the `SelectedModifier` snapshot list. The item's stored price becomes `menuItem.getPrice()` plus the sum of selected options' `priceDelta`. `getSessionDetails`'s `OrderItemDto` construction was updated to pass `i.getModifiers()` through.
- `SessionController.addItem` and all 5 pre-existing `SessionServiceTest` call sites, plus 3 call sites in `SessionControllerTest` and 1 in `E2EOrderFlowTest`, were updated to the new 4-arg/2-arg signatures.
- 3 new tests added to `SessionServiceTest`: required-group-not-selected rejection, option-not-assigned-to-item rejection, and correct price computation with a modifier delta.
- `frontend/src/lib/backend-types.ts` regenerated; `OrderItemDto`/`AddItemRequest` schemas now carry `modifiers`/`selectedOptionIds`.

## 5. Why It Changed?
Implements MOD-03 of the approved EMB-MOD plan (`docs/superpowers/plans/2026-08-22-emb-mod.md`): the admin-side modifier catalog from MOD-01/MOD-02 needs a consumer on the customer ordering path before it's usable end-to-end, and the snapshot-not-live-reference design (spec-mandated) is what lets KDS/printed tickets (MOD-06, later) and past orders stay correct after a future catalog edit. The two test files outside the plan's stated file list were found by grepping every `addItem(`/`AddItemRequest(` call site in `backend/src` rather than trusting the plan's list as exhaustive — both would have failed compilation otherwise.

## 6. Verification
- `cd backend && ./mvnw test` — 770/770 pass (was 767; +3 new modifier tests), 0 failures/errors.
- `cd frontend && pnpm run openapi` (backend run in background, then stopped) — regenerated `backend-types.ts` confirmed to contain `OrderItemDto.modifiers`/`SelectedModifier`.
- `cd frontend && pnpm run build` — `tsc -b && vite build` green.
