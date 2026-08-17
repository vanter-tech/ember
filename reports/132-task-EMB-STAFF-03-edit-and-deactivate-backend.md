# Report 132 — task-EMB-STAFF-03

**Predecessor Task:** EMB-STAFF-01..02 (create-staff endpoint + modal, reports 130–131)

## Objective
Back the "edit/delete employee" request with real backend capability: let `PATCH /admin/staff/{userId}` update `name`/`email` (not just HR-profile fields), and make the existing `active` flag — reused as the "delete" action per the user's explicit choice — actually revoke access instead of being purely cosmetic.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/dto/UpdateStaffProfileRequest.java`
- `backend/src/main/java/com/vanter/ember/identity/service/UserAdminService.java`
- `backend/src/main/java/com/vanter/ember/identity/service/EmberUserDetailsService.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/test/java/com/vanter/ember/identity/service/UserAdminServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`
- `backend/src/test/java/com/vanter/ember/identity/service/EmberUserDetailsServiceTest.java` (new)
- `backend/src/test/java/com/vanter/ember/config/DeactivatedUserAccessTest.java` (new)

## What Changed?
- **Investigation finding before writing code:** `User.active` was checked nowhere in the auth path — `AuthService.login` does its own manual `userRepository.findByEmail` + password match, bypassing `EmberUserDetailsService`/`AuthenticationManager` entirely, and `EmberUserDetailsService.loadUserByUsername` built a `UserDetails` with no `disabled`/`enabled` wiring at all. A deactivated employee could still log in and act. Presented this to the user as a 3-way choice (deactivate-cosmetic / deactivate-enforced / hard-delete); they chose enforced deactivation.
- `UpdateStaffProfileRequest`: added optional `name`/`email` fields, appended at the end of the record (not inserted where logically grouped) specifically to avoid breaking every existing positional-constructor call site in the test suite.
- `UserAdminService.updateProfile`: applies `name`/`email` when present; email change only re-checks `existsByEmail` when the new value actually differs from the user's current one (editing without changing email, or re-submitting the same email, no longer false-positives as "already in use").
- `EmberUserDetailsService.loadUserByUsername`: now sets `.disabled(!user.getActive())` on the built `UserDetails`.
- `AuthService.login`: rejects with the same generic `BadCredentialsException("Invalid credentials")` used for wrong-password/unknown-email when `!user.getActive()` — no account-status leak.
- `SecurityConfig.jwtAuthFilter`: only sets `SecurityContextHolder`'s authentication when `userDetails.isEnabled()`; when disabled, simply doesn't authenticate and lets the existing `anyRequest().authenticated()` rule 401 it — no new response-writing code, reuses the exact path an absent/invalid token already takes. This is what makes an *already-issued* JWT for a since-deactivated user stop working on its very next request, not just block future logins.
- New `EmberUserDetailsServiceTest` (enabled/disabled/not-found cases) and `DeactivatedUserAccessTest` — a real `@SpringBootTest`/`MockMvc`/real-`JwtService` integration test (mirrors `PlatformAuthIsolationTest`'s shape) proving a deactivated user's token gets 401'd on the next request, with a paired "active user's token still works" regression guard.

## Why It Changed?
Direct continuation of the payment-cycle-adjacent staff work: the user asked to be able to edit and delete employee info. Investigating "delete" surfaced that reusing the existing `active` flag — the obvious, audit-trail-preserving choice, matching how the codebase already models employee status — would be silently non-functional without this enforcement work, so it was surfaced and approved before implementing.

## Verification
`cd backend && ./mvnw test` — full suite green (exit code 0), including all new/updated test files.
