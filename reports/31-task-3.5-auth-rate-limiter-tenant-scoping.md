# Report 31 — task-3.5: auth rate limiter (leak, proxy IPs, tenant scoping)

## 1. Identification
- **Report number:** 31
- **Task ID:** task-3.5
- **Predecessor task:** task-3.4 (report 30 — dynamic CORS/WebSocket origins)

## 2. Objective
Fix the login/register rate limiter's unbounded memory growth and its proxy-blind client-IP
resolution, and rescope its buckets from bare IP to `(tenant + IP)` so one tenant's traffic can no
longer consume another tenant's allowance on a shared egress address.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java` (new)
- `backend/src/main/java/com/vanter/ember/config/AuthRateLimiterFilter.java`
- `backend/src/test/java/com/vanter/ember/config/AuthRateLimiterFilterTest.java`
- `backend/src/main/resources/application.yml`
- `.env.example`

## 4. What Changed?

### 4.1 The limiter was dead in production (found while implementing)
`shouldNotFilter` compared `request.getRequestURI()` against the literals `/auth/login` and
`/auth/register`. `getRequestURI()` **includes the servlet context path**, and
`application.yml` sets `server.servlet.context-path: /v1/`, so every real request arrived as
`/v1/auth/login`, matched neither literal, and was skipped. The filter had never throttled a single
production request. Path matching now runs against `pathWithinApplication()`, which strips
`getContextPath()` (and any trailing slash) before comparing, and the guarded path list moved into
configuration (`ember.ratelimit.paths`).

The old tests did not catch this because they invoked `doFilterInternal` directly — bypassing
`shouldNotFilter` — using a `/api/auth/login` URI that matches neither the old constants nor the
real route.

### 4.2 Memory leak
`ConcurrentHashMap<String, Deque<Long>>` entries were created per IP and never removed: timestamps
inside a deque were pruned, but an emptied deque stayed mapped forever. Every distinct source IP
that ever hit `/auth/*` was retained for the process lifetime.

- Buckets are now dropped, not just drained: a sweep walks the map and removes any bucket whose hits
  have all aged out. It is CAS-guarded on `nextSweepAt` so it runs at most once per window, on
  whichever request first crosses the deadline — no scheduler or extra thread.
- All bucket mutation moved from `synchronized (deque)` to `ConcurrentHashMap.compute` /
  `computeIfPresent`. Both run under the bin lock, which makes "prune, count, append" and
  "prune, remove-if-empty" atomic with respect to each other — a plain `remove(key)` from a sweep
  could otherwise discard a hit recorded concurrently.
- `maxTrackedKeys` (default 100 000) is a last-resort cap: once reached, new tenant buckets stop
  being created and requests fall back to the per-IP counter instead of growing the map.

### 4.3 Proxy IP resolution
`getRemoteAddr()` behind a load balancer is the balancer's address, which collapsed every client
into one bucket — 10 requests/min for the entire internet. `resolveClientIp` now walks
`X-Forwarded-For` **right to left and only when the peer is a configured proxy**
(`ember.ratelimit.trusted-proxies`, literal IPs or CIDR, matched with Spring Security's
`IpAddressMatcher`), returning the first hop that is not itself a trusted proxy. Hops are normalized
for `:port` and `[::1]` bracket forms; a non-IP-literal hop (`unknown`, obfuscated identifiers)
aborts the walk and falls back to the peer address, which also guarantees no attacker-supplied
string ever reaches `InetAddress` resolution and triggers a DNS lookup. The trusted-proxy list is
**empty by default**, so an unconfigured deployment keeps using the peer address rather than
believing a spoofable header.

### 4.4 Tenant scoping
Buckets are now keyed `(tenant, client IP)`. Before any JWT exists there is no `rid` claim to read,
so the tenant is derived from the request host's leading label when the host falls under a
configured suffix (`ember.ratelimit.tenant-host-suffixes`, e.g. `acme.ember.vanter.com` → `acme`),
matching the subdomain scheme task-3.4 established. `X-Forwarded-Host` is consulted only behind a
trusted proxy. Hosts outside every configured suffix share one untenanted bucket, and the feature is
inert until a suffix is configured.

### 4.5 Per-IP ceiling (added alongside the rescope)
Because the tenant now comes from a client-controlled `Host` header, tenant-only keying would let a
caller mint a fresh 10-request bucket per forged subdomain — bypassing the limiter and inflating the
map. A second counter, `ipMaxRequests` (default 30), caps one IP across *all* tenants, so the
rescope widens the legitimate allowance without opening a bypass. A denied request still counts
against the IP ceiling; over-counting rejects is the conservative direction.

### 4.6 Response shape
A 429 previously returned a bare status with no body. It now emits an RFC 7807 `ProblemDetail`
(`application/problem+json`) plus a `Retry-After` header, matching the format task-3.3 standardized
— the filter runs outside `@RestControllerAdvice`, so it serializes the payload itself with the
injected Jackson `ObjectMapper`.

### 4.7 Configuration
`ember.ratelimit.*` (`enabled`, `max-requests`, `ip-max-requests`, `window`, `trusted-proxies`,
`tenant-host-suffixes`, `max-tracked-keys`, `paths`) is bound by `RateLimitProperties`, wired in
`application.yml` behind `EMBER_RATELIMIT_*` environment variables and documented in `.env.example`.
Defaults preserve the previous intent (10 requests / 60 s).

## 5. Why It Changed?
- **A silently disabled control is worse than none:** the context-path mismatch meant credential
  stuffing against `/v1/auth/login` was completely unthrottled while the codebase and tests implied
  otherwise. Fixing this is the substance of the task; the leak and proxy issues only matter once
  the filter actually executes.
- **Unbounded retention is a slow OOM** on a public endpoint, where the key space is the whole IPv4
  and IPv6 internet.
- **Proxy-blind keying inverts the control:** in a deployment behind a load balancer it throttles
  all users collectively at 10/min (a self-inflicted DoS) while giving individual attackers no
  isolation. Trusting `X-Forwarded-For` unconditionally would be the opposite failure — a one-header
  bypass — hence the explicit trusted-proxy allowlist.
- **Tenant isolation is the platform's core invariant** (tasks 2.11–2.18): a restaurant whose staff
  share an office NAT should not be able to lock another tenant's staff out of login.
- **The IP ceiling closes the hole the rescope would otherwise open,** keeping the change a net
  security gain rather than a trade.

## 6. Verification
`./mvnw test` → **BUILD SUCCESS, 398/398 tests passing** (baseline 384; `AuthRateLimiterFilterTest`
grew from 4 to 18 cases covering context-path matching, window expiry, bucket eviction, tenant
separation, the key cap, forwarded-header trust, hop walking and non-literal hop fallback).
The `@SpringBootTest` contexts (`EmberApplicationTests`, `E2EOrderFlowTest`) confirm the new
constructor-injected filter and `@EnableConfigurationProperties` binding start cleanly.

## 7. Deployment Note
`EMBER_RATELIMIT_TRUSTED_PROXIES` **must** be set to the load balancer's address/CIDR when the app is
deployed behind one; otherwise the limiter keys on the balancer IP and throttles all tenants
together. Set `EMBER_RATELIMIT_TENANT_HOST_SUFFIXES=ember.vanter.com` in the same environment to
activate per-tenant buckets.
