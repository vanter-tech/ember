# Report 192 — Task INV-04 (frontend half / Task 5): Admin inventory UI

## 1. Identification
- **Report number:** 192
- **Task ID:** INV-04 frontend half — Task 5 of `docs/superpowers/plans/2026-08-22-emb-inv.md` (also finishes wiring INV-03's low-stock alert and INV-05's restock/create reachability). Final task of the EMB-INV backlog.
- **Predecessor Task:** INV-04 backend half (report 191)

## 2. Objective
Build the admin UI at `/admin/inventory` so admins can opt menu items into stock tracking, edit unit/threshold, restock, and stop tracking — consuming INV-03's dedicated WebSocket slot and INV-04's backend CRUD endpoints.

## 3. Modified Files
- Modify: `frontend/src/lib/api.ts` (`inventoryService`, `inventoryMenuItemService`)
- Modify: `frontend/src/store/uiStore.ts` (`CREATE_INVENTORY_ITEM`/`EDIT_INVENTORY_ITEM` modal types)
- Modify: `frontend/src/App.tsx` (`/admin/inventory` route)
- Modify: `frontend/src/components/FloatingNav.tsx` (nav link, `Warehouse` icon)
- Create: `frontend/src/pages/admin/Inventory.tsx`
- Create: `frontend/src/pages/admin/components/NewInventoryItemModal.tsx`
- Create: `frontend/src/pages/admin/components/EditInventoryItemModal.tsx`
- Modify: `frontend/src/locales/es/admin.ts`, `frontend/src/locales/en/admin.ts`
- Modify: `frontend/src/locales/es/common.ts`, `frontend/src/locales/en/common.ts`

## 4. What Changed?
- `inventoryService` (`getAll`/`create`/`update`/`restock`/`remove`) wraps the `/catalog/inventory` endpoints; a small dedicated `inventoryMenuItemService.listAll` fetches every menu item across all categories (up to 500) for the "pick an untracked item" dropdown — deliberately not reusing `menuItemService.getAll`, which is paginated per-category.
- `Inventory.tsx`: owns its own STOMP connect/subscribe lifecycle (mirrors `KitchenLayout`/`WaiterLayout`, page-scoped since no other admin page opens the connection), subscribes to `subscribeToInventory` on mount, shows each tracked item as a card (stock, unit, "Agotado"/"Stock bajo" badges), and surfaces `lastLowStockAlert` as a toast via `clearLowStockAlert`.
- `NewInventoryItemModal`: menu-item picker limited to untracked items, unit/initial-stock/threshold fields, calls `inventoryService.create`.
- `EditInventoryItemModal`: shows current stock, a restock input + "Aplicar" button (`inventoryService.restock`), unit/threshold edit form, and a destructive "Eliminar rastreo" action (`inventoryService.remove`).
- New locale keys added to both `es`/`en` `admin.ts` (inventory dialog/labels/toasts) and `common.ts` (`navInventory`), keeping the `en` file's `satisfies typeof esAdmin` constraint intact.
- Nav link added between "Modificadores" and "Analíticas" using the `Warehouse` lucide icon.

## 5. Why It Changed?
This is the last of the five EMB-INV tasks — without it, INV-03's WebSocket alert plumbing and INV-04's backend CRUD had no UI consumer, and INV-05's auto-86/restock logic had no way to be triggered from the admin side (only the kitchen-decrement path was reachable). The page-scoped STOMP lifecycle (vs. subtree-scoped like Kitchen/Waiter) matches the fact that no other admin page currently needs a live connection.

## Verification
- `cd frontend && pnpm run build` → PASS (`tsc -b && vite build`).
- Manual browser smoke test (per plan Step 10) was **not completed this session** — see report 193, which found and fixed a WebSocket-connectivity regression (unrelated to this task's own code) that was blocking *all* tenant-facing STOMP connections, discovered while investigating a user-reported bug on the customer side. That fix should be re-verified together with this page's connect/subscribe/alert flow in a live browser before this task is considered fully verified end-to-end.
