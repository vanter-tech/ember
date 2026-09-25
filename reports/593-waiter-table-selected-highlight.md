# Report 593 — WAITER-TABLE-SELECTED-HIGHLIGHT

## 1. Identification
- Report: 593
- Task ID: WAITER-TABLE-SELECTED-HIGHLIGHT
- Predecessor: WAITER-TABLE-AVATAR-BADGE (report 592)

## 2. Objective
Make it obvious which table is selected in `waiter/Tables` when its details show in the right panel.

## 3. Modified Files
- `frontend/src/pages/waiter/Tables.tsx`

## 4. What Changed?
- The card whose `tableId === selectedTable` renders a non-interactive overlay (`absolute inset-0`, `border-2 border-[#8c1717]`, `animate-pulse`) with a 3px inset white shadow.
- Soft glow: the selected card gets an outer red blur shadow (`shadow-[0_0_8px_1px_rgba(140,23,23,0.35)]`, replacing `shadow-sm`) and the overlay adds an inner red blur next to the white band.

## 5. Why It Changed?
The details panel gave no hint of which table was clicked. A separate overlay pulses only the border (pulsing the card would fade its content), and the white inset band keeps the red border visible on occupied cards, which are already `#8c1717`.

Verification: `pnpm run build` clean, lint 0 errors, `Tables.test.tsx` 2/2. Not visually verified.
