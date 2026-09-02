# Report 333 — fix: waiter table-view action buttons overflow with Spanish labels

## 1. Identification
- **Report number:** 333
- **Current Task:** fix-waiter-table-view-action-button-width
- **Predecessor Task:** report 332 (fix-waiter-table-view-untranslated-buttons)

## 2. Objective
The red "Agregar platillo" button (and its two sibling action buttons) in the
individual table view had a fixed width `w-38` (152px). shadcn's `Button` applies
`whitespace-nowrap`, so the longer Spanish label overflowed the rounded pill and
looked badly laid out ("mal distribuido").

## 3. Modified Files
- `frontend/src/pages/waiter/TableInformation.tsx`

## 4. What Changed?
The three header action buttons (`:184-199` — Imprimir cuenta / Transferir /
Agregar platillo) had `w-38 h-18` swapped for `px-6 h-18`. Each pill now sizes to
its own content, so the row stays coherent in both Spanish and English regardless
of label length. Height, radius, colors, icons and the flex `gap-3` container are
unchanged.

## 5. Why It Changed?
`w-38` was tuned for the shorter English strings ("Add Item", "Print Bill"). Once
the ES labels were translated (report 332) they no longer fit the fixed box and
`whitespace-nowrap` pushed the text past the pill edge. Switching to
horizontal-padding sizing removes the language-dependent overflow without
introducing per-locale styling.

## Verification
- `cd frontend && pnpm run build` — PASS (`tsc -b && vite build`, built in 1.96s,
  0 TypeScript errors).
