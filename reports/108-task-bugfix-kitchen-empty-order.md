# Report 108 — bugfix-kitchen-empty-order

## 1. Identification
- **Report Number:** 108
- **Task ID:** bugfix-kitchen-empty-order
- **Predecessor Task:** bugfix-kitchen-orphan-item (report 107)

## 2. Objective
Stop the KDS from showing a blank order card (table number, no items) after a waiter deletes the last remaining item of a kitchen order.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/kitchen/service/KitchenService.java`

## 4. What Changed?
`handleItemDeleted` now checks whether `order.getItems()` is empty after removing the deleted item. If so, it retires the order (`active=false`) and publishes the existing `KitchenOrderRetired` event instead of `KitchenItemRemoved`, exactly like `handleSessionClosed` already does. `handleOrderItemAdded` now force-sets `order.setActive(true)` on every confirm (new or reused document), so a later order confirmed for the same session after an earlier one was fully deleted/retired reactivates onto the live display instead of silently attaching to a retired, hidden document.

## 5. Why It Changed?
`findDisplay()` only filters `KitchenOrder` by `active=true`; report 107's fix removed the deleted item from `order.items` but left `active` untouched, so a now-empty order still passed that filter and rendered as an empty card. Retiring on empty-items reuses the same "hide from live display" mechanism already established for session close, rather than introducing a second way to hide an order. The `handleOrderItemAdded` guard closes the resulting edge case: `findByTenantIdAndSessionId` matches regardless of `active`, so without forcing it back to `true`, a customer's second order in the same session would attach to the now-retired document and never appear on the KDS.

## 6. Verification
`cd backend && ./mvnw test` — exit code 0, all tests passed.
