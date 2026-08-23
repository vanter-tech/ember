# EMB-INV — Inventario básico — Design Spec

**Date:** 2026-08-22
**Backlog prefix:** `EMB-INV`
**Status:** Approved, pending implementation plan
**Related:** SaaS feature-gap initiative (`PROGRESS.md`), sibling of EMB-GATEWAY/EMB-MOD in the same backlog wave

## 1. Purpose

Today a `MenuItem` has no concept of stock — `available` is a manual boolean
an admin flips by hand, with no link to how many units of a dish can
actually be made. This spec adds an opt-in stock counter per `MenuItem`,
automatic decrement as kitchen orders are confirmed, a low-stock alert
pushed to the admin in real time, and automatic 86'ing (and un-86'ing) of
the `MenuItem` when stock crosses zero.

This is gap `EMB-INV` from the SaaS feature-gap audit (2026-08-22).

## 2. Scope decisions (confirmed with user)

1. **Granularity is per-`MenuItem`, not per-ingredient.** One
   `InventoryItem` tracks stock for one dish. No shared-ingredient model, no
   bill-of-materials/recipe entity, no consumption from `ModifierOption`
   selections — a future backlog if ever needed, not this one.
2. **Opt-in, not mandatory.** `InventoryItem` is an optional 1:1 relation to
   `MenuItem`. A dish with no `InventoryItem` is never tracked, never
   decremented, never auto-86'd — the existing manual `available` toggle is
   the only control for it, unchanged. Admins only create tracking for
   dishes with a real limiting stock.
3. **Stock decrements when the kitchen confirms the order**
   (`KitchenItemsConfirmed`), not when the bill is paid. Payment can happen
   hours after the dish left the kitchen (open tabs, split bills at the end
   of a long sitting) — decrementing that late would make low-stock alerts
   and auto-86 useless during service. `KitchenItemsConfirmed` already
   carries `tenantId` and the resolved `List<OrderItem>`, so no extra
   lookups are needed (unlike `PaymentCompleted`, which only carries
   `sessionId`/`tableId`/`billId`).
4. **Each confirmed `OrderItem` row = 1 unit.** `OrderItem` has no
   `quantity` field today (a second unit of the same dish is a second
   `OrderItem` row) — decrement logic counts occurrences per `itemId` within
   a confirmation batch rather than reading a quantity field.
5. **Low-stock alert is a real-time WebSocket push**, not a page-load-only
   badge — broadcast to a new `/topic/inventory/{tenantId}` topic the
   moment stock crosses the threshold, consumed only while the admin
   Inventory page is mounted.
6. **Auto-86 is reversible automatically.** When any stock-changing
   operation (in practice, an admin's manual restock — the kitchen listener
   only ever decrements) brings `currentStock` back above `0` on an
   `InventoryItem` whose `MenuItem.available` is currently `false`, the item
   is flipped back to `available = true` unconditionally.
   The system does not try to distinguish "I disabled this via auto-86" from
   "an admin disabled this manually for an unrelated reason" — accepted
   risk, explicitly chosen over adding a tracking field for it.
7. **Stock and threshold are `BigDecimal`**, not integers — units like "kg"
   or "L" are fractional in practice, not just "unidades".

## 3. Backend data model

### 3.1 New entity (`inventory` module, Postgres/JPA, `V14__inventory.sql`)

**`inventory_items`**

| column | type | notes |
|---|---|---|
| `id` | `bigint PK identity` | |
| `tenant_id` | `uuid` | `@TenantId`, auto-filtered like `Category`/`MenuItem` |
| `menu_item_id` | `bigint`, unique | plain reference column, **not** a JPA `@ManyToOne` — matches the project's existing convention for cross-entity references that don't need lazy-loaded navigation (`MenuItemModifierGroup.menuItemId`, `PrintJob.sourceId`) |
| `unit` | `varchar(20)` | free text, e.g. `"kg"`, `"unidades"`, `"L"` |
| `current_stock` | `numeric(10,3)` | `>= 0`, enforced at the DB layer via the clamped update (§4), never negative |
| `low_stock_threshold` | `numeric(10,3)` | `>= 0` |
| `updated_at` | `timestamp` | bumped on every stock-changing operation |

