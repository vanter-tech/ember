# Report 539 — F-10/E-23: PIN-login enumeration rate limit + audit logging

## 1. Identification
- **Report:** 539
- **Task ID:** F-10-PIN-ENUMERATION-MITIGATION (security debt item from `PROGRESS.md`'s Security debt list)
- **Predecessor Task:** report 538 — F-17-TOKEN-REVOCATION
- **Branch:** `fix/pin-enumeration-rate-limit` off `main`

## 2. Objective
Mitigate F-10/E-23 (`POST /auth/login/pin` lets an anonymous caller tell which emails exist and
which have a PIN configured, via a 401/409/423 split) **without** touching that response contract
— the distinction is a deliberate, user-ratified product decision (`QuickLoginModal`'s UX depends
on it, confirmed 2026-09-04, see `QA_SIMULATION_REPORT_v2.md`). Two defense-in-depth pieces:
throttle the endpoint harder than the shared auth budget, and make an enumeration attempt visible
in logs.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java`
- `backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- Tests: `AuthRateLimiterFilterTest` (+5), `AuthServiceTest` (+4)

## 4. What Changed?
- `RateLimitProperties.pinLoginMaxRequests` (default 5/min, `0` disables it): a **third**, tighter
  ceiling checked only for `/auth/login/pin`, on top of — not instead of — the existing shared
  `(tenant, IP)` budget (10/min) and the IP-wide ceiling (30/min). Same bucket/sweep mechanics as
  the two existing counters (`AuthRateLimiterFilter`'s sliding window), just a third key prefixed
  `pin:` instead of `t:`.
- `AuthService.loginWithPin` now logs one `WARN` (via a new `@Slf4j` logger on the class) on each
  of the four rejection paths that carry enumeration signal: unknown email, no PIN set, wrong PIN,
  and a locked account (wrapped `PinAttemptGuard.assertNotLocked`/`PinLockedException`). The PIN
  value itself is never logged, only the email and the reason. The client-visible exception/status
  code for every path is unchanged — verified by the pre-existing `loginWithPin_*` tests, all still
  green.

## 5. Why It Changed?
`AuthRateLimiterFilter` already threw an IP + tenant-scoped sliding-window budget in front of
every unauthenticated auth endpoint (`RateLimitProperties.paths`), but that budget (10/min) is
**shared** across `/auth/login`, `/auth/login/pin`, `/sessions/join`, etc. — for a small
restaurant's staff list (typically <20 people), 10 requests/minute is enough to enumerate the
whole tenant in under 2 minutes. Since unifying the response codes was already explicitly rejected
by the user, the two remaining levers that don't touch the contract are: slow the harvest down
(tighter, path-specific budget) and make it observable (audit log) — both land here.

**Explicitly evaluated and rejected during planning:** stripping `email` out of
`frontend/src/store/quickAccessStore.ts` (an earlier idea from this task's plan). `QuickLoginModal.tsx`
uses `profile.email` directly to build the `/auth/login/pin` request — removing it from the
persisted profile would break the "tap your chip, type your PIN" flow the store exists for, not
just its data-exposure surface. Left untouched; E-21's existing fix (don't *display* the email on
the chip) remains the only mitigation there.

## 6. Verification
- TDD throughout, including the audit-log assertions (Logback `ListAppender` attached to
  `AuthService`'s logger in `AuthServiceTest`, no new pattern needed elsewhere in the codebase).
- `./mvnw test`: **1514/1514**, 0 failures, 0 errors.
