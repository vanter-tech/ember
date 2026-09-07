# Report 398 — EMB-FEAT-HUB: Ember Hub waiter-managed seats

## 1. Identification
- **Report number:** 398
- **Task ID:** EMB-FEAT-HUB (T1–T10)
- **Predecessor task:** Report 397 — guest table-join (Cloud), PR #97 (merged `7dba69fe`)
- **Branch:** `spec/hub-waiter-seats` (rebased onto `main` after #97 merged)
- **Spec:** `docs/superpowers/specs/2026-09-07-hub-waiter-managed-seats-design.md`
- **Plan:** `docs/superpowers/plans/2026-09-07-hub-waiter-managed-seats.md`

## 2. Objective
Make the Ember Hub build fully waiter-driven. The Hub runs on a restaurant LAN with no
customer-reachable server, so the collaborative-cart / join-table flow is dead weight there.
Instead the waiter opens a table with a fixed set of named, account-less "seats", renames /
adds / removes them, and adds items per seat through the existing `waiter-items` path; split
billing is unchanged. The customer subsystem and the loyalty admin UI are stripped from the
Hub bundle. **No DB migration** — `Participant` is a JSON column and `userId` is already nullable.

## 3. Modified Files

### Backend
- `backend/src/main/java/com/vanter/ember/session/dto/CreateSessionRequest.java` — `seatNames` component
- `backend/src/main/java/com/vanter/ember/session/dto/AddSeatRequest.java` — new
- `backend/src/main/java/com/vanter/ember/session/dto/RenameSeatRequest.java` — new
- `backend/src/main/java/com/vanter/ember/session/event/ParticipantRenamed.java` — new
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java` — `createSession` 4-arg + seat seeding, `addSeat` / `renameSeat` / `removeSeat`, null-safe participant matching
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java` — `POST` / `PATCH` / `DELETE /sessions/{id}/participants…`, null-safe participant check
- `backend/src/main/java/com/vanter/ember/session/listener/SessionWebSocketListener.java` — `ParticipantRenamed` → `/topic/session/{id}`
- `backend/src/main/java/com/vanter/ember/session/listener/WaiterWebSocketListener.java` — `ParticipantRenamed` → `/topic/waiter/{tenantId}`
- `backend/src/main/java/com/vanter/ember/loyalty/listener/LoyaltyAccountJoinListener.java` — null-`userId` guard
- Tests: `SessionServiceTest`, `SessionControllerTest`, `SessionWebSocketListenerTest`, `WaiterWebSocketListenerTest`, `LoyaltyAccountJoinListenerTest`, new `HubSeatFlowIntegrationTest`, `E2EOrderFlowTest` / `GuestJoinFlowIntegrationTest` (4-arg `CreateSessionRequest` call sites)

### Frontend
- `frontend/src/lib/isHubBuild.ts` — new; `export const isHubBuild = import.meta.env.BASE_URL !== '/'`
- `frontend/src/App.tsx` — customer pages lazy-loaded, `/menu/join` + `/customer/*` routes gated behind `!isHubBuild`, `RoleRedirect` sends a stale CUSTOMER token back to `/login`
- `frontend/src/pages/auth/Login.tsx` — use the shared `isHubBuild` module
- `frontend/src/pages/waiter/components/ParticipantsQrModal.tsx` — Hub branch renders seat-name inputs (no QR), creates the session with the names and navigates to `/waiter/tables/{id}`
- `frontend/src/pages/waiter/components/SeatFormModal.tsx` — new; add / rename form + remove-confirm, owns every seat mutation + `sessionDetails` / `bill` invalidation
- `frontend/src/pages/waiter/TableInformation.tsx` — Hub-only "Agregar asiento" + per-row rename / remove controls, participant card `key` fixed to `participant.name`, `<SeatFormModal/>` mounted
- `frontend/src/lib/api.ts` — `SessionTableService.createSession` 3rd `seatNames` arg; `addSeat` / `renameSeat` / `removeSeat`
- `frontend/src/store/websocket.ts` — `PARTICIPANT_JOINED` / `PARTICIPANT_RENAMED` invalidate `['sessionDetails', sessionId]`
- `frontend/src/store/uiStore.ts` — `SEAT_FORM` / `DELETE_SEAT` modal types
- `frontend/src/components/SettingsBar.tsx` — no `FIDELIZACION` group when `isHubBuild`
- `frontend/src/pages/admin/Settings.tsx` — `FIDELIZACION` / `LOYALTY_REWARDS` render `null` when `isHubBuild`
- `frontend/src/components/GlobalSearchResults.tsx` — loyalty settings entries skipped when `isHubBuild`
- `frontend/src/locales/es/waiter.ts`, `frontend/src/locales/en/waiter.ts` — seat i18n keys
- Tests: new `App.hubBuild.test.tsx`, `ParticipantsQrModal.test.tsx`, `SeatFormModal.test.tsx`, `TableInformation.seats.test.tsx`, `SettingsBar.hubBuild.test.tsx`

## 4. What Changed?

**Seat seeding (T1).** `SessionService.createSession` takes a nullable `List<String> seatNames`.
When present, it seeds exactly `maxParticipants` `Participant` rows with `userId == null`: provided
names in order, blank / missing slots auto-named `"Asiento N"` by 1-based seat position (bumped
past any collision with a provided name). More names than seats, or duplicate names, →
`IllegalArgumentException` (409). Cloud passes `null` and nothing is seeded.

**Null-safety sweep (T2).** Eight `p.getUserId().equals(x)` participant-stream matches in
`SessionService` plus one in `SessionController.getSession` now use `Objects.equals(...)` — a
name-only seat has a null `userId`. `LoyaltyAccountJoinListener` returns early when
`event.userId() == null` (in addition to the existing `guest` guard), so a name-only seat never
spawns a loyalty account.

**`ParticipantRenamed` event (T3).** New record broadcast to `/topic/session/{id}` and
`/topic/waiter/{tenantId}` by the two websocket listeners.

**Three WAITER endpoints (T4–T5).**
- `POST /sessions/{id}/participants` — append one seat; blank name → lowest free `"Asiento N"`;
  capacity bumped to fit; publishes `ParticipantJoined(userId=null)`.
- `PATCH /sessions/{id}/participants` — rename `from`→`to`; rewrites `participantName` on every
  `OrderItem` and `SessionActivity` that referenced the old name; **blocked once a non-voided
  bill exists** (409, the splits are name-keyed); publishes `ParticipantRenamed`.
- `DELETE /sessions/{id}/participants/{name}` — reject an account-backed seat (403); discard its
  DRAFT items, keep sent ones; reuses the leave semantics — an empty table with no billable items
  is CLOSED, otherwise `ParticipantLeft` lets billing redistribute an unpaid split.
All three require the assigned waiter and an OPEN session.

**Frontend fork on `isHubBuild` (T6–T9).** `isHubBuild` is `import.meta.env.BASE_URL !== '/'`
(true only for the `--base=/app/` Hub bundle). The customer subsystem is lazy-loaded and its
routes (`/menu/join`, `/customer/*`) are not registered in the Hub build; the assign-table modal
collects seat names instead of showing a QR and routes straight to the table detail; the table
detail grows add / rename / remove seat controls (Hub only); the loyalty settings group, its
panels and its global-search entries are hidden in the Hub build. Every gated path is behind
`!isHubBuild`, so the Cloud bundle is byte-identical.

## 5. Why It Changed?
The Ember Hub is an on-prem, LAN-only deployment for a single restaurant. Customers' phones
never reach the Hub server, so the QR / join-code collaborative-cart flow cannot work there and
the waiter has to be able to run the whole table: seat the party, name each cover, order per
cover, split the bill. Name-only `Participant` rows reuse the entire existing per-participant
ordering and split-billing machinery — the only new backend surface is seeding at open time and
three add/rename/remove endpoints, plus a null-safety pass because `Participant.userId` can now
be null. Loyalty is a cloud feature (no customer app on the Hub to accrue points from), so its
admin UI is removed from that build to avoid dead settings screens.

## 6. Verification
- Backend `cd backend && ./mvnw test` — **1195 / 1195**, BUILD SUCCESS (baseline pre-effort 1167;
  +28 across the seat seeding, null sweep, `ParticipantRenamed`, three endpoints, loyalty guard
  and the `HubSeatFlowIntegrationTest`).
- Frontend `cd frontend && pnpm run build` clean, `pnpm run lint` clean (16 pre-existing
  warnings, 0 errors), `pnpm run test:run` — **115 / 115** (baseline 104; +11 across five new
  Hub-build test files).
- No Flyway script added.

## 7. Deviations from the plan
- **Auto-seat naming.** The plan's `nextAutoSeatName` helper (lowest-free-from-1) contradicted its
  own T1 test, which expects position-based `"Asiento 3" / "Asiento 4"` when seeding. Seeding is
  position-based with a collision bump; `addSeat` uses the lowest-free-from-1 variant (its own
  test expects that). Both plan tests pass as written.
- **`HubSeatFlowIntegrationTest`.** The bill-blocks-rename 409 could not be exercised end-to-end —
  `POST /billing/sessions/{id}/request` and `/bill` both 409'd on a billing precondition not worth
  satisfying in this test. That path stays covered by `renameSeat_billExists_throws` (service) and
  `renameSeat_billExists_returns409` (controller slice); the integration test instead asserts the
  assigned-waiter guard (403 for a stranger) over real HTTP + DB + events, plus seed → GET →
  add → rename-cascade → remove.
- **i18n key placement.** `addSeatLabel` / `renameSeatTitle` / `removeSeatTitle` were added in T7
  (used for the +/- button aria-labels) rather than T8.
