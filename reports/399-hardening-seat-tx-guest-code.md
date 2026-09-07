# Report 399 — Hub seat hardening + public guest code-join

## 1. Identification
- **Report number:** 399
- **Current Task:** post-audit fixes on the `spec/hub-waiter-seats` branch (items 1–5 from the code-review scoring pass)
- **Predecessor Task:** report 398 — EMB-FEAT-HUB Hub waiter-managed seats

## 2. Objective
Close five loose ends flagged while scoring the recent guest-join + Hub-seats work:
1. Missing transaction boundary on the three seat-mutation endpoints.
2. Flaky `App.hubBuild` test (timeout under parallel load).
3. `addSeat` silently growing the table past `maxParticipants`.
4. Untracked cruft in the working tree (an issued license blob, a Word lock file, the local skills bundle).
5. Guest-join follow-up: a diner with only the 5-character code and no account had nowhere to enter it.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/controller/HubSeatFlowIntegrationTest.java`
- `frontend/src/App.tsx`
- `frontend/src/App.hubBuild.test.tsx`
- `frontend/src/pages/customer/JoinByCode.tsx` (new)
- `frontend/src/pages/customer/JoinByCode.test.tsx` (new)
- `frontend/src/locales/es/customer.ts`
- `frontend/src/locales/en/customer.ts`
- `.gitignore`
- removed: `to_delete/` (its `license.key` moved out of the repo tree), `docs/~$CHITECTURE.docx`

## 4. What Changed?

### Item 1 — transaction boundary on seat mutations
Added `@Transactional` to `addSeat`, `renameSeat`, `removeSeat` in `SessionController`, matching the
existing `POST /sessions/join-as-guest` pattern. `removeSeat` in particular does a read-modify-write
plus a fan-out of `DeleteItem` / `ParticipantLeft` / `SessionClosed` events consumed synchronously by
billing; without a shared transaction a failing listener left the seat removed but the split
un-rebalanced. `Session` already carries `@Version`, so the boundary also makes the optimistic-lock
retry atomic.

### Item 2 — flaky `App.hubBuild` test
Rewrote the test to mock `@/lib/isHubBuild` with a hoisted mutable flag (the pattern the other three
Hub tests already use) and a static `import App`, instead of `vi.stubEnv('BASE_URL')` +
`vi.resetModules()` + `await import('./App')`. That dynamic re-import re-parsed the whole App module
graph twice per file and blew past the 20 s timeout whenever the suite ran under CPU contention.
`isHubBuild` is only read at render time in `App`, so the mock is sufficient. Full-suite runtime
dropped from ~79 s to ~43 s and the test is stable under load.

### Item 3 — `addSeat` capacity guard
`addSeat` now rejects with `IllegalStateException` (→ HTTP 409) when the table is already at
`maxParticipants`, and no longer bumps `maxParticipants` as a side effect. The waiter raises capacity
explicitly through the existing `PATCH /sessions/{id}/capacity` first, so "add seat" never silently
grows the table. `SessionServiceTest.addSeat_overCapacity_bumpsMaxParticipants` became
`addSeat_atCapacity_throwsAndDoesNotGrowTheTable`; `HubSeatFlowIntegrationTest` now expects the 409
and calls `expandCapacity` before adding the fourth seat.

### Item 4 — working-tree cruft
- `to_delete/license.key` (a real issued Hub license blob) moved out of the repo to
  `../ember-hub-license-sample.key`; `to_delete/` removed.
- `docs/~$CHITECTURE.docx` (Word lock file) removed.
- `.gitignore`: added `*.key` (issued license blobs — no tracked `.key` files exist), `.claude/skills/`
  (local agent tooling), and `~$*` (Office lock/temp files).

### Item 5 — public guest code-join
New public page `JoinByCode` at `/join` (gated out of the Hub build like `/menu/join`): a 5-character
code field + optional name, POSTing to the existing `/sessions/join-as-guest` endpoint with
`{ joinCode, name }`. The backend already supported code-based guest join
(`JoinAsGuestRequest` accepts `joinCode` xor `qrToken`; `GuestUserService` mints the throwaway
identity) — this is the missing frontend entry point for a diner who has neither a QR scan nor an
account. Three i18n keys added per locale (`codeJoinTitle`, `codeJoinSubtitle`,
`codeJoinCodePlaceholder`); the rest reuse existing `qrJoin*` / toast strings.

## 5. Why It Changed?
- **1:** priority #3 in `CLAUDE.md` — explicit `@Transactional` boundaries and race-condition safety
  in billing-adjacent paths. The seat endpoints were the only mutation group in the guest-join batch
  left without one.
- **2:** the health line in `PROGRESS.md` claimed `115/115` while the suite actually failed ~1-in-N
  under load. Fixing the test at the source makes the claim true again and speeds every CI run.
- **3:** `maxParticipants` is the split denominator ceiling; letting `addSeat` move it implicitly
  bypasses the deliberate `expandCapacity` step and can grow a table unbounded with no feedback.
- **4:** the repo has a history of tracked secrets; an issued license blob and a directory literally
  named `to_delete/` are exactly what a stray `git add` would sweep in.
- **5:** the guest-join feature's promise is "no account required", but the only code-entry surface
  (`JoinTableModal`) is auth-gated, so that promise did not hold for a walk-in with just the code.
