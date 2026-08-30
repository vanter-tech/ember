# Report 320 — Task D: leave table, one-session-per-customer, resume on re-login

## 1. Identification
- **Report number:** 320
- **Current Task:** Task D (payment-flow bug cluster, final) — a customer can abandon
  a table; a customer cannot be seated at two tables at once; a customer who re-logs
  in while their table is still open is reconnected to it instead of bounced home.
- **Predecessor Task:** report 319 (Task C — redistribute share + settle & close)
- **Branch:** `feat/hpd-14-monitoring`

## 2. Objective
RC3 of report 317: there was no "one active session per customer" concept, no leave
endpoint, and `logout()` drops the tenant-scoped token so re-login could not read the
still-open session (customer bounced off the menu; could join a second table). Task D
closes all three, reusing Task C's `redistributeSplit` for the abandon-mid-payment
case. Per the user's decision: abandoning with items already sent to the kitchen
leaves them on the table bill; only DRAFT items are discarded.

## 3. Modified Files
### Backend
- `session/event/ParticipantLeft.java` — **new** event (`PARTICIPANT_LEFT`)
- `session/listener/SessionWebSocketListener.java` — broadcast it to the per-session topic
- `session/listener/WaiterWebSocketListener.java` — broadcast it to the floor topic
- `session/service/SessionService.java` — `leaveSession`, `resumeSession`, `rejectIfSeatedElsewhere` (called from `joinSession` + `joinSessionCode`)
- `session/controller/SessionController.java` — `POST /sessions/{id}/leave`, `POST /sessions/{id}/resume`
- `billing/listener/ParticipantLeftListener.java` — **new** best-effort redistribution
- `session/service/SessionServiceTest.java` — 12 new tests
- `session/controller/SessionControllerTest.java` — 5 new tests
- `billing/listener/ParticipantLeftListenerTest.java` — **new**, 4 tests
### Frontend
- `lib/api.ts` — `SessionTableService.leaveSession`, `resumeSession`
- `store/sessionStore.tsx` — `removeParticipant`
- `store/websocket.ts` — `PARTICIPANT_LEFT` (customer `removeParticipant`, waiter invalidate `['sessionDetails',id]`)
- `components/FloatingNav.tsx` — "Abandonar mesa" button + confirm dialog (CUSTOMER, when a participant)
- `pages/auth/Login.tsx` — CUSTOMER branch resumes a persisted open session
- `pages/customer/components/JoinTableModal.tsx` — 409 → "already at another table" toast
- `locales/{es,en}/common.ts` — 7 leave keys
- `locales/{es,en}/customer.ts` — `joinBlockedOtherTableToast`
- `locales/{es,en}/auth.ts` — `sessionResumedToast`

## 4. What Changed?
- **`SessionService.leaveSession(id, email)`**: session must be OPEN and the caller a
  participant (else `AccessDeniedException`). The leaver's `DRAFT` items are removed
  and each is published as a `DeleteItem` (`ITEM_DELETED`) frame; items already sent
  to the kitchen stay. The participant is removed. If no participants remain **and**
  nothing is billable, the session is closed (`SessionClosed`); otherwise a
  `ParticipantLeft` event is published.
- **`ParticipantLeftListener`** (billing): if a non-voided bill exists for the session
  and the departed participant still has an **`UNPAID`** split, it calls
  `PaymentService.redistributeSplit(billId, name)` (Task C) — best-effort, any
  `RuntimeException` is logged and swallowed so the leave never fails because of
  billing. Event-driven (not a direct `SessionService → PaymentService` call) to keep
  the session module free of a billing dependency.
- **`SessionService.resumeSession(id, email)`**: looks the session up **untenanted**
  (`sessionRepository.findById`, same rationale as `joinSessionCode`), requires it
  still be OPEN and the caller already a participant, then `bindResolvedTenant`. The
  controller wraps the result in `withRescopedToken` → `JoinSessionResponse {session,
  token}`, so the customer's tenant-less login token is swapped for a scoped one
  without re-joining.
- **`rejectIfSeatedElsewhere`**: `joinSession` and `joinSessionCode` now 409 when the
  user is already a participant of a *different* OPEN session in the same tenant.
  Scoped to the tenant — a stale session at another venue is not the common footgun
  and there is no cross-tenant participant finder.
- **`SessionController`**: `POST /sessions/{id}/leave` and `/resume`, both
  `hasRole('CUSTOMER')` (covered by the existing `/sessions/**` → `authenticated()`
  rule; the login token authenticates even while tenant-less, exactly like `/join`).
- **Frontend**: `FloatingNav` shows a `DoorOpen` "Abandonar mesa" button whenever the
  signed-in customer is a participant; confirming calls `leaveSession`, clears the
  session store and routes to `/customer/home`. `Login.tsx`'s CUSTOMER branch, if a
  persisted `sessionStore.id` exists, calls `resumeSession` → `setAuth({token})` +
  `setSession` + `/customer/menu`; on any failure it clears the stale session id and
  falls back to `/customer/home`. `websocket.ts` drops the departed participant from
  the customer store and refetches the waiter's `sessionDetails`. `JoinTableModal`
  surfaces the 409 as its own toast.

## 5. Why It Changed?
Before this, a customer had no way to cleanly exit a table: closing the browser left
them a participant forever, their DRAFT items stranded in everyone's cart, and — once
a bill existed — their unpaid share unassignable. Re-logging in made it worse: the
new token had no `rid`, so every session read 403'd and the app dumped them on
`/customer/home` even though their table was still open, and nothing stopped them
from scanning a second table's code and fragmenting their own order across two
sessions. `leaveSession` + the one-session guard + `resumeSession` make table
membership a thing a customer can actually end and resume, and the
`ParticipantLeft → redistribute` path means walking out mid-payment doesn't silently
drop revenue — the remaining diners absorb the share and the waiter still has
Task C's settle/void tools.

## Verification
- `cd backend && ./mvnw test` — **exit 0**, aggregated surefire: **945 tests, 0
  failures, 0 errors, 0 skipped** (925 after Task C + 21 new: `SessionServiceTest`
  +12, `SessionControllerTest` +5, new `ParticipantLeftListenerTest` ×4). Targeted
  run first: `SessionServiceTest` 56, `SessionControllerTest` 29,
  `ParticipantLeftListenerTest` 4 — the listener's best-effort-swallow test logs the
  expected `WARN … Could not redistribute …` line.
- `cd frontend && pnpm run build` — green (`tsc -b` + `vite build`, ~3s).
- `cd frontend && pnpm run lint` — **0 errors**, 17 warnings, all pre-existing.
- `cd frontend && pnpm run test:run` — **41/41 pass, 12 files**.
- Not exercised in a live browser this session (no `claude-in-chrome`).
