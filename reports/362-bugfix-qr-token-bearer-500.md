# Report 362 — bugfix: QR session token as Bearer caused unhandled 500

## 1. Identification
- **Report:** 362
- **Task:** bugfix-qr-token-bearer-500 (user-requested, surfaced by `QA_SIMULATION_REPORT_v2.md`'s live re-verification of `AUDIT_BLUEPRINT.md` F-13)
- **Predecessor:** report 361 (FIX-QA branch remediation)

## 2. Objective
`QA_SIMULATION_REPORT_v2.md` found that presenting a QR session token as `Authorization: Bearer`
(e.g. `GET /sessions/{id}`, `GET /printing/agents/me/printers`) threw an unhandled exception,
surfacing as a raw `500` instead of the clean `401` `AUDIT_BLUEPRINT.md` F-13 expected. Fix it.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/session/service/QrTokenService.java`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/test/java/com/vanter/ember/config/QrTokenAsBearerRejectionTest.java` (new)

## 4. What Changed?
**Root cause:** `SecurityConfig.jwtAuthFilter` only skipped the user-lookup branch when a
token's `typ` claim was exactly `"print-agent"`. A QR session token (subject = session id, no
`typ` claim at all) fell into the user-lookup branch, which called
`userDetailsService.loadUserByUsername(sessionId)` — `sessionId` is never a real email, so this
threw `UsernameNotFoundException`. Because the filter runs *before* the `DispatcherServlet`, the
exception never reached `GlobalExceptionHandler` (a `@RestControllerAdvice`, which only sees
exceptions from controller invocations) — it escaped the filter chain as an unhandled `500` for
*any* endpoint, `permitAll` or not.

**Fix (two parts):**
1. `QrTokenService.generateQrToken` now stamps QR tokens with `"typ": "session-qr"`, same pattern
   as print-agent tokens — giving every non-user token type a positive, checkable marker instead
   of relying on the absence of a claim.
2. `SecurityConfig.jwtAuthFilter`'s condition changed from `!"print-agent".equals(type)` to
   `type == null` — only a token with no `typ` claim at all (a genuine user token) is a candidate
   for `loadUserByUsername`. Any other current or future non-user token type is skipped by
   construction, not by a growing list of string comparisons. As defense-in-depth, the
   `loadUserByUsername` call is also wrapped in a `try/catch (UsernameNotFoundException)` that
   silently skips authentication (same treatment already given to a disabled account) rather than
   letting the exception escape the filter — so an unanticipated future token/subject mismatch
   degrades to a clean `401` instead of a `500`.

New test `QrTokenAsBearerRejectionTest` (`@SpringBootTest` + `MockMvc`, same pattern as
`DeactivatedUserAccessTest`): a `typ=session-qr` token presented as Bearer to `GET /admin/staff`
now gets `401`, not `500`.

## 5. Why It Changed?
This is a real availability/hygiene bug (a raw `500`, not the standard `ProblemDetail` JSON body
the rest of the API returns), not a security bypass — the QR token's forged-proof `rid`/subject
never granted access to anything, it just crashed the filter first. Root-causing it also closes
the design gap `AUDIT_BLUEPRINT.md` F-13 flagged (QR tokens indistinguishable from user tokens by
claim shape) for free, without needing a matching change in every consumer of `typ`.

## 6. Verification
- Backend: `./mvnw test -Dtest=QrTokenAsBearerRejectionTest,QrTokenServiceTest,DeactivatedUserAccessTest,PrintAgentTokenFlowIntegrationTest,SecurityAuditTest` — 88/88.
- Full suite: `./mvnw test` — **1030/1030 BUILD SUCCESS**.
- No frontend files touched; no frontend verification needed for this change.
