# Report 432

**Task ID:** ad-hoc — Waiter table view: table code + QR info button
**Predecessor Task:** report 431 — `/waiter/cash-register` hide write actions from ADMIN

## Objective
On the waiter's table detail page (`/waiter/tables/:id`), add a button next to "Imprimir cuenta" that shows the table's join code and QR (previously only shown once, at table-open time, in `ParticipantQrModal`).

## Modified Files
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java`
- `frontend/src/lib/api.ts`
- `frontend/src/pages/waiter/TableInformation.tsx`
- `frontend/src/locales/es/waiter.ts`
- `frontend/src/locales/en/waiter.ts`

## What Changed?
`GET /sessions/{id}/qr` (`WAITER`-only, already checks the caller is the table's assigned waiter) now also returns the session's `joinCode` alongside the freshly-minted `qrToken` — the code was already a stable field on `Session`, just never exposed outside the create-table flow. `SessionControllerTest.sampleSession()` didn't set a `joinCode` (defaulted to `null`), which would have made the new `Map.of(...)` throw an NPE (`Map.of` rejects null values) — fixed by giving the fixture a `joinCode("ABCDE")`, matching the real invariant that every `Session` always has one; `getQr_returnsToken` now also asserts `$.joinCode`.

`TableInformation.tsx` adds a `CircleAlert`-icon `Button` (`Popover`, not `Dialog` — no backdrop, consistent with the report-issue floating action's earlier fix) right before "Imprimir cuenta" in the header actions row, disabled whenever the other table actions are (`actionsDisabled`, i.e. the session isn't `OPEN`). Opening it lazily fetches `SessionTableService.getQrToken(id)` (react-query, `enabled: qrPopoverOpen`) and renders the same `QRCodeSVG` + join-code block used by `ParticipantQrModal`, reusing its `joinCodeLabel`/`qrPlaceholderLabel` i18n keys plus 2 new ones (`tableQrInfoLabel`, `qrLoadingLabel`). Each open regenerates a fresh QR token (stateless 15-min JWT, `QrTokenService` — no invalidation of prior tokens, so this is safe/idempotent), which is a feature here: the code shown is never stale/expired.

## Why It Changed?
User: waiters need to re-share a table's code/QR after the fact (e.g. a diner lost the code, or wants to add someone mid-meal) — currently that information only ever appears once, in the modal shown at table-open time, with no way back to it.

## Verification
`cd backend && ./mvnw test` — 1216/1216 passed (full suite, not just the touched test class).
`cd frontend && pnpm run build` — clean.
`cd frontend && pnpm run test:run` — 121/121 passed.
Verified live in Chrome (claude-in-chrome) against the user's own backend (restarted in IntelliJ to pick up the Java change, since there's no devtools hot-reload configured): button renders next to "Imprimir cuenta", opens a non-blurring popover showing the QR and "Codigo para entrar a la mesa: XM5UY".