Unique constraint on `menu_item_id` — a dish can have at most one
`InventoryItem`. No FK constraint to `menu_items(id)` (same reasoning as
the plain-reference precedent above); the service layer validates the
`MenuItem` exists and belongs to the caller's tenant on create.

## 4. Stock decrement (INV-02)

New `inventory.listener.InventoryConfirmedItemsListener`, `@EventListener`
on the existing `KitchenItemsConfirmed` (`session.event`) — no changes to
that event or to `KitchenService`.

1. Group `event.confirmedItems()` by `itemId`, counting occurrences per
   group (§2.4) — this is the quantity to decrement for that `menuItemId`.
2. For each `(menuItemId, qty)` pair, look up an `InventoryItem` by
   `(tenantId, menuItemId)`. No match → skip silently (opt-in, same
   "skip if unmatched" pattern as `LoyaltyAccrualListener`).
3. Apply an **atomic clamped decrement** via a `@Modifying` repository
   query:
   ```sql
   UPDATE inventory_items
   SET current_stock = GREATEST(current_stock - :qty, 0), updated_at = :now
   WHERE id = :id
   ```
   This is a single statement, safe under concurrent confirmations from
   different tables without needing `@Version`/optimistic locking — two
   simultaneous decrements against the same row simply serialize at the DB
   row-lock level, and the result never goes negative.
4. Re-read the row's `current_stock` after the update and run the
   threshold/auto-86 check shared with restock (§5).

## 5. Auto-86 + low-stock alert (INV-03 / INV-05)

Both live in `InventoryService`, invoked after **every** operation that
changes `current_stock` (kitchen decrement above, admin restock, and
initial creation with a non-default starting stock) — one shared method,
not duplicated per call site:

```java
void applyStockSideEffects(InventoryItem item, MenuItem menuItem) {
    if (item.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0) {
        if (menuItem.isAvailable()) {
            menuItem.setAvailable(false); // auto-86, INV-05
        }
    } else if (!menuItem.isAvailable()) {
        menuItem.setAvailable(true); // auto un-86, per scope decision #6
    } else if (item.getCurrentStock().compareTo(item.getLowStockThreshold()) <= 0) {
        broadcastLowStock(item, menuItem); // INV-03
    }
}
```

`broadcastLowStock` sends directly via the already-injected
`SimpMessagingTemplate` to `/topic/inventory/{tenantId}` — no new domain
event type, matching how `CashShiftService`/`PaymentService` broadcast
directly rather than introducing an intermediate event for a
WebSocket-only side effect. Payload: `{type: 'LOW_STOCK', menuItemId,
menuItemName, currentStock, unit, threshold}`.

No STOMP SUBSCRIBE-destination ACL is added for this topic — accepted
pre-existing gap, same as `/topic/kitchen/{tenantId}` and
`/topic/waiter/{tenantId}`.

### Frontend subscription

`store/websocket.ts` gains a **dedicated slot**, not a reuse of
`currentSubscription`:

```ts
inventorySubscription: any | null,
subscribeToInventory: (tenantId: string) => void,
unsubscribeFromInventory: () => void,
```

Mirrors `waiterSessionSubscription`/`subscribeToWaiterSession` (added in
report 123 specifically to stop different pages fighting over one shared
slot). `Inventory.tsx` subscribes on mount, unsubscribes on unmount. On a
`LOW_STOCK` message: `queryClient.invalidateQueries({queryKey:
['inventoryItems']})` plus a toast alert. The subscription is scoped to
the Inventory admin page only — no global always-on admin subscription, matching how `subscribeToKitchen`/`subscribeToWaiter` are each scoped to their own page.

## 6. Backend CRUD + admin UI (INV-01 / INV-04)

New `inventory.controller.InventoryItemController` at `/catalog/inventory`
(route-prefix convention matches `ModifierGroupController`'s
`/catalog/modifier-groups` — this is catalog-adjacent data gated by
method-level `@PreAuthorize`, not a path prefix):

