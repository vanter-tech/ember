# Report 466

## 1. Identification
- **Report Number:** 466
- **Task ID:** KDS-BULK-STATUS-UPDATE Task 3 (frontend API client + i18n keys)
- **Predecessor Task:** report 465 (Task 2, bulk endpoint)

## 2. Objective
Give the frontend a typed client method for Task 2's bulk endpoint and the copy strings the bulk-selection UI (Task 4) will need, in both locales.

## 3. Modified Files
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/locales/es/kitchen.ts`
- Modify: `frontend/src/locales/en/kitchen.ts`

## 4. What Changed?
Added `kitchenServices.updateItemsStatus(orderId: string, itemIds: string[], status: OrderItemStatus): Promise<kitchenOrders>` right after the existing `updateItemStatus`, calling `PATCH /kitchen/orders/${orderId}/items/status` with body `{ itemIds, status }` — matches Task 2's endpoint path/body shape exactly. No new generated OpenAPI type needed; reuses the existing `OrderItemStatus` export.

Added 4 i18n keys to `es/kitchen.ts` (`kdsSelectAll`, `kdsDeselectAll`, `kdsBulkStatusPlaceholder`, `kdsSelectItemAriaLabel`) and the matching English strings to `en/kitchen.ts`.

## 5. Why It Changed?
Directly implements Task 3 of the approved plan — the plumbing Task 4's `FocusedCard.tsx` UI will call and display.

## 6. Verification
`cd frontend && pnpm run build` — clean (`tsc -b` passes, which also enforces `en/kitchen.ts`'s `satisfies typeof esKitchen` key parity; `vite build` succeeds). No test suite change — this task is pure typed plumbing/copy, no new logic to unit test.
