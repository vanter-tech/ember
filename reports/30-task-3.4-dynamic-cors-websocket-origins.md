# Report 30 — task-3.4: dynamic CORS & WebSocket origin configuration

## 1. Identification
- **Report number:** 30
- **Task ID:** task-3.4
- **Predecessor Task:** task-3.3 (report 29 — `GlobalExceptionHandler` catch-all + `ProblemDetail`)

## 2. Objective
Replace the hardcoded browser-origin allowlists (`http://localhost:5173` / `http://localhost:3000` in `CorsConfig`, and the single hardcoded `http://localhost:5173` in `WebSocketConfig`) with one externally-configurable policy that supports per-tenant subdomains (`<businessName>.ember.vanter.com`) without a redeploy per tenant.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/CorsProperties.java` (new)
- `backend/src/main/java/com/vanter/ember/config/CorsConfig.java`
- `backend/src/main/java/com/vanter/ember/config/WebSocketConfig.java`
- `backend/src/main/resources/application.yml`
- `.env.example`
- `backend/src/test/java/com/vanter/ember/config/CorsConfigTest.java` (new)
- `backend/src/test/java/com/vanter/ember/config/WebSocketConfigTest.java`

## 4. What Changed?

### `CorsProperties` (new, `@ConfigurationProperties("ember.cors")`)
Single source of truth for the browser-origin policy, with these bound keys:

| Key | Default |
| --- | --- |
| `ember.cors.allowed-origins` | `http://localhost:5173`, `http://localhost:3000` |
| `ember.cors.allowed-origin-patterns` | *(empty)* |
| `ember.cors.allowed-methods` | `GET, POST, PUT, DELETE, PATCH, OPTIONS` |
| `ember.cors.allowed-headers` | `*` |
| `ember.cors.exposed-headers` | *(empty)* |
| `ember.cors.allow-credentials` | `true` |
| `ember.cors.max-age` | `3600` |

The defaults reproduce the previous hardcoded REST behaviour exactly, so an unconfigured deployment behaves as before.

### `CorsConfig`
Now `@EnableConfigurationProperties(CorsProperties.class)` and builds the `CorsConfiguration` from the bean instead of inline literals. Adds `allowedOriginPatterns`, `exposedHeaders` and `maxAge` (previously the preflight cache was Spring's implicit default).

### `WebSocketConfig`
`registerStompEndpoints` injects the same `CorsProperties` and feeds both `setAllowedOrigins(...)` and `setAllowedOriginPatterns(...)` on the `/ws` SockJS endpoint, replacing the hardcoded `"http://localhost:5173"`.

### `application.yml` / `.env.example`
New `ember.cors` block reading `${EMBER_CORS_ALLOWED_ORIGINS:...}` (defaulted to the two localhost dev servers) and `${EMBER_CORS_ALLOWED_ORIGIN_PATTERNS:}` (empty by default). Both documented in `.env.example`; production sets the pattern to `https://*.ember.vanter.com`. Neither is a secret, so both keep in-code fallbacks (unlike the §3.1 credentials, which fail fast).

### Tests (+5, all passing)
- `CorsConfigTest` (4, via `ApplicationContextRunner`): defaults admit only the two localhost origins; a configured `https://*.ember.vanter.com` pattern admits arbitrary tenant subdomains while still admitting the exact list; lookalike origins are rejected (`https://acme.ember.vanter.com.evil.com` suffix attack, `http://` scheme downgrade, the bare apex); every knob is overridable from configuration.
- `WebSocketConfigTest` (+1): the `/ws` STOMP endpoint receives exactly the origins and patterns held in `CorsProperties`, proving REST and socket policy cannot drift apart.

## 5. Why It Changed?
- **Tenant subdomains cannot be enumerated at build time.** A SaaS that provisions `<businessName>.ember.vanter.com` per tenant would need a code change and redeploy for every signup under a static allowlist. Wildcard patterns make onboarding a DNS + tenant-record concern (feeding into task-4.3).
- **`allowedOrigins` cannot express that, and `*` is illegal here.** With `allowCredentials=true` the CORS spec forbids answering `Access-Control-Allow-Origin: *`; Spring throws at request time if you try. `allowedOriginPatterns` is the sanctioned mechanism — it matches the request origin and echoes back the concrete value. Hence two distinct lists rather than one loosened one; exact origins stay exact.
- **The WebSocket endpoint was the weaker half of the same policy.** It allowed a single hardcoded dev origin, so any non-localhost deployment (including every tenant subdomain) would have had its SockJS handshake rejected while REST calls succeeded — a real-time outage that looks like an app bug. Sharing one properties bean makes divergence structurally impossible.
- **Defaults stay closed.** `allowed-origin-patterns` is empty unless a deployment opts in, so a self-hosted or dev instance does not silently accept wildcard hosts; `CorsConfigTest` pins that.

## 6. Verification
`cd backend && ./mvnw test` — **384/384 passing, 0 failures, 0 errors** (379 baseline + 5 new).