| Method & path | Purpose |
|---|---|
| `GET /catalog/inventory` | list all `InventoryItem`s for the tenant, joined with `MenuItem` name/`available` for display |
| `POST /catalog/inventory` | create tracking for a `MenuItem` (`menuItemId`, `unit`, `currentStock`, `lowStockThreshold`) — rejects if that `MenuItem` already has one (unique constraint) or belongs to another tenant |
| `PATCH /catalog/inventory/{id}` | edit `unit`/`lowStockThreshold` (not `currentStock` — that's restock, below) |
| `POST /catalog/inventory/{id}/restock` | body `{delta: BigDecimal}`, applied via the same atomic clamped `GREATEST(current_stock + delta, 0)` update as §4 (race-safe against a concurrent kitchen decrement), can be negative for manual correction/shrinkage, runs `applyStockSideEffects` after |
| `DELETE /catalog/inventory/{id}` | removes tracking entirely (hard delete — unlike `MenuItem.available`, there's no "already-placed order" snapshot referencing `InventoryItem`, so nothing to preserve) |

Routes added to `SecurityAuditTest`'s 401 matrix, per existing convention.

**Admin UI**: new `Inventory.tsx` page at `/admin/inventory`, entry added
to `FloatingNav` (same pattern as the existing Analytics link). Table of
tracked items: dish name, unit, current stock, threshold, a red/yellow
badge when at/under threshold or at zero. "+ Rastrear" opens a modal to
pick an untracked `MenuItem` and set initial stock/unit/threshold. Each
row has an inline "Restock" action (small form: delta amount) and an edit
action (threshold/unit). New `inventoryService` in `api.ts`
(`list`/`create`/`update`/`restock`/`remove`). i18n: new `admin/inventory`
namespace (ES default + EN), following the existing per-role-namespace
convention; any zod form needing localized messages uses the
`createXSchema(t)` factory pattern (per EMB-i18N-08).

## 7. Testing

- `InventoryServiceTest`: clamped decrement never goes negative,
  `applyStockSideEffects` transitions (`>0→0` auto-86, `0→>0` auto-un-86,
  threshold crossing triggers broadcast, no-op when already in the target
  state), restock with a negative delta.
- `InventoryConfirmedItemsListenerTest` (or covered inside
  `InventoryServiceTest` depending on how the plan splits it): correct
  per-`menuItemId` quantity grouping from a `KitchenItemsConfirmed` batch
  with duplicate `itemId`s, silent skip when no `InventoryItem` exists.
- New routes added to `SecurityAuditTest`.
- No new frontend component tests — consistent with the rest of the
  catalog/admin UI, which has none today.

## 8. Deferred (explicitly out of scope for v1)

- Per-ingredient/recipe (bill-of-materials) tracking — granularity stays
  per-`MenuItem` only (§2.1).
- Stock consumption from `ModifierOption` selections.
- Any global always-on low-stock notification outside the Inventory admin
  page (e.g. a persistent header badge across the whole admin app).
- Distinguishing system-driven vs admin-driven `MenuItem.available`
  changes — auto-86/un-86 always wins on the next stock-crossing event
  (§2.6).
- Inventory history/audit log of individual restock events (only the
  current `current_stock`/`updated_at` is kept, no ledger — unlike
  `LoyaltyTransaction`'s append-only pattern).

## 9. Task breakdown (reference — full task list lives in `PROGRESS.md`)

INV-01 model (`InventoryItem` + migration + repo) · INV-02 stock decrement
listener on `KitchenItemsConfirmed` · INV-03 low-stock threshold +
real-time WebSocket broadcast + dedicated frontend subscription slot ·
INV-04 backend CRUD (`/catalog/inventory`) + Admin UI · INV-05 auto-86 /
auto-un-86 of `MenuItem.available` (implemented inside
`InventoryService.applyStockSideEffects`, shared by INV-02's decrement path
and INV-04's restock/create paths — not a separate listener).
