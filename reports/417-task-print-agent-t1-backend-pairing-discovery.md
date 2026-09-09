# Report 417 — EMB-PRINT-AGENT T1: code-based pairing + printer-discovery sink + `V10`

## 1. Identification
- **Report number:** 417
- **Current Task ID:** EMB-PRINT-AGENT — T1 (backend)
- **Predecessor Task:** report 416 — print-agent installer implementation plan

## 2. Objective
Give the `printing` module a short-code pairing handshake and a printer-discovery sink so the
future desktop agent can pair by a phone-readable code (instead of a hand-copied API key) and
report the Windows print queues it sees. Backend half only; agent/frontend/installer are
T2–T7.

## 3. Modified Files
**Created**
- `backend/src/main/resources/db/migration/V10__print_agent_pairing_and_discovery.sql`
- `backend/src/main/java/com/vanter/ember/printing/model/PairingCode.java`
- `backend/src/main/java/com/vanter/ember/printing/model/DiscoveredPrinter.java`
- `backend/src/main/java/com/vanter/ember/printing/repository/PairingCodeRepository.java`
- `backend/src/main/java/com/vanter/ember/printing/dto/PairingCodeResponse.java`
- `backend/src/main/java/com/vanter/ember/printing/dto/PairRequest.java`
- `backend/src/main/java/com/vanter/ember/printing/dto/PairResponse.java`
- `backend/src/main/java/com/vanter/ember/printing/dto/ReportDiscoveredPrintersRequest.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PairAttemptGuard.java`
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentPairingController.java`
- `backend/src/test/java/com/vanter/ember/printing/service/PrintAgentPairingServiceTest.java`
- `backend/src/test/java/com/vanter/ember/printing/controller/PrintAgentPairingControllerTest.java`

**Modified**
- `backend/src/main/java/com/vanter/ember/printing/model/PrintAgent.java`
- `backend/src/main/java/com/vanter/ember/printing/service/PrintAgentService.java`
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentAdminController.java`
- `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentSelfController.java`
- `backend/src/main/java/com/vanter/ember/printing/dto/PrintAgentResponse.java`
- `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`
- `backend/src/test/java/com/vanter/ember/printing/service/PrintAgentServiceTest.java`

## 4. What Changed?
- **`V10` migration** (idempotent — `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS`):
  new `pairing_codes` table (`code` PK, `print_agent_id` FK `ON DELETE CASCADE`,
  `api_key_plaintext`, `backend_base_url`, `expires_at`, `consumed_at`, `created_at`) + index
  on `print_agent_id`; two columns on `print_agents` — `paired_at timestamp`,
  `discovered_printers jsonb`.
- **`PairingCode` entity** + `PairingCodeRepository.findByCode`. Deliberately not `@TenantId`
  (the redeem call is `permitAll`, no tenant bound — the code *is* the credential), same
  exception class as `PrintAgent`.
- **`DiscoveredPrinter`** record `(name, driverName, portName, inkjetGuess)` — the shared
  agent↔backend shape. `PrintAgent` gains `pairedAt` + `@JdbcTypeCode(SqlTypes.JSON)
  List<DiscoveredPrinter> discoveredPrinters` (same JSON-column pattern as
  `Session.participants` / `RestaurantSettings.payload`).
- **`PrintAgentService`** (new `pairingCodeRepository` as the last `@RequiredArgsConstructor`
  field, `@Value ember.agent.backend-base-url`):
  - `createPairingCode(tenantId, agentId)` — mints a fresh API key, rotates `apiKeyHash`,
    inserts a single-use `PairingCode` (TTL 15 min), returns `{code, expiresAt}`.
  - `redeemPairingCode(rawCode)` — trims/upper-cases, validates exists + `consumedAt == null`
    + not expired + agent `ACTIVE`, stamps `consumedAt` + `pairedAt`, returns
    `{apiKey, backendBaseUrl, agentId, agentName}`. Bad/used/expired → `BadCredentialsException`
    (→ 401 via `GlobalExceptionHandler`).
  - `saveDiscoveredPrinters(agentId, printers)` — overwrites the JSON snapshot, no history.
  - `generatePairingCode()` — 10 chars of Crockford-ish base32 (no I/O/0/1), ~50 bits.
  - `toResponse` now fills `paired` (= `pairedAt != null`) and `discoveredPrinters`
    (null → `List.of()`).
