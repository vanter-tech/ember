# Report 274 — HPD-03: prod profile management port, forwarded headers, health details

## 1. Identification
- **Report number:** 274
- **Task ID:** HPD-03 (Hosted Production Deployment — Phase 1, Task 3)
- **Predecessor task:** HPD-02 (report 273 — `PublicPingController`)

## 2. Objective
Under the `prod` profile, isolate the Actuator management endpoints on a loopback-only
port and trust proxy-forwarded headers, so routing `api.ember.vanter.net` → `:8080`
through the Cloudflare Tunnel never exposes `/actuator/*` and scheme/host reconstruction
works behind the Tunnel.

## 3. Modified Files
- `backend/src/main/resources/application-prod.properties` — added 3 prod-only settings.
- `backend/src/test/java/com/vanter/ember/config/ProdManagementPortConfigTest.java` — **new**, config-file contract test.

## 4. What Changed?
Appended to `application-prod.properties`:

```properties
server.forward-headers-strategy=framework
management.server.port=8081
management.endpoint.health.show-details=never
```

New `ProdManagementPortConfigTest` (3 tests) loads `application-prod.properties` from the
classpath into a `java.util.Properties` and asserts each of the three keys resolves to its
expected value. Written test-first: confirmed RED (all 3 `expected … but was: null`) before
the properties were added, GREEN after.

## 5. Why It Changed?
- **`management.server.port=8081`** — the prod Compose (HPD-05) publishes this port on
  `127.0.0.1` only, so `/actuator/health` and `/actuator/prometheus` are reachable by the
  on-VM Ops Agent (HPD-14 metrics scrape) but never through the Tunnel or the LAN. The main
  API stays on `8080`. This is the reason HPD-02's public `/v1/public/ping` exists — the
  external uptime check can no longer hit `/actuator/health`.
- **`server.forward-headers-strategy=framework`** — behind the Cloudflare Tunnel the app
  sees the `cloudflared` container as the peer; `X-Forwarded-*` must be honoured to
  reconstruct the original `https://api.ember.vanter.net` scheme/host.
- **`management.endpoint.health.show-details=never`** — base `application.yml` sets
  `show-details: always`; prod must not leak component health (DB, disk, etc.) detail.
- **Test approach:** the plan's primary `@SpringBootTest(webEnvironment = RANDOM_PORT)` +
  `@ActiveProfiles("prod")` variant needs a live Postgres (the prod profile pins
  `ddl-auto=validate`) and the suite has no prod-profile boot-test convention, so the
  plan's documented fallback — a config-file contract test — was used.

## 6. Verification
- `./mvnw test -Dtest=ProdManagementPortConfigTest` → 3 run, 0 failures.
- `./mvnw test` (full suite) → **897 tests, 0 failures, BUILD SUCCESS** (894 baseline + 3 new).
