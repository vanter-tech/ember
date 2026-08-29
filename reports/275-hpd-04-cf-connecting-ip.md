# Report 275 — HPD-04: rate limiter trusts `CF-Connecting-IP` behind a configured proxy

## 1. Identification
- **Report number:** 275
- **Task ID:** HPD-04 (Hosted Production Deployment — Phase 1, Task 4)
- **Predecessor task:** HPD-03 (report 274 — prod management port)

## 2. Objective
When the immediate peer is a configured trusted proxy (the `cloudflared` container behind
the Cloudflare Tunnel), key the auth rate-limit buckets off `CF-Connecting-IP` — the true
client address Cloudflare guarantees and strips from any client-supplied value at the edge
— rather than the unreliable `X-Forwarded-For` chain that path produces.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java`
- `backend/src/test/java/com/vanter/ember/config/AuthRateLimiterFilterTest.java`

## 4. What Changed?
- New constant `CF_CONNECTING_IP = "CF-Connecting-IP"` beside `FORWARDED_FOR`.
- `resolveClientIp(...)`: immediately after the existing `if (!trustedPeer) return peer;`
  guard, if `CF-Connecting-IP` is present and (after `normalizeIp`) an IP literal
  (`isIpLiteral`), return it. The existing rightmost-non-proxy `X-Forwarded-For` walk stays
  as the fallback below it. Method javadoc updated to describe the new precedence.
- 3 new tests (in the "proxy resolution" block, mirroring the `X-Forwarded-For` trust tests):
  - `trustedPeerPrefersCfConnectingIpForBucketKey` — trusted peer `10.0.0.5`, two requests
    with `CF-Connecting-IP: 203.0.113.9` share a bucket (11th → 429); a request with a
    different `CF-Connecting-IP` is a separate bucket (→ 200).
  - `cfConnectingIpTakesPrecedenceOverForwardedForBehindATrustedProxy` — with both headers
    present, buckets key off `CF-Connecting-IP`; a varying `X-Forwarded-For` does not open
    fresh buckets (11th → 429).
  - `untrustedPeerIgnoresCfConnectingIp` — peer not in `trustedProxies`; buckets key off the
    peer, the header is ignored (varying `CF-Connecting-IP` still → 429 on the 11th).

## 5. Why It Changed?
With the Cloudflare Tunnel the app's TCP peer is always the `cloudflared` sidecar, so
`getRemoteAddr()` and the `X-Forwarded-For` hop it injects do not identify the real client
— every external caller would share one rate-limit bucket. Cloudflare's `CF-Connecting-IP`
is the documented true-client header and is overwritten (not appended) at the edge, so a
client cannot forge it. The change is confined to the existing `trustedPeer` gate: an
untrusted peer's `CF-Connecting-IP` is still ignored, and a non-literal value falls through
to the unchanged `X-Forwarded-For` logic, so no new spoofing surface is introduced. The
`prod` env sets `EMBER_RATELIMIT_TRUSTED_PROXIES` to the docker bridge CIDR (HPD-05).

## 6. Verification
- `./mvnw test -Dtest=AuthRateLimiterFilterTest` → 21 run, 0 failures (2 of the 3 new tests
  RED before the implementation; the untrusted-peer guard test was green by construction).
- `./mvnw test` (full suite) → **900 tests, 0 failures, BUILD SUCCESS** (897 baseline + 3 new).
- `SecurityAuditTest` → 75/75 green.
- `security-review` skill run on the diff — see below.

## 7. Security Review
`security-review` skill run on the diff — **no HIGH or MEDIUM findings**. Rationale: the
`CF-Connecting-IP` lookup sits after the existing `if (!trustedPeer) return peer;` guard, so
it is consulted only when the TCP peer already matches a configured
`ember.ratelimit.trusted-proxies` CIDR — no header-spoofing bypass is added (identical model
to the existing `X-Forwarded-For` handling). The resolved value flows only into in-memory
rate-limit bucket keys — never SQL, OS commands, file paths, an HTTP client host, or logs —
and `normalizeIp`/`isIpLiteral` constrain it to `[0-9a-fA-F.:]`, so no hostname reaches a
resolver and no metacharacters survive. Residual risk is rate-limit attribution only (a
party already inside the trusted CIDR), which is an excluded category and grants nothing
beyond what the pre-existing `X-Forwarded-For` trust already allows.
