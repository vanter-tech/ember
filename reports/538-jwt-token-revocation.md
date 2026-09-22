# Report 538 — F-17: JWT session revocation via `tokenVersion`

## 1. Identification
- **Report:** 538
- **Task ID:** F-17-TOKEN-REVOCATION (security debt item from `PROGRESS.md`'s Security debt list)
- **Predecessor Task:** report 537 — HUB-ACTIVATION-NO-PASSWORD-HASH (F-15)
- **Branch:** `fix/jwt-token-revocation` off `main`

## 2. Objective
Close the "no revocation" half of F-17 (JWT in `localStorage`, 24h TTL, no way to invalidate an
already-issued token before it expires). A stolen token used to remain valid up to 24h no matter
what the account owner or an admin did, unless the account was fully deactivated. Storage location
(`localStorage`) and TTL length are explicitly out of scope — see report's "Why" section.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V17__user_token_version.sql` (new)
- `backend/src/main/java/com/vanter/ember/identity/model/User.java`
- `backend/src/main/java/com/vanter/ember/identity/service/JwtService.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetails.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetailsService.java`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/main/java/com/vanter/ember/identity/controller/UserAdminController.java`
- Tests: `JwtServiceTest`, `AuthServiceTest`, `EmberUserDetailsServiceTest`,
  `UserAdminServiceTest`, `UserAdminControllerTest`, `SecurityAuditTest` (updated); new
  `backend/src/test/java/com/vanter/ember/config/TokenVersionAccessTest.java`

## 4. What Changed?
- `users.token_version` (int, default 0, idempotent `V17`, same `ADD COLUMN IF NOT EXISTS`
  pattern as `V11`/`V13`/`V16`).
- `JwtService.extractTokenVersion(token)` reads the new `ver` claim, defaulting to `0` when
  absent (tokens minted before this change).
- `AuthService.buildResponse` (the single choke point for every user-token issuance — register,
  login, PIN login, tenant-scoped re-issue) now puts `user.getTokenVersion()` into the `ver` claim.
- New `EmberUserDetails` (extends Spring's `User`) carries the account's live `tokenVersion`.
  `EmberUserDetailsService.loadUserByUsername` now builds this instead of the plain
  `org.springframework.security.core.userdetails.User` builder.
- `SecurityConfig`'s `jwtAuthFilter`: right next to the existing `isEnabled()` check (which
  already re-loads the user from the DB on every request), a new `tokenVersionMatches` check
  rejects the token when its `ver` claim is behind the account's live `tokenVersion` — same
  fallthrough-to-401 path already used for a deactivated account's stale token.
- `UserAdminService.setPin`/`clearPin` and `updateProfile`'s deactivation branch now bump
  `tokenVersion`. New `revokeSessions(userId, tenantId)` method + `@PreAuthorize("hasRole('ADMIN')")`
  endpoint `POST /admin/staff/{userId}/revoke-sessions` (204, wired into `SecurityAuditTest`'s
  401 matrix) for an explicit "sign out everywhere" admin action with no other side effect.

## 5. Why It Changed?
`SecurityConfig`'s `jwtAuthFilter` already does a DB round-trip (`loadUserByUsername`) on every
authenticated request to check `isEnabled()` — deactivating a user already kills their session on
the next request, not just at their next login. But nothing could do the same for an account that
stays **active**: a PIN reset, a suspected-stolen device, or "sign out everywhere" had no effect on
a token already in the attacker's hands — it simply outlived the action, valid for up to 24h. Since
the DB lookup already happens every request, adding one integer comparison (`ver` claim vs. live
`tokenVersion`) closes that gap for free — no session store, no Redis, no change to where the token
lives client-side.

Deliberately **not** in scope (see `docs/superpowers/plans/` for a possible follow-up
"F-17-b"): moving the JWT out of `localStorage` into an `httpOnly` cookie (removes the token from
JS-reachable storage entirely, closing the XSS theft vector at the source) and shortening the 24h
TTL. Both are real further hardening but are a bigger, cross-cutting change (STOMP handshake via
`JwtChannelInterceptor`, CORS between Cloud and Hub, CSRF) — this report only lands the cheap,
self-contained revocation half that was approved.

## 6. Verification
- TDD throughout: every new/changed behavior had a failing test first (compile-error or wrong
  assertion), confirmed RED, then minimal code to GREEN — see commit history on this branch.
- `./mvnw test`: **1519/1519**, 0 failures, 0 errors (was 1502/1502 with the 2 known pre-existing,
  unrelated port-59999 environment failures before this branch; not reproduced in this run).
