# Report 273 — HPD-02: Public ping endpoint for the external uptime check

## 1. Identification
- **Report number:** 273
- **Task ID:** HPD-02 (Hosted Production Deployment plan, `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md`, Task 2)
- **Predecessor Task:** report 272 (HPD-01 — `minio.public-url` config + `ImageUploadService` URL build)

## 2. Objective
Add an unauthenticated `GET /v1/public/ping` liveness endpoint (body `pong`, `text/plain`) so the Cloud Monitoring uptime check (spec §7) has a public probe URL. Phase 1 Task 3 (HPD-03) moves `/actuator/**` onto a loopback-only management port, so `/actuator/health` will not be reachable through the Cloudflare Tunnel.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/PublicPingController.java` (created)
- `backend/src/test/java/com/vanter/ember/config/PublicPingControllerTest.java` (created)
- `PROGRESS.md` (state + Task Queue checkbox)
- `reports/273-hpd-02-public-ping.md` (this file)

## 4. What Changed?
- **`PublicPingController`** — `@RestController @RequestMapping("/public")`, single handler `@GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)` returning the literal `"pong"`. No new security rule needed: `SecurityConfig` already has `.requestMatchers("/public/**").permitAll()` (line 61). With the app's `/v1` context path the live route is `GET /v1/public/ping`.
- **`PublicPingControllerTest`** — `@WebMvcTest(PublicPingController.class)` + `@Import({SecurityConfig.class, CorsConfig.class})`, mirroring `PublicRestaurantControllerTest`. Mocks the beans `SecurityConfig` needs but this slice doesn't provide: `JwtService`, `UserDetailsService`, `UserRepository`, `RestaurantRepository`. One test: `ping_returnsPongWithoutAuth` asserts 200 + body `pong` with no `Authorization` header.
- **Plan drift:** the plan's sketch test imported only `PublicPingController` and no mocks; running it that way failed context load because the imported `SecurityConfig` autowires `RestaurantRepository` (and the JWT-filter collaborators). Followed the plan's own Step 1 note ("mirror an existing `*ControllerTest` in `config`/`restaurant`") and copied `PublicRestaurantControllerTest`'s `@MockBean` set instead.

## 5. Why It Changed?
The external uptime check must hit a URL that is (a) public through the Tunnel and (b) not behind auth. `/actuator/health` fails (a) after HPD-03; every other endpoint fails (b). A dedicated trivial `/public/ping` under the already-permitted `/public/**` prefix is the minimal addition.

## Verification
- `./mvnw test -Dtest=PublicPingControllerTest` — 1/1 PASS (RED first: `cannot find symbol PublicPingController`).
- `./mvnw test` (full suite) — **894/894 PASS**, 0 failures, 0 errors (893 baseline from report 272 + 1 new).
