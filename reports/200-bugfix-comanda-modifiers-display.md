# Report 200 — bugfix-comanda-modifiers-display

## 1. Identification
- **Report Number:** 200
- **Task ID:** bugfix-comanda-modifiers-display (ad-hoc bugfix, not in the milestone backlog)
- **Predecessor Task:** report 199 (toast slide-in animation)

## 2. Objective
Show selected item modifiers below the product name in the customer's comanda review screen (`ComandaView.tsx`), both in the draft/pending cards and the sent-order history panel.

## 3. Modified Files
- `frontend/src/pages/customer/ComandaView.tsx`

## 4. What Changed?
- Draft/pending item rows (Participants section): added a conditional `<span>` under the item name rendering `item.modifiers.map(m => m.optionName).join(', ')` when `item.modifiers` is non-empty.
- History item rows (Historial section): restructured the row into a `flex-col` wrapper and added the same conditional modifiers line beneath the name/price row.

## 5. Why It Changed?
`OrderItemDto.modifiers` (`SelectedModifier[]`, carrying `groupName`/`optionName`/`priceDelta`) has been populated by the backend since MOD-03/MOD-05 (customer modifier selection flow), and the Kitchen Display already renders it (`QueueCard.tsx`/`FocusedCard.tsx`). `ComandaView.tsx` was the one remaining customer-facing surface that read every other `OrderItemDto` field but never `modifiers`, so a customer adding modifiers to an item could not see them when reviewing what they were about to send to the kitchen. Root cause confirmed via `systematic-debugging`: the field existed end-to-end, only the render was missing — no backend or type change needed.

## Verification
`cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, no errors).
