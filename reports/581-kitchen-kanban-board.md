# Report 581 — KITCHEN-KANBAN-BOARD

## 1. Identification
- **Report number:** 581
- **Task ID:** KITCHEN-KANBAN-BOARD
- **Predecessor:** report 580 (KITCHEN-REMOVE-VIEW-DETAILS-BUTTON)

## 2. Objective
Give the KDS a clear place to organize cooking: orders were a single horizontal row with no separation by state.

## 3. Modified Files
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`
- `frontend/src/pages/kitchen/components/QueueCard.tsx`
- `frontend/src/pages/kitchen/OrdersDisplay.test.tsx`
- `frontend/src/locales/es/kitchen.ts`, `frontend/src/locales/en/kitchen.ts`

## 4. What Changed?
`OrdersDisplay` renders a 3-column board (Pendiente / Preparando / Listo), each with a count badge and its own vertical scroll. An order appears in every column where it has items. `QueueCard` now takes `status` and `now`, shows only the items in that status, the elapsed minutes (red from 15 min; refreshed every 30 s) and the per-item advance button. `FocusedCard` (print, bulk status) is kept unchanged. New i18n keys `kdsColumnEmpty`, `elapsedMinutes`.

## 5. Why It Changed?
Cooks need to see at a glance what is waiting, in progress and ready, and which tickets are late. Build clean, lint 0 errors, kitchen tests 9/9. Not visually verified.