- **`PrintAgentResponse`** widened with `boolean paired` + `List<DiscoveredPrinter>
  discoveredPrinters`. Sole constructor call site is `PrintAgentService.toResponse`.
- **`PairAttemptGuard`** — in-memory per-IP throttle for `POST /printing/agents/pair`
  (10 failures / 15 min → `429`), mirrors `identity.PinAttemptGuard`, injects the existing
  `Clock` bean (`IdentityClockConfig`).
- **Endpoints:**
  - `POST /printing/admin/agents/{id}/pairing-code` (ADMIN) → `PairingCodeResponse`, on
    `PrintAgentAdminController`.
  - `POST /printing/agents/pair` (`permitAll`) → `PairResponse`, new
    `PrintAgentPairingController`; IP-throttled via `PairAttemptGuard`, IP taken from
    `X-Forwarded-For` first hop else `getRemoteAddr()`.
  - `POST /printing/agents/me/discovered-printers` (agent JWT in `Authorization`, already
    covered by the `/printing/agents/me/**` `permitAll` matcher) → `204`, on
    `PrintAgentSelfController` (now also injects `PrintAgentService`).
- **`SecurityConfig`** — one line: `POST /printing/agents/pair` → `permitAll`.
- **Tests:** `PrintAgentPairingServiceTest` (5: key rotation + code shape, happy-path redeem
  with trim/upper, already-consumed, expired, discovered-printers persist);
  `PrintAgentPairingControllerTest` (`@WebMvcTest` — 200 + body + `recordSuccess`, 401 +
  `recordFailure` on bad code, 429 when the guard is locked); one `SecurityAuditTest`
  `@CsvSource` row for the admin `pairing-code` route. `PrintAgentServiceTest` gains a
  `@Mock PairingCodeRepository` for the widened constructor.

## 5. Why It Changed?
`PrintAgent.apiKeyHash` is a salted BCrypt hash — unrecoverable — so `/pair` cannot "return
the existing key" without a recoverable store. The pairing code *is* that store: single-use,
15-min TTL, cascade-deleted when the agent is revoked. From the operator's view the key is
minted once ("Generar código") and the agent then persists it locally forever; minting a
**new** code is the explicit, rare rotation. `discovered_printers` as one overwritten JSON
column (no history) is all the admin UI needs to populate a printer-name dropdown and
pre-pick `DRIVER` mode for inkjets (spec §6 decisions 3 & 5).

## 6. Verification
- `cd backend && ./mvnw test` — **1208/1208**, 0 failures (1199 baseline + 9 new).
- `V10` applied by hand to the local dev DB (baselined at v15 → Flyway skips it there; prod
  Flyway is not baselined and runs it on the next tagged release — PROGRESS.md ⚠). Backend
  booted clean under `ddl-auto: validate` against the migrated schema.
- **`frontend/src/lib/backend-types.ts` NOT regenerated in this commit.** `pnpm run openapi`
  against the running backend produced a ~1230-line diff — the file had drifted many features
  behind HEAD (last regen `b8fd7b04`). Rolling all that unrelated schema drift into a
  backend-pairing commit violates CLAUDE.md §4 scoped-staging; the regen is deferred to **T5**
  (the frontend task that actually consumes `PairingCodeResponse` / `paired` /
  `discoveredPrinters`), which boots the backend anyway.

## 7. Follow-ups for later tasks
- **T5:** run `pnpm run openapi` and commit the (large, drift-laden) `backend-types.ts` there.
- Local dev DB now carries `pairing_codes` + the two `print_agents` columns out-of-band; no
  Flyway row was inserted (matches the existing v15-baseline convention).
