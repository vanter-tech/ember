# Report 467

## 1. Identification
- **Report Number:** 467
- **Task ID:** KDS-BULK-STATUS-UPDATE Task 4 (`FocusedCard.tsx` bulk-select UI) — **plan complete**
- **Predecessor Task:** report 466 (Task 3, frontend API client + i18n)

## 2. Objective
Give kitchen staff the bulk-selection UI on the focused ticket card: a per-dish circular checkbox, a "select all" toggle, and a status dropdown that bulk-moves every selected dish to the chosen status via Task 2's endpoint. `QueueCard.tsx` and the existing per-item advance button are untouched, per the user's explicit scope decision.

## 3. Modified Files
- Modify: `frontend/src/pages/kitchen/components/FocusedCard.tsx`
- Create: `frontend/src/pages/kitchen/components/FocusedCard.test.tsx`
- Modify: `frontend/src/test/setup.ts`

## 4. What Changed?
`FocusedCard.tsx`: added `selectedIds` state (`Set<string>`), a `bulkUpdateMutation` (calls `kitchenServices.updateItemsStatus(order.id!, Array.from(selectedIds), status)`, invalidates the `kitchenOrders` query, clears the selection on success), and `toggleItem`/`toggleSelectAll`/`allSelected` helpers. A new toolbar row under the header shows a "Seleccionar todo"/"Deseleccionar todo" `Button` (disabled when there are no visible items) and, only once `selectedIds.size > 0`, a `Select` dropdown offering the 4 kitchen-facing statuses (`PENDING`/`PREPARING`/`READY`/`DELIVERED` — never `DRAFT`, which items never reach on the KDS) that fires the bulk mutation immediately on choice, no separate "apply" button. Each item's `<li>` gained a leading circular `Checkbox` (`className="rounded-full"` overriding the component's default `rounded-[4px]`), wired to `selectedIds`. The existing `DELIVERED` filter, the per-item "advance one step" button, and everything else in the file is unchanged; `QueueCard.tsx` was not touched.

New `FocusedCard.test.tsx` (4 tests, `@testing-library/react` + `@testing-library/user-event`, `kitchenServices` mocked): dropdown hidden with no selection; selecting one item reveals the dropdown and picking a status calls `updateItemsStatus('ko-1', ['item-1'], 'READY')`; "Seleccionar todo" selects every visible item and toggles its own label; selection clears after a successful bulk update.

Also fixed a real, general test-infra gap found while writing this test: `src/test/setup.ts` had no polyfill for the Pointer Events capture API (`hasPointerCapture`/`setPointerCapture`/`releasePointerCapture`) or `scrollIntoView`, both of which jsdom doesn't implement and Radix's `Select` calls internally when opened via a real pointer click — every test in the file threw `TypeError: target.hasPointerCapture is not a function` until this was added. This is the same category of gap the file's existing `ResizeObserver` polyfill addresses (also a Radix-primitive jsdom gap), so it was added there rather than locally in the new test file — it will silently unblock any future test that opens a Radix `Select`/`Popover` via `userEvent.click`.

## 5. Why It Changed?
Implements Task 4 of the approved plan — the final task, completing the feature end-to-end as the user specified across the design conversation: select-all/select-some circular checkboxes and a status dropdown on the focused ticket only, immediate apply, clears after success.

## 6. Verification
- `pnpm exec vitest run src/pages/kitchen/components/FocusedCard.test.tsx` — **4/4 passing**.
- `pnpm run test:run` (full suite) — **132/132** (was 128, +4).
- `pnpm run build` — clean.
- `pnpm run build:hub` — clean.

**KDS-BULK-STATUS-UPDATE plan is now fully implemented** (Tasks 1-4, reports 464-467).
