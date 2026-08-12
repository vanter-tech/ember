# Report 05 — task-1.5

## Identification
- **Report Number:** 05
- **Current Task ID:** task-1.5
- **Predecessor Task:** task-1.4

## Objective
Fix WebSocket state handling so `isConnected` is reset to `false` when the STOMP connection drops or errors, instead of only being set `true` on connect.

## Modified Files
- `frontend/src/store/websocket.ts`

## What Changed?
Added `client.onDisconnect` and `client.onStompError` handlers in `connect()`, alongside the existing `client.onConnect`. Both new handlers call `set({isConnected: false})`; `onStompError` additionally logs the STOMP error frame message before resetting state.

## Why It Changed?
Previously only `onConnect` updated `isConnected`, so after a broker-initiated disconnect or a STOMP protocol error the store still reported `isConnected: true`. Consumers of this flag (e.g. `subscribeToSession` callers, UI connection indicators) could act on stale state. The handlers ensure the store accurately reflects live connection status per CLAUDE.md §3 real-time synchronization priority.
