# Report 186 — Task MOD-05: Selector de modificadores al agregar ítem (cliente)

## 1. Identification
- **Report:** 186
- **Task ID:** MOD-05 (EMB-MOD backlog, 5th of 6)
- **Predecessor Task:** MOD-04 (report 185)

## 2. Objective
Let a customer choose modifier options (size, doneness, extras) when adding a menu item with `modifierGroups` to the cart, sending the selection to the already-built MOD-03 backend.

## 3. Modified Files
- `frontend/src/components/ui/radio-group.tsx` (new)
- `frontend/src/lib/api.ts`
- `frontend/src/pages/customer/components/SelectModifiersModal.tsx` (new)
- `frontend/src/pages/customer/Menu.tsx`
- `frontend/src/locales/es/customer.ts`, `frontend/src/locales/en/customer.ts`

## 4. What Changed?
- `SessionTableService.addItem` now takes an optional `selectedOptionIds: number[]` param, sent as `{ menuItemId, selectedOptionIds }` in the POST body (matches `AddItemRequest`'s compact-constructor default on the backend).
- New `SelectModifiersModal`: renders each of the item's `modifierGroups` as a `RadioGroup` (`SINGLE_REQUIRED`) or a `Checkbox` list (`MULTI_OPTIONAL`/`MULTI_LIMITED`, capped at `maxSelections`), computes a live running total from `item.price` + selected `priceDelta`s, and disables submit until every group's `minSelections` is met.
- `Menu.tsx`: the "+" button now branches — items with no modifier groups add straight to cart as before; items with `modifierGroups.length > 0` open the modal instead. `mutation` gained `selectedOptionIds`; on success it also clears `selectingItem`.
- Added ES/EN keys: `selectModifiersDialogTitle`, `addToCartButton`, `requiredSelectionHint`, `limitedSelectionHint`.

## 5. Why It Changed?
Completes MOD-05 of the EMB-MOD backlog (`docs/superpowers/plans/2026-08-22-emb-mod.md`) — MOD-03 already validates/prices `selectedOptionIds` server-side and MOD-04 lets admins define groups, but nothing on the customer cart UI could send a selection yet. MOD-06 (KDS/ticket propagation) is next.

## 6. Deviation from the plan's literal snippets
- `frontend/src/components/ui/radio-group.tsx` did not exist yet, as flagged in MOD-04's report. Built on the already-installed unified `radix-ui` meta-package's `RadioGroup` export (confirmed present via a quick `node -e "require('radix-ui')"` check), matching `checkbox.tsx`'s function-component/`data-slot` convention. No new dependency added.
- Added a `requiredSelectionHint`/`limitedSelectionHint` line under each group's name in the modal (the plan defined the keys but its illustrative JSX snippet never actually rendered them) — otherwise the two translated strings would sit unused and the min/max constraint would be invisible to the customer until they tried to submit.

## 7. Verification
`cd frontend && pnpm run build` → PASS (`tsc -b && vite build`), no missing-key errors from the `en/customer.ts` `satisfies` check. No browser click-through (`claude-in-chrome` unavailable this session, same recurring gap as prior EMB-MOD tasks).
