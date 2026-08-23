# Report 189

## 1. Identification
- **Report:** 189
- **Task ID:** INV-02 (also covers INV-05's shared side-effect logic and the backend half of INV-03's low-stock broadcast)
- **Predecessor Task:** INV-01 (report 188)

## 2. Objective
Build the core inventory business logic on top of Task 1's data model: decrement stock automatically when the kitchen confirms a table's draft order items, auto-86/un-86 the linked `MenuItem` as stock crosses zero, and broadcast a low-stock WebSocket alert when stock is at or under threshold — all three behaviors sharing one method, per the spec's explicit design (§5).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/inventory/dto/LowStockAlertMessage.java`
- `backend/src/main/java/com/vanter/ember/inventory/service/InventoryService.java`
- `backend/src/main/java/com/vanter/ember/inventory/listener/InventoryConfirmedItemsListener.java`
- `backend/src/test/java/com/vanter/ember/inventory/service/InventoryServiceTest.java`
- `backend/src/test/java/com/vanter/ember/inventory/listener/InventoryConfirmedItemsListenerTest.java`

## 4. What Changed?
- `InventoryService.applyDelta(Long inventoryItemId, BigDecimal delta): InventoryItem` — the one public entry point for changing stock (later reused by INV-04's restock/create endpoints): calls Task 1's atomic `applyClampedDelta`, re-fetches the row, then runs the private `applyStockSideEffects`.
- `applyStockSideEffects` (private, one shared method): if `currentStock <= 0` and the `MenuItem` was available, disables it (auto-86); else if stock rose back above 0 and the item was unavailable, re-enables it unconditionally (auto-un-86, no distinction between system- and admin-driven disables); else if stock is at/under `lowStockThreshold`, broadcasts `LowStockAlertMessage` to `/topic/inventory/{tenantId}` (tenant resolved via `TenantContextHolder.requireTenantId()`, same convention `LoyaltyAccrualListener` uses).
- `InventoryConfirmedItemsListener` — new `@EventListener` on the **existing** `KitchenItemsConfirmed` event (no changes to that event or its firing site in `SessionService`): groups the confirmed `OrderItem`s by `itemId` (counting occurrences, since `OrderItem` has no `quantity` field), and for each `menuItemId` with a matching `InventoryItem` calls `applyDelta` with a negative delta. An item with no matching `InventoryItem` (untracked) is silently skipped, never throws.
- Tests cover all 3 `applyStockSideEffects` branches plus both no-op cases (already-in-target-state, above-threshold-no-op), the inclusive threshold boundary, and the listener's duplicate-`itemId` aggregation and silent-skip behavior — all against real domain objects and genuine state-mutation assertions, not mock echoes.

## 5. Why It Changed?
This is Task 2 of `docs/superpowers/plans/2026-08-22-emb-inv.md`, implementing spec §4/§5. Kitchen confirmation (not payment) is the decrement trigger because payment can happen hours after a dish leaves the kitchen — too late for low-stock alerts or auto-86 to matter during service. The three behaviors (auto-86, auto-un-86, broadcast) live in one method rather than being duplicated per call site, per the spec's explicit design decision, so INV-04's future restock/create endpoints reuse the exact same logic instead of re-deriving it.

## Verification
- `cd backend && ./mvnw test -Dtest=InventoryServiceTest,InventoryConfirmedItemsListenerTest` → 7/7 passed, pristine output.
- `cd backend && ./mvnw test` (full suite) → 782/782 passed (up from 775 after INV-01; +7 for this task's two new test classes).
