# Report 54 — task-5.11

## Identification
- **Report:** 54
- **Task ID:** task-5.11
- **Predecessor Task:** task-5.10 (report 53)

## Objective
Add a STOMP connection-state indicator to the KDS so kitchen staff can see when the live WebSocket feed drops.

## Modified Files
- `frontend/src/pages/kitchen/OrdersDisplay.tsx`

## What Changed?
`OrdersDisplays` now reads `isConnected` from `useWebsocketStore` and renders a `Badge` (existing `components/ui/badge.tsx`) in the top-right corner of the KDS header block: `default` variant + "Conectado" when connected, `destructive` variant + "Desconectado" when not, each with a small status dot.

## Why It Changed?
`useWebsocketStore.isConnected` already tracked STOMP connect/disconnect/error state, but nothing in the KDS surfaced it — a dropped connection was invisible to kitchen staff, who would see a frozen queue with no explanation. Reusing the existing Badge primitive and store field kept the change minimal, no new state or backend work required.
