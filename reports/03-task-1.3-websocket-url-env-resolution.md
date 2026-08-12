# Report 03 — task-1.3

## 1. Identification
- **Report Number:** 03
- **Task ID:** task-1.3
- **Predecessor Task:** task-1.2

## 2. Objective
Replace the hardcoded `http://localhost:8080/v1/ws` WebSocket URL in `store/websocket.ts` with environment-variable resolution, consistent with the existing `EMBW_API_URL` runtime-injection pattern used by `lib/api.ts`.

## 3. Modified Files
- `frontend/src/lib/api.ts`
- `frontend/src/store/websocket.ts`
- `frontend/entrypoint.sh`
- `frontend/.env`

## 4. What Changed?
- Added `EMBW_WS_URL?: string` to the global `Window.ENV` type declaration in `api.ts`.
- In `websocket.ts`, the SockJS factory now resolves the URL via `window.ENV?.EMBW_WS_URL || import.meta.env.VITE_WS_URL || 'http://localhost:8080/v1/ws'`, mirroring the API client's resolution order (runtime nginx injection → build-time Vite var → hardcoded fallback).
- `entrypoint.sh` now also emits `EMBW_WS_URL` into `env-config.js` at container start, defaulting to `http://localhost:8080/v1/ws` if unset.
- Added `VITE_WS_URL=http://localhost:8080/v1/ws` to `frontend/.env` for local dev builds.

## 5. Why It Changed?
The WebSocket endpoint was hardcoded to `localhost`, breaking real-time connectivity in any non-local deployment (staging/production) despite the REST API client already supporting environment-based resolution. This change brings WebSocket URL resolution in line with the established `EMBW_API_URL` pattern so both can be configured per-environment without code changes.
