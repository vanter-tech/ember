# Report 582 — KITCHEN-FOCUSED-CARD-STATUS-COLUMNS

## 1. Identification
- **Report number:** 582
- **Task ID:** KITCHEN-FOCUSED-CARD-STATUS-COLUMNS
- **Predecessor:** report 581 (KITCHEN-KANBAN-BOARD)

## 2. Objective
Keep the KDS as a horizontal row of small per-order cards with the first (leftmost) order shown large below, and move the status columns inside that large card.

## 3. Modified Files
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.tsx`
- `frontend/src/pages/kitchen/components/FocusedCard.test.tsx`
- `frontend/src/pages/kitchen/OrdersDisplay.test.tsx`

## 4. What Changed?
Reverted the page-level board of r581: `OrdersDisplay` again renders one `QueueCard` per order in a horizontally scrolling row (page now scrolls vertically) with `FocusedCard` below. `QueueCard` is the compact card again (all items with status and advance button) plus elapsed minutes (red from 15 min, refreshed every 30 s via `now`). `FocusedCard` groups the order's items into Pendiente / Preparando / Listo columns with counts; print, selection and bulk status kept. FocusedCard tests pick the status option by role since "Listo" is now also a column title.

## 5. Why It Changed?
The status columns belong to the order being cooked, not the whole queue. Build clean, lint 0 errors, kitchen tests 9/9. Not visually verified.
