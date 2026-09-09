# Ember Print Agent — Desktop App + Windows Installer Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: this repo runs the **CLAUDE.md §7 1-Task-1-Context lifecycle** — one task per session, PLAN → APPROVE → EXECUTE → REPORT → COMMIT → `/clear`. Do **not** batch tasks. Steps use checkbox (`- [ ]`) syntax for tracking. `superpowers:executing-plans` / `superpowers:subagent-driven-development` are NOT used here.

**Goal:** Turn the print agent (`printing-agent/`, `com.vanter.emberagent`) from a bare `java -jar` + hand-written `agent.properties` into a one-`.exe` Windows install: pair by short code, credential encrypted on disk (DPAPI), a Swing dashboard + tray, printer names picked from a dropdown, auto-start on log-on — packaged exactly like the Ember Hub.

**Architecture:** The headless core (`AgentConnection`, `PrintJobDispatcher`, `*Sender`, `AuthClient`, `PrinterConfigClient`) is reused with **no logic change**; a new observable `StatusHub` sits beside it and a Swing `AgentDashboard`/`AgentTrayIcon` consume it. The backend `printing` module gains a code-based pairing handshake (`pairing_codes` table, `V10`) and a printer-discovery sink (`discovered_printers` JSON column on `print_agents`). The agent stores its API key with Windows DPAPI (`Crypt32` via JNA, machine scope) under `%ProgramData%\EmberAgent\`, so it never re-pairs. Packaging clones `ember-hub/`: jlink → jpackage `app-image` → Inno Setup `.exe`, auto-start via a `{commonstartup}` shortcut (one process, no service, no IPC).

**Tech Stack:** Java 17, Spring Boot 3.5.14 / JPA (Postgres) + Flyway `V10` for the backend half; plain Swing + `net.java.dev.jna:jna-platform` (`Crypt32`) for the agent UI + credential store; Spring `spring-websocket`/`spring-messaging` + Tyrus (unchanged) for the STOMP core; React 19 / TypeScript / react-hook-form + zod / TanStack Query / shadcn `Select` for the admin UI; `openapi-typescript` regeneration for the new DTO shapes; PowerShell + jlink + jpackage + Inno Setup 6 for the installer.

**Spec:** `docs/superpowers/specs/2026-09-08-print-agent-installer-design.md` — this plan implements it in full for the **cloud agent** only. Spec §4.1 (Hub detects local printers in-process, no agent) is a **separate future plan** `docs/superpowers/plans/2026-09-08-hub-local-printer-detection.md`, out of scope here; T3 deliberately builds `WindowsPrinterEnumerator` as a standalone class so that plan can reuse it.

## Global Constraints

- **Windows-only v1.** DPAPI is Windows-only; macOS/Linux keep the `agent.properties` fallback with a logged warning. jpackage/Inno run on Windows only.
- **`printing-agent/` has no Maven wrapper** and is **not** a backend module (its own `pom.xml`, `com.vanter:printing-agent:0.1.0-SNAPSHOT`). Agent verification command is `mvn -f printing-agent/pom.xml test` (plain `mvn`, the one place in this repo where that is unavoidable). Backend tasks still use `cd backend && ./mvnw test`; frontend tasks `cd frontend && pnpm run build`. Never `mvn` for backend, never bare `tsc -b` for frontend (CLAUDE.md §2/§5).
- **One module.** Swing + JNA + headless core all live in `printing-agent/` under one pom. Do **not** create `printing-agent-app/`.
- **`POST /printing/agents/pair` does NOT rotate the key on the agent's side.** The key is generated once, when the admin creates a pairing code; redeeming a code returns that same plaintext. Generating a *new* code is an explicit, rare admin action (spec §6 decision 3). The agent persists the credential to `%ProgramData%` and never re-pairs unless data is wiped or the PC changes.
- **`discovered_printers` = one JSON column on `print_agents`**, overwritten on each report, no history (spec §6 decision 5). `@JdbcTypeCode(SqlTypes.JSON)` + `@Column`, same pattern as `Session.participants` / `RestaurantSettings.payload`.
- **Agent versions itself** via `printing-agent/pom.xml` `<version>` (`0.1.0-SNAPSHOT` today), independent of `backend/pom.xml` (spec §6 decision 6). The installer strips `-SNAPSHOT`.
- **Auto-start = a shortcut in `{commonstartup}`** launching `Ember Agent.exe --tray` (Hub pattern, `EmberHub.iss` line 46). Single process: dashboard + tray + core in one JVM. No Windows service, no control socket.
- **Migration number is `V10`** (`V9` is the latest applied migration). `V10` must be pure `CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` (idempotent) — the local dev DB is baselined at v15 so migrations ≤15 are skipped there; prod Flyway is not baselined and runs it on the next tagged release (PROGRESS.md ⚠ note).
- **Real route prefixes** (the spec's `POST /print-agents/...` is shorthand): admin endpoints hang off `PrintAgentAdminController` `@RequestMapping("/printing/admin/agents")`; agent-facing off `/printing/agents/...` (`permitAll` in `SecurityConfig`). The `SecurityAuditTest` `@CsvSource` rows use the file's `/api/...`-prefixed style.
- **Every new user-facing string** goes through `t('key')` and is added to **both** `es` and `en` locale files in the same step (`en/*.ts` is `satisfies typeof es*`, a missing key fails `tsc -b`).
- **Closes F-24** (print-agent key plaintext on disk) — track it off the PROGRESS.md "Security debt" line when T2 lands.
- **Commit/PR attribution:** zero Claude attribution in commit messages (no `Co-Authored-By`, no `Claude-Session`, no badge). User is sole author (PROGRESS.md).
- **Surgical edits.** The headless core classes (`AgentConnection`, `PrintJobDispatcher`, `PrintJobHandler`, `*Sender`, `AuthClient`, `PrinterConfigClient`) get **no logic change** — only `Main` is refactored, and only to extract a reusable runner + feed `StatusHub`.

---

## Task 1: Backend — code-based pairing + printer discovery sink + `V10`

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__print_agent_pairing_and_discovery.sql`
- Create: `backend/src/main/java/com/vanter/ember/printing/model/PairingCode.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/model/DiscoveredPrinter.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/repository/PairingCodeRepository.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/dto/PairingCodeResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/dto/PairRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/dto/PairResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/dto/ReportDiscoveredPrintersRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/service/PairAttemptGuard.java`
- Create: `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentPairingController.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/model/PrintAgent.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/service/PrintAgentService.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentAdminController.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentSelfController.java`
- Modify: `backend/src/main/java/com/vanter/ember/printing/dto/PrintAgentResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/OpenApiConfig.java` *(only if it maintains an explicit `permitAll` doc list — check; skip otherwise)*
- Test: `backend/src/test/java/com/vanter/ember/printing/service/PrintAgentPairingServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/printing/controller/PrintAgentPairingControllerTest.java`
- Modify: `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java`

**Interfaces:**
- Consumes: existing `PrintAgent{id,tenantId,name,apiKeyHash,status,lastSeenAt,createdAt}`, `PrintAgentRepository.findByTenantId`, `PrintAgentService.getOwned` (private — new methods live in the same class), `PasswordEncoder`, `TenantContextHolder.requireTenantId()`, `JwtService.extractSubject`.
- Produces:
  - `PairingCode{code, printAgentId, apiKeyPlaintext, backendBaseUrl, expiresAt, consumedAt, createdAt}` entity + `pairing_codes` table.
  - `DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess)` record (shared shape agent ↔ backend).
  - `PrintAgent` gains `pairedAt: LocalDateTime` and `discoveredPrinters: List<DiscoveredPrinter>` (JSON column).
  - `PrintAgentService.createPairingCode(UUID tenantId, UUID agentId): PairingCodeResponse` — generates a fresh API key, sets `agent.apiKeyHash`, inserts a single-use `PairingCode` (TTL 15 min), returns `{code, expiresAt}`.
  - `PrintAgentService.redeemPairingCode(String code): PairResponse` — validates (exists, `consumedAt == null`, `expiresAt` in the future), stamps `consumedAt` + `agent.pairedAt`, returns `{apiKey, backendBaseUrl, agentId, agentName}`.
  - `PrintAgentService.saveDiscoveredPrinters(UUID agentId, List<DiscoveredPrinter>): void`.
  - `PrintAgentResponse(UUID id, String name, String status, LocalDateTime lastSeenAt, boolean connected, boolean paired, List<DiscoveredPrinter> discoveredPrinters)`.
  - Routes: `POST /printing/admin/agents/{id}/pairing-code` (ADMIN) → `PairingCodeResponse`; `POST /printing/agents/pair` (`permitAll`, IP rate-limited) → `PairResponse`; `POST /printing/agents/me/discovered-printers` (`permitAll`, agent JWT in header) → 204.

- [ ] **Step 1: Write the migration**

Create `backend/src/main/resources/db/migration/V10__print_agent_pairing_and_discovery.sql`:

```sql
-- Print-agent installer (2026-09-08 design spec): short-code pairing + printer discovery.
-- Idempotent (IF NOT EXISTS / ADD COLUMN IF NOT EXISTS) — prod Flyway is not baselined and
-- runs this on the next tagged backend release; local dev DB is baselined at v15 and skips it.

CREATE TABLE IF NOT EXISTS pairing_codes (
    code               varchar(12)  PRIMARY KEY,
    print_agent_id     uuid         NOT NULL REFERENCES print_agents (id) ON DELETE CASCADE,
    api_key_plaintext  varchar(255) NOT NULL,
    backend_base_url   varchar(255) NOT NULL,
    expires_at         timestamp(6) NOT NULL,
    consumed_at        timestamp(6),
    created_at         timestamp(6) NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_pairing_codes_agent ON pairing_codes (print_agent_id);

ALTER TABLE print_agents ADD COLUMN IF NOT EXISTS paired_at          timestamp(6);
ALTER TABLE print_agents ADD COLUMN IF NOT EXISTS discovered_printers jsonb;
```

> **Design note — why the plaintext key lives on the code row.** `PrintAgent.apiKeyHash` is a BCrypt hash; a salted hash cannot be reversed, so `/pair` cannot "return the existing key" without storing it somewhere recoverable. The pairing code is that store: it is single-use, expires in 15 minutes, and is deleted-by-cascade when the agent is revoked. From the operator's point of view the key is *not* rotated on every pairing — it is minted once when the admin clicks "Generar código", and the agent then persists it locally forever (spec §1, §6 decision 3). Minting a **new** code is the explicit, rare admin action that rotates it.

- [ ] **Step 2: Write `DiscoveredPrinter`**

Create `backend/src/main/java/com/vanter/ember/printing/model/DiscoveredPrinter.java`:

```java
package com.vanter.ember.printing.model;

/**
 * One Windows print queue the agent enumerated on its PC (spec §2.3). Reported wholesale on
 * every connect/refetch and stored as a JSON array on {@link PrintAgent#getDiscoveredPrinters()}
 * — no history. {@code inkjetGuess} is the agent's heuristic that this queue is a driver-only
 * inkjet (EcoTank etc.) so the admin UI can pre-pick {@code DRIVER} render mode.
 */
public record DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess) {}
```

- [ ] **Step 3: Add the two columns to `PrintAgent`**

Edit `backend/src/main/java/com/vanter/ember/printing/model/PrintAgent.java` — add imports:

```java
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
```

Add fields after `private LocalDateTime createdAt;`:

```java
    @Column(name = "paired_at")
    private LocalDateTime pairedAt;

    // SqlTypes.JSON resolves to the dialect's JSON type on both PostgreSQL and H2 — same
    // pattern as Session.participants / RestaurantSettings.payload. Null until the agent's
    // first discovered-printers report.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "discovered_printers")
    private List<DiscoveredPrinter> discoveredPrinters;
```

- [ ] **Step 4: Write `PairingCode` + repository**

Create `backend/src/main/java/com/vanter/ember/printing/model/PairingCode.java`:

```java
package com.vanter.ember.printing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A one-time, short-lived ({@code expiresAt}, ~15 min) code the admin hands to whoever installs
 * the agent. Redeeming it at {@code POST /printing/agents/pair} returns the agent's API key
 * (stored here in the clear precisely so it can be handed back once — see V10 design note) plus
 * the backend base URL. {@code consumedAt != null} means spent.
 *
 * <p>Deliberately NOT {@code @TenantId}: the redeem call is {@code permitAll} with no tenant
 * bound (the code IS the credential), same class of exception as {@link PrintAgent}.
 */
@Entity
@Table(name = "pairing_codes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PairingCode {

    @Id
    @Column(length = 12)
    private String code;

    @Column(name = "print_agent_id", nullable = false)
    private UUID printAgentId;

    @Column(name = "api_key_plaintext", nullable = false)
    private String apiKeyPlaintext;

    @Column(name = "backend_base_url", nullable = false)
    private String backendBaseUrl;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
```

Create `backend/src/main/java/com/vanter/ember/printing/repository/PairingCodeRepository.java`:

```java
package com.vanter.ember.printing.repository;

import com.vanter.ember.printing.model.PairingCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PairingCodeRepository extends JpaRepository<PairingCode, String> {

    Optional<PairingCode> findByCode(String code);
}
```

- [ ] **Step 5: Write the DTOs**

Create the four DTO files:

```java
// backend/src/main/java/com/vanter/ember/printing/dto/PairingCodeResponse.java
package com.vanter.ember.printing.dto;

import java.time.LocalDateTime;

public record PairingCodeResponse(String code, LocalDateTime expiresAt) {}
```

```java
// backend/src/main/java/com/vanter/ember/printing/dto/PairRequest.java
package com.vanter.ember.printing.dto;

import jakarta.validation.constraints.NotBlank;

public record PairRequest(@NotBlank String code) {}
```

```java
// backend/src/main/java/com/vanter/ember/printing/dto/PairResponse.java
package com.vanter.ember.printing.dto;

import java.util.UUID;

public record PairResponse(String apiKey, String backendBaseUrl, UUID agentId, String agentName) {}
```

```java
// backend/src/main/java/com/vanter/ember/printing/dto/ReportDiscoveredPrintersRequest.java
package com.vanter.ember.printing.dto;

import com.vanter.ember.printing.model.DiscoveredPrinter;
import java.util.List;

public record ReportDiscoveredPrintersRequest(List<DiscoveredPrinter> printers) {}
```

- [ ] **Step 6: Write `PairAttemptGuard`**

Create `backend/src/main/java/com/vanter/ember/printing/service/PairAttemptGuard.java` — an in-memory per-IP throttle, mirroring `com.vanter.ember.identity.service.PinAttemptGuard`:

```java
package com.vanter.ember.printing.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Best-effort brute-force throttle for {@code POST /printing/agents/pair}, keyed by client IP.
 * In-memory / node-local — acceptable for this single-node monolith, same reasoning as
 * {@link com.vanter.ember.identity.service.PinAttemptGuard}. A pairing code is a 10-char
 * base32 token (~50 bits) with a 15-minute TTL; this just closes the "hammer /pair" hole.
 */
@Component
public class PairAttemptGuard {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(int count, Instant windowStart) {}

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public PairAttemptGuard(Clock clock) {
        this.clock = clock;
    }

    public void assertNotLocked(String ip) {
        Attempt a = attempts.get(ip);
        if (a != null && a.count() >= MAX_FAILURES && !windowExpired(a)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many pairing attempts");
        }
    }

    public void recordFailure(String ip) {
        attempts.compute(ip, (k, a) -> {
            Instant now = clock.instant();
            if (a == null || windowExpired(a)) {
                return new Attempt(1, now);
            }
            return new Attempt(a.count() + 1, a.windowStart());
        });
    }

    public void recordSuccess(String ip) {
        attempts.remove(ip);
    }

    private boolean windowExpired(Attempt a) {
        return a.windowStart().plus(WINDOW).isBefore(clock.instant());
    }
}
```

> If no `Clock` bean exists in the context, add `@Bean Clock clock() { return Clock.systemUTC(); }` where `PinAttemptGuard`'s clock is defined — grep `Clock` in `com.vanter.ember.config` first; `PinAttemptGuard` already injects one, so the bean exists.

- [ ] **Step 7: Extend `PrintAgentService`**

Edit `backend/src/main/java/com/vanter/ember/printing/service/PrintAgentService.java`. Add imports:

```java
import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.dto.PairingCodeResponse;
import com.vanter.ember.printing.model.DiscoveredPrinter;
import com.vanter.ember.printing.model.PairingCode;
import com.vanter.ember.printing.repository.PairingCodeRepository;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
```

Add fields (extend the constructor via `@RequiredArgsConstructor` — just add `final` fields):

```java
    private final PairingCodeRepository pairingCodeRepository;

    private static final Duration PAIRING_CODE_TTL = Duration.ofMinutes(15);

    @Value("${ember.agent.backend-base-url:https://api.ember.vanter.net/v1}")
    private String agentBackendBaseUrl;
```

Add methods:

```java
    @Transactional
    public PairingCodeResponse createPairingCode(UUID tenantId, UUID agentId) {
        PrintAgent agent = getOwned(tenantId, agentId);
        String apiKey = generateApiKey();
        agent.setApiKeyHash(passwordEncoder.encode(apiKey));
        printAgentRepository.save(agent);

        LocalDateTime now = LocalDateTime.now();
        PairingCode code = pairingCodeRepository.save(PairingCode.builder()
                .code(generatePairingCode())
                .printAgentId(agent.getId())
                .apiKeyPlaintext(apiKey)
                .backendBaseUrl(agentBackendBaseUrl)
                .expiresAt(now.plus(PAIRING_CODE_TTL))
                .createdAt(now)
                .build());
        return new PairingCodeResponse(code.getCode(), code.getExpiresAt());
    }

    @Transactional
    public PairResponse redeemPairingCode(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(java.util.Locale.ROOT);
        PairingCode pairing = pairingCodeRepository.findByCode(code)
                .filter(c -> c.getConsumedAt() == null)
                .filter(c -> c.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                        "Invalid, used or expired pairing code"));

        PrintAgent agent = printAgentRepository.findById(pairing.getPrintAgentId())
                .filter(a -> a.getStatus() == PrintAgentStatus.ACTIVE)
                .orElseThrow(() -> new org.springframework.security.authentication.BadCredentialsException(
                        "Agent is not active"));

        LocalDateTime now = LocalDateTime.now();
        pairing.setConsumedAt(now);
        pairingCodeRepository.save(pairing);
        agent.setPairedAt(now);
        printAgentRepository.save(agent);

        return new PairResponse(
                pairing.getApiKeyPlaintext(), pairing.getBackendBaseUrl(), agent.getId(), agent.getName());
    }

    @Transactional
    public void saveDiscoveredPrinters(UUID agentId, List<DiscoveredPrinter> printers) {
        printAgentRepository.findById(agentId).ifPresent(agent -> {
            agent.setDiscoveredPrinters(printers == null ? List.of() : printers);
            printAgentRepository.save(agent);
        });
    }

    private static String generatePairingCode() {
        // 10 chars from Crockford-ish base32 (no I/O/0/1) — readable over the phone, ~50 bits.
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
```

Update `toResponse` (both call sites pass `connected`; add the two new fields):

```java
    private PrintAgentResponse toResponse(PrintAgent agent, boolean connected) {
        return new PrintAgentResponse(
                agent.getId(), agent.getName(), agent.getStatus().name(), agent.getLastSeenAt(),
                connected, agent.getPairedAt() != null,
                agent.getDiscoveredPrinters() == null ? List.of() : agent.getDiscoveredPrinters());
    }
```

Add `import java.util.List;` if not present.

- [ ] **Step 8: Widen `PrintAgentResponse`**

Edit `backend/src/main/java/com/vanter/ember/printing/dto/PrintAgentResponse.java`:

```java
package com.vanter.ember.printing.dto;

import com.vanter.ember.printing.model.DiscoveredPrinter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PrintAgentResponse(
        UUID id,
        String name,
        String status,
        LocalDateTime lastSeenAt,
        boolean connected,
        boolean paired,
        List<DiscoveredPrinter> discoveredPrinters) {}
```

> Grep `new PrintAgentResponse(` across the backend — `PrintAgentService.rename` calls `toResponse(agent, false)` (fine), and any test helpers that build the record inline need the two extra args. Fix them in Step 11.

- [ ] **Step 9: Add the admin `pairing-code` endpoint**

Edit `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentAdminController.java` — add after `regenerateKey`:

```java
    @PostMapping("/{id}/pairing-code")
    public PairingCodeResponse createPairingCode(@PathVariable UUID id) {
        return printAgentService.createPairingCode(TenantContextHolder.requireTenantId(), id);
    }
```

Add `import com.vanter.ember.printing.dto.PairingCodeResponse;`.

- [ ] **Step 10: Add the `/pair` and `/me/discovered-printers` endpoints**

Create `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentPairingController.java`:

```java
package com.vanter.ember.printing.controller;

import com.vanter.ember.printing.dto.PairRequest;
import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.service.PairAttemptGuard;
import com.vanter.ember.printing.service.PrintAgentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent-facing, unauthenticated (permitAll in SecurityConfig) — the pairing code IS the
 * credential, redeemed once for the API key. Same trust boundary as {@code /printing/agents/token}.
 */
@RestController
@RequestMapping("/printing/agents")
@RequiredArgsConstructor
public class PrintAgentPairingController {

    private final PrintAgentService printAgentService;
    private final PairAttemptGuard pairAttemptGuard;

    @PostMapping("/pair")
    public PairResponse pair(@Valid @RequestBody PairRequest request, HttpServletRequest http) {
        String ip = clientIp(http);
        pairAttemptGuard.assertNotLocked(ip);
        try {
            PairResponse response = printAgentService.redeemPairingCode(request.code());
            pairAttemptGuard.recordSuccess(ip);
            return response;
        } catch (RuntimeException e) {
            pairAttemptGuard.recordFailure(ip);
            throw e;
        }
    }

    private static String clientIp(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
```

Edit `backend/src/main/java/com/vanter/ember/printing/controller/PrintAgentSelfController.java` — inject `PrintAgentService` and add:

```java
    @PostMapping("/discovered-printers")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reportDiscoveredPrinters(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ReportDiscoveredPrintersRequest request) {
        String token = authHeader.substring("Bearer ".length());
        UUID agentId = UUID.fromString(jwtService.extractSubject(token));
        printAgentService.saveDiscoveredPrinters(agentId, request.printers());
    }
```

Add imports: `com.vanter.ember.printing.dto.ReportDiscoveredPrintersRequest`, `com.vanter.ember.printing.service.PrintAgentService`, `org.springframework.http.HttpStatus`, `org.springframework.web.bind.annotation.PostMapping`, `org.springframework.web.bind.annotation.RequestBody`, `org.springframework.web.bind.annotation.ResponseStatus`, and add `private final PrintAgentService printAgentService;` to the constructor (it is `@RequiredArgsConstructor`).

- [ ] **Step 11: Wire security + fix compile fallout**

Edit `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java` — add next to the existing print-agent lines (after `/printing/agents/me/**`):

```java
                        .requestMatchers(HttpMethod.POST, "/printing/agents/pair").permitAll()
```

`/printing/agents/me/discovered-printers` is already covered by the existing `/printing/agents/me/**` matcher — no new line needed for it.

Then `cd backend && ./mvnw test-compile` and fix every `new PrintAgentResponse(...)` / `toResponse` arity error the two new record components introduce (test fixtures in `com.vanter.ember.printing`).

- [ ] **Step 12: Write `PrintAgentPairingServiceTest`**

Create `backend/src/test/java/com/vanter/ember/printing/service/PrintAgentPairingServiceTest.java`:

```java
package com.vanter.ember.printing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.vanter.ember.printing.dto.PairResponse;
import com.vanter.ember.printing.dto.PairingCodeResponse;
import com.vanter.ember.printing.model.DiscoveredPrinter;
import com.vanter.ember.printing.model.PairingCode;
import com.vanter.ember.printing.model.PrintAgent;
import com.vanter.ember.printing.model.PrintAgentStatus;
import com.vanter.ember.printing.repository.PairingCodeRepository;
import com.vanter.ember.printing.repository.PrintAgentRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PrintAgentPairingServiceTest {

    @Mock PrintAgentRepository printAgentRepository;
    @Mock PairingCodeRepository pairingCodeRepository;
    @Mock PrintAgentConnectionRegistry connectionRegistry;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private PrintAgentService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID AGENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PrintAgentService(
                printAgentRepository, passwordEncoder, connectionRegistry, pairingCodeRepository);
        ReflectionTestUtils.setField(service, "agentBackendBaseUrl", "https://api.example/v1");
    }

    private PrintAgent activeAgent() {
        return PrintAgent.builder()
                .id(AGENT_ID).tenantId(TENANT).name("Caja 1")
                .apiKeyHash(passwordEncoder.encode("old-key"))
                .status(PrintAgentStatus.ACTIVE).createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createPairingCode_rotatesKeyAndReturnsUnexpiredCode() {
        PrintAgent agent = activeAgent();
        String oldHash = agent.getApiKeyHash();
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));
        when(pairingCodeRepository.save(any(PairingCode.class))).thenAnswer(i -> i.getArgument(0));

        PairingCodeResponse res = service.createPairingCode(TENANT, AGENT_ID);

        assertThat(res.code()).hasSize(10);
        assertThat(res.expiresAt()).isAfter(LocalDateTime.now());
        assertThat(agent.getApiKeyHash()).isNotEqualTo(oldHash);
    }

    @Test
    void redeemPairingCode_validCode_returnsKeyAndStampsConsumedAndPaired() {
        PrintAgent agent = activeAgent();
        PairingCode code = PairingCode.builder()
                .code("ABCDEFGHJK").printAgentId(AGENT_ID).apiKeyPlaintext("the-key")
                .backendBaseUrl("https://api.example/v1")
                .expiresAt(LocalDateTime.now().plusMinutes(10)).createdAt(LocalDateTime.now())
                .build();
        when(pairingCodeRepository.findByCode("ABCDEFGHJK")).thenReturn(Optional.of(code));
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(pairingCodeRepository.save(any(PairingCode.class))).thenAnswer(i -> i.getArgument(0));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));

        PairResponse res = service.redeemPairingCode("  abcdefghjk ");

        assertThat(res.apiKey()).isEqualTo("the-key");
        assertThat(res.backendBaseUrl()).isEqualTo("https://api.example/v1");
        assertThat(res.agentId()).isEqualTo(AGENT_ID);
        assertThat(code.getConsumedAt()).isNotNull();
        assertThat(agent.getPairedAt()).isNotNull();
    }

    @Test
    void redeemPairingCode_alreadyConsumed_throws() {
        PairingCode code = PairingCode.builder()
                .code("USEDCODE00").printAgentId(AGENT_ID).apiKeyPlaintext("k")
                .backendBaseUrl("u").expiresAt(LocalDateTime.now().plusMinutes(10))
                .consumedAt(LocalDateTime.now().minusMinutes(1)).createdAt(LocalDateTime.now())
                .build();
        when(pairingCodeRepository.findByCode("USEDCODE00")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.redeemPairingCode("USEDCODE00"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void redeemPairingCode_expired_throws() {
        PairingCode code = PairingCode.builder()
                .code("OLDCODE000").printAgentId(AGENT_ID).apiKeyPlaintext("k")
                .backendBaseUrl("u").expiresAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusMinutes(20))
                .build();
        when(pairingCodeRepository.findByCode("OLDCODE000")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.redeemPairingCode("OLDCODE000"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void saveDiscoveredPrinters_persistsListOnAgent() {
        PrintAgent agent = activeAgent();
        when(printAgentRepository.findById(AGENT_ID)).thenReturn(Optional.of(agent));
        when(printAgentRepository.save(any(PrintAgent.class))).thenAnswer(i -> i.getArgument(0));

        service.saveDiscoveredPrinters(AGENT_ID, List.of(
                new DiscoveredPrinter("EPSON L3210 Series", "EPSON L3210 Series", "USB001", true)));

        assertThat(agent.getDiscoveredPrinters()).singleElement()
                .satisfies(p -> assertThat(p.inkjetGuess()).isTrue());
    }
}
```

> If `PrintAgentService`'s constructor arg order differs after `@RequiredArgsConstructor` regen, match it — Lombok orders by field declaration. Put the new `pairingCodeRepository` field **last** so existing positional test constructors that don't use it still compile via the explicit `new PrintAgentService(...)` here only.

- [ ] **Step 13: Write `PrintAgentPairingControllerTest`**

Create `backend/src/test/java/com/vanter/ember/printing/controller/PrintAgentPairingControllerTest.java` — a `@WebMvcTest(PrintAgentPairingController.class)` (or a plain MockMvc standalone) covering: `200` + body on a valid code (service mocked), `401` when the service throws `BadCredentialsException`, `429` after `MAX_FAILURES` calls with a bad code from the same `X-Forwarded-For`. Mirror the structure of an existing `printing` controller test (grep `@WebMvcTest` under `com.vanter.ember.printing`).

- [ ] **Step 14: Add `SecurityAuditTest` rows**

Edit `backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java` — add to the `@CsvSource`, near the other `/api/printing` rows:

```
"POST, /api/printing/admin/agents/00000000-0000-0000-0000-000000000000/pairing-code",
```

`POST /printing/agents/pair` and `POST /printing/agents/me/discovered-printers` are intentionally `permitAll` (agent-facing, credential-in-body / signed-JWT-in-header) — add them to whatever "explicitly public" allowlist that test keeps for the `/printing/agents/**` family, matching how `/printing/agents/token` is already listed.

- [ ] **Step 15: Run backend tests**

Run: `cd backend && ./mvnw test`
Expected: all green, including the two new test classes and the updated `SecurityAuditTest`. Flyway applies `V10` on the test datasource boot.

- [ ] **Step 16: Regenerate frontend types**

Run (background, stop once the fetch succeeds): `cd backend && ./mvnw spring-boot:run`
Run: `cd frontend && pnpm run openapi`
Expected: `frontend/src/lib/backend-types.ts` `PrintAgentResponse` now has `paired` + `discoveredPrinters`; new `PairingCodeResponse` schema present.

- [ ] **Step 17: Report + PROGRESS.md + commit**

Report `reports/XX-task-print-agent-t1-backend-pairing-discovery.md` (§4 structure). Update PROGRESS.md (T1 checkbox, health line). Commit:

```bash
git add backend/src/main/resources/db/migration/V10__print_agent_pairing_and_discovery.sql backend/src/main/java/com/vanter/ember/printing backend/src/main/java/com/vanter/ember/config/SecurityConfig.java backend/src/test/java/com/vanter/ember/printing backend/src/test/java/com/vanter/ember/config/SecurityAuditTest.java frontend/src/lib/backend-types.ts PROGRESS.md reports/XX-task-print-agent-t1-backend-pairing-discovery.md
git commit -m "feat(backend): code-based print-agent pairing and printer discovery sink"
```

---

## Task 2: Agent — observable `StatusHub` + DPAPI credential store + config precedence

**Files:**
- Modify: `printing-agent/pom.xml`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/AgentPaths.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/AgentCredential.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/credential/CredentialStore.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/credential/DpapiCredentialStore.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/credential/PlaintextCredentialStore.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/credential/CredentialStores.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/status/StatusHub.java`
- Modify: `printing-agent/src/main/java/com/vanter/emberagent/AgentConfig.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/AgentConfigTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/credential/PlaintextCredentialStoreTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/credential/DpapiCredentialStoreTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/status/StatusHubTest.java`

**Interfaces:**
- Consumes: nothing from T1 at the code level (the agent talks to T1's endpoints only from T3 onward).
- Produces:
  - `AgentPaths` — `AgentPaths.dataDir(): Path` (`%ProgramData%\EmberAgent` on Windows via `System.getenv("ProgramData")`, else `~/.ember-agent`), `credentialFile()`, `stateFile()`, `logsDir()`; creates dirs on first call.
  - `AgentCredential(String apiKey, String backendBaseUrl)` — JSON via Jackson.
  - `CredentialStore` interface: `Optional<AgentCredential> load()`, `void save(AgentCredential c)`, `void clear()`, `boolean isEncrypted()`.
  - `DpapiCredentialStore` (JNA `Crypt32`, `CRYPTPROTECT_LOCAL_MACHINE`) writing `credential.bin`; `PlaintextCredentialStore` writing `credential.json` (+ `WARN` log); `CredentialStores.forThisMachine(): CredentialStore` picks by `System.getProperty("os.name")`.
  - `StatusHub` — `enum Phase { UNPAIRED, CONNECTING, CONNECTED, RETRYING }`; `record Snapshot(Phase phase, String detail, Instant lastSeen, String agentId, int printerCount, List<JobRecord> recentJobs)`; `record JobRecord(Instant at, String role, String queue, String result, String error)`; `setPhase(Phase, String detail)`, `setConnected(String agentId, int printerCount)`, `recordJob(JobRecord)`, `addListener(Consumer<Snapshot>)`, `snapshot()`. Thread-safe (synchronized mutation + `CopyOnWriteArrayList` listeners), keeps the last 20 `JobRecord`s.
  - `AgentConfig.load(Path optionalPropertiesFile): Optional<AgentConfig>` — now returns empty instead of throwing when nothing is configured; tries `CredentialStore.load()` first, then the properties file.

- [ ] **Step 1: Add the JNA dependency**

Edit `printing-agent/pom.xml` — add inside `<dependencies>`:

```xml
        <dependency>
            <groupId>net.java.dev.jna</groupId>
            <artifactId>jna-platform</artifactId>
            <version>5.14.0</version>
        </dependency>
```

(`jna-platform` pulls `jna` transitively and ships `com.sun.jna.platform.win32.Crypt32`/`Crypt32Util`.)

- [ ] **Step 2: Write `AgentPaths`**

```java
package com.vanter.emberagent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Resolves the agent's on-disk locations. On Windows everything lives under
 *  {@code %ProgramData%\EmberAgent} so it survives app updates (spec §2.5 layout). */
public final class AgentPaths {

    private AgentPaths() {}

    public static Path dataDir() {
        String programData = System.getenv("ProgramData");
        Path base = (programData != null && !programData.isBlank())
                ? Path.of(programData, "EmberAgent")
                : Path.of(System.getProperty("user.home"), ".ember-agent");
        return ensure(base);
    }

    public static Path credentialFile() { return dataDir().resolve("credential.bin"); }

    public static Path plaintextCredentialFile() { return dataDir().resolve("credential.json"); }

    public static Path stateFile() { return dataDir().resolve("agent-state.json"); }

    public static Path logsDir() { return ensure(dataDir().resolve("logs")); }

    private static Path ensure(Path p) {
        try {
            Files.createDirectories(p);
            return p;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + p, e);
        }
    }
}
```

- [ ] **Step 3: Write `AgentCredential`**

```java
package com.vanter.emberagent;

/** The two things the agent needs to connect, persisted (encrypted on Windows) between runs. */
public record AgentCredential(String apiKey, String backendBaseUrl) {}
```

- [ ] **Step 4: Write the `CredentialStore` interface + both implementations + the selector**

`CredentialStore.java`:

```java
package com.vanter.emberagent.credential;

import com.vanter.emberagent.AgentCredential;
import java.util.Optional;

public interface CredentialStore {
    Optional<AgentCredential> load();
    void save(AgentCredential credential);
    void clear();
    /** true if the bytes on disk are OS-encrypted (DPAPI), false for the plaintext fallback. */
    boolean isEncrypted();
}
```

`DpapiCredentialStore.java` — wraps the JSON bytes with `Crypt32Util.cryptProtectData(bytes, null, WinCrypt.CRYPTPROTECT_LOCAL_MACHINE)` on save and `cryptUnprotectData` on load, file `AgentPaths.credentialFile()`:

```java
package com.vanter.emberagent.credential;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;
import com.vanter.emberagent.AgentCredential;
import com.vanter.emberagent.AgentPaths;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DpapiCredentialStore implements CredentialStore {

    private static final Logger log = LoggerFactory.getLogger(DpapiCredentialStore.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file = AgentPaths.credentialFile();

    @Override
    public Optional<AgentCredential> load() {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            byte[] protectedBytes = Files.readAllBytes(file);
            byte[] plain = Crypt32Util.cryptUnprotectData(protectedBytes, WinCrypt.CRYPTPROTECT_LOCAL_MACHINE);
            return Optional.of(mapper.readValue(plain, AgentCredential.class));
        } catch (IOException e) {
            throw new UncheckedIOException("credential.bin unreadable", e);
        } catch (RuntimeException e) {
            log.warn("credential.bin present but could not be decrypted on this machine: {}", e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(AgentCredential credential) {
        try {
            byte[] plain = mapper.writeValueAsBytes(credential);
            byte[] protectedBytes = Crypt32Util.cryptProtectData(
                    plain, null, WinCrypt.CRYPTPROTECT_LOCAL_MACHINE, "EmberAgent", null);
            Files.write(file, protectedBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write credential.bin", e);
        }
    }

    @Override
    public void clear() {
        try { Files.deleteIfExists(file); } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    @Override
    public boolean isEncrypted() { return true; }
}
```

> Verify the exact `Crypt32Util.cryptProtectData` overload in jna-platform 5.14.0 at implementation time (some versions expose `cryptProtectData(byte[], byte[] entropy, int flags, String description, WinCrypt.CRYPTPROTECT_PROMPTSTRUCT)`). If the 5-arg form isn't there, use `cryptProtectData(byte[], int flags)` and drop the description.

`PlaintextCredentialStore.java` — `Files.writeString(AgentPaths.plaintextCredentialFile(), json)` + a one-time `log.warn("Storing the API key UNENCRYPTED at {} — DPAPI is Windows-only", file)`; `isEncrypted()` returns `false`.

`CredentialStores.java`:

```java
package com.vanter.emberagent.credential;

public final class CredentialStores {

    private CredentialStores() {}

    public static CredentialStore forThisMachine() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? new DpapiCredentialStore() : new PlaintextCredentialStore();
    }
}
```

- [ ] **Step 5: Write `StatusHub`**

```java
package com.vanter.emberagent.status;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The one observable surface the Swing dashboard/tray consume. The headless core
 * ({@code Main}/{@code PrintJobHandler}) pushes state in; nothing reads back out of it except
 * UI. No dependency on Swing so it stays unit-testable.
 */
public final class StatusHub {

    public enum Phase { UNPAIRED, CONNECTING, CONNECTED, RETRYING }

    public record JobRecord(Instant at, String role, String queue, String result, String error) {}

    public record Snapshot(
            Phase phase, String detail, Instant lastSeen, String agentId,
            int printerCount, List<JobRecord> recentJobs) {}

    private static final int MAX_JOBS = 20;

    private final Object lock = new Object();
    private final Deque<JobRecord> jobs = new ArrayDeque<>();
    private final List<Consumer<Snapshot>> listeners = new CopyOnWriteArrayList<>();

    private Phase phase = Phase.UNPAIRED;
    private String detail = "Sin emparejar";
    private Instant lastSeen;
    private String agentId = "";
    private int printerCount;

    public void addListener(Consumer<Snapshot> listener) {
        listeners.add(listener);
        listener.accept(snapshot());
    }

    public void setPhase(Phase newPhase, String newDetail) {
        synchronized (lock) {
            this.phase = newPhase;
            this.detail = newDetail;
        }
        publish();
    }

    public void setConnected(String agentId, int printerCount) {
        synchronized (lock) {
            this.phase = Phase.CONNECTED;
            this.detail = "Conectado";
            this.agentId = agentId;
            this.printerCount = printerCount;
            this.lastSeen = Instant.now();
        }
        publish();
    }

    public void recordJob(JobRecord record) {
        synchronized (lock) {
            jobs.addFirst(record);
            while (jobs.size() > MAX_JOBS) {
                jobs.removeLast();
            }
            this.lastSeen = Instant.now();
        }
        publish();
    }

    public Snapshot snapshot() {
        synchronized (lock) {
            return new Snapshot(phase, detail, lastSeen, agentId, printerCount, new ArrayList<>(jobs));
        }
    }

    private void publish() {
        Snapshot s = snapshot();
        listeners.forEach(l -> l.accept(s));
    }
}
```

- [ ] **Step 6: Rework `AgentConfig`**

Rewrite `printing-agent/src/main/java/com/vanter/emberagent/AgentConfig.java`:

```java
package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public record AgentConfig(String backendBaseUrl, String apiKey) {

    /**
     * Resolution order (spec §2.2): the encrypted credential store first (the normal path after
     * pairing), then a hand-written {@code agent.properties} (dev / support override), then
     * empty — the dashboard opens the pairing dialog.
     */
    public static Optional<AgentConfig> resolve(CredentialStore store, Path propertiesFile) {
        Optional<AgentConfig> fromStore = store.load()
                .map(c -> new AgentConfig(c.backendBaseUrl(), c.apiKey()));
        if (fromStore.isPresent()) {
            return fromStore;
        }
        if (propertiesFile != null && Files.exists(propertiesFile)) {
            try {
                return Optional.of(loadProperties(propertiesFile));
            } catch (IOException e) {
                throw new IllegalStateException("agent.properties unreadable: " + e.getMessage(), e);
            }
        }
        return Optional.empty();
    }

    private static AgentConfig loadProperties(Path file) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        return new AgentConfig(require(props, "backend.base-url"), require(props, "agent.api-key"));
    }

    private static String require(Properties props, String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value;
    }
}
```

> `Main` currently calls `AgentConfig.load(configPath)` — leave `Main` alone this task; T4 rewires `Main`. To keep `printing-agent` compiling now, **keep the old `load(Path)` method too** as a thin shim: `public static AgentConfig load(Path f) throws IOException { return loadProperties(f); }`. Remove the shim in T4.

- [ ] **Step 7: Tests**

- `AgentConfigTest`: store-hit wins over properties; properties used when store empty; empty Optional when neither exists (uses a fake `CredentialStore` returning `Optional.empty()` and a `@TempDir`).
- `PlaintextCredentialStoreTest`: `save` then `load` round-trips an `AgentCredential` (point `AgentPaths` at a temp dir via a `ProgramData` env override is not possible in-process — instead have `PlaintextCredentialStore` accept an optional `Path` constructor for tests, default to `AgentPaths.plaintextCredentialFile()`).
- `DpapiCredentialStoreTest`: `@EnabledOnOs(OS.WINDOWS)` — `save` then `load` round-trips; on non-Windows the class is never exercised.
- `StatusHubTest`: `addListener` gets an immediate snapshot; `setConnected` flips phase + notifies; `recordJob` past `MAX_JOBS` evicts oldest; listener sees each change.

- [ ] **Step 8: Run agent tests**

Run: `mvn -f printing-agent/pom.xml test`
Expected: green on all OSes (DPAPI test auto-skips off Windows).

- [ ] **Step 9: Report + PROGRESS.md + commit**

```bash
git add printing-agent/pom.xml printing-agent/src/main/java/com/vanter/emberagent printing-agent/src/test/java/com/vanter/emberagent PROGRESS.md reports/XX-task-print-agent-t2-statushub-dpapi.md
git commit -m "feat(print-agent): observable StatusHub and DPAPI-encrypted credential store"
```

---

## Task 3: Agent — `PairingClient` + `WindowsPrinterEnumerator` + discovery reporting

**Files:**
- Create: `printing-agent/src/main/java/com/vanter/emberagent/PairingClient.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/DiscoveredPrinter.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/WindowsPrinterEnumerator.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/DiscoveredPrintersClient.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/PairingClientTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/WindowsPrinterEnumeratorTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/DiscoveredPrintersClientTest.java`
- Create (test fixture): `printing-agent/src/test/resources/get-printer-sample.json`

**Interfaces:**
- Consumes (from T2): `AgentCredential`, `CredentialStore`, `AgentPaths`. Consumes existing test infra `mockwebserver3` (already a test dep).
- Produces:
  - `DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess)` — agent-side twin of the backend record (agent module can't import backend classes).
  - `PairingClient(CredentialStore store, HttpClient http)` — `AgentCredential redeem(String backendBaseUrl, String code)`: `POST {baseUrl}/printing/agents/pair` `{code}` → parse `{apiKey, backendBaseUrl, agentId, agentName}` → `store.save(new AgentCredential(apiKey, backendBaseUrl))` → return the credential. Throws `PairingException` (new small runtime type) on non-200.
  - `WindowsPrinterEnumerator` — `List<DiscoveredPrinter> enumerate()` (runs PowerShell; `[]` on non-Windows or on failure, never throws); `static List<DiscoveredPrinter> parse(String json)` (package-visible, tested against the fixture); `static boolean looksLikeInkjet(String driverName)`.
  - `DiscoveredPrintersClient(HttpClient http)` — `void report(String backendBaseUrl, String jwt, List<DiscoveredPrinter> printers)`: `POST {baseUrl}/printing/agents/me/discovered-printers` `{printers:[...]}` with `Authorization: Bearer {jwt}`; logs and swallows non-204 (discovery is best-effort, never blocks printing).

- [ ] **Step 1: `DiscoveredPrinter` + `PairingException`**

```java
// printing-agent/src/main/java/com/vanter/emberagent/DiscoveredPrinter.java
package com.vanter.emberagent;

public record DiscoveredPrinter(String name, String driverName, String portName, boolean inkjetGuess) {}
```

```java
// printing-agent/src/main/java/com/vanter/emberagent/PairingException.java
package com.vanter.emberagent;

public class PairingException extends RuntimeException {
    public PairingException(String message) { super(message); }
}
```

- [ ] **Step 2: `PairingClient`**

```java
package com.vanter.emberagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.emberagent.credential.CredentialStore;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class PairingClient {

    private final CredentialStore store;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public PairingClient(CredentialStore store) {
        this(store, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    PairingClient(CredentialStore store, HttpClient http) {
        this.store = store;
        this.http = http;
    }

    public AgentCredential redeem(String backendBaseUrl, String code) {
        try {
            String body = mapper.writeValueAsString(new PairBody(code.trim()));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(backendBaseUrl) + "/printing/agents/pair"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 429) {
                throw new PairingException("Demasiados intentos. Espera unos minutos e intenta de nuevo.");
            }
            if (res.statusCode() != 200) {
                throw new PairingException("Código inválido, usado o vencido.");
            }
            PairResult parsed = mapper.readValue(res.body(), PairResult.class);
            AgentCredential credential = new AgentCredential(parsed.apiKey(), parsed.backendBaseUrl());
            store.save(credential);
            return credential;
        } catch (PairingException e) {
            throw e;
        } catch (Exception e) {
            throw new PairingException("No se pudo contactar al servidor: " + e.getMessage());
        }
    }

    private static String trimTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private record PairBody(String code) {}

    private record PairResult(String apiKey, String backendBaseUrl, String agentId, String agentName) {}
}
```

- [ ] **Step 3: `WindowsPrinterEnumerator`**

```java
package com.vanter.emberagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsPrinterEnumerator {

    private static final Logger log = LoggerFactory.getLogger(WindowsPrinterEnumerator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // EcoTank / inkjet / photo lines that have no ESC/POS and only print through their driver.
    private static final Pattern INKJET = Pattern.compile(
            "(?i)(inkjet|ecotank|deskjet|officejet|pixma|stylus|expression|workforce|\\bL\\d{3,4}\\b|\\bET-\\d{3,4}\\b)");

    public List<DiscoveredPrinter> enumerate() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return List.of();
        }
        try {
            Process p = new ProcessBuilder(
                    "powershell", "-NoProfile", "-NonInteractive", "-Command",
                    "Get-Printer | Select-Object Name,DriverName,PortName | ConvertTo-Json -Compress")
                    .redirectErrorStream(true)
                    .start();
            String out = readAll(p.getInputStream());
            p.waitFor();
            return parse(out);
        } catch (Exception e) {
            log.warn("No se pudo enumerar impresoras de Windows: {}", e.getMessage());
            return List.of();
        }
    }

    static List<DiscoveredPrinter> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            List<DiscoveredPrinter> result = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(n -> result.add(toPrinter(n)));
            } else if (root.isObject()) {
                result.add(toPrinter(root)); // ConvertTo-Json emits a bare object for a single printer
            }
            return result;
        } catch (Exception e) {
            log.warn("Salida de Get-Printer no parseable: {}", e.getMessage());
            return List.of();
        }
    }

    private static DiscoveredPrinter toPrinter(JsonNode n) {
        String name = n.path("Name").asText("");
        String driver = n.path("DriverName").asText("");
        String port = n.path("PortName").asText("");
        return new DiscoveredPrinter(name, driver, port, looksLikeInkjet(driver));
    }

    static boolean looksLikeInkjet(String driverName) {
        return driverName != null && INKJET.matcher(driverName).find();
    }

    private static String readAll(InputStream in) throws Exception {
        try (in; ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}
```

- [ ] **Step 4: `DiscoveredPrintersClient`**

`POST {baseUrl}/printing/agents/me/discovered-printers` with `{"printers":[...]}` and the bearer JWT; on a status other than 204 → `log.warn` and return (never throw). Mirror `PrinterConfigClient`'s `HttpClient`/`ObjectMapper` style.

- [ ] **Step 5: Fixture + tests**

`printing-agent/src/test/resources/get-printer-sample.json` — a real 3-printer `ConvertTo-Json` array (one `EPSON L3210 Series`, one `Microsoft Print to PDF`, one generic `POS-80`).

- `WindowsPrinterEnumeratorTest`: `parse(fixture)` → 3 printers, `EPSON L3210` has `inkjetGuess == true`, `POS-80` `false`; `parse` of a bare single object → 1 printer; `parse("")` / `parse("not json")` → empty; `looksLikeInkjet` truth table (`"EPSON L3210 Series"`, `"EPSON ET-2850 Series"`, `"Generic / Text Only"`, `"OKI POS80"`).
- `PairingClientTest` (mockwebserver3): 200 → credential saved to a fake store + returned; 401 → `PairingException`; 429 → `PairingException` with the "Demasiados intentos" text.
- `DiscoveredPrintersClientTest` (mockwebserver3): 204 → no throw, request body has the printers; 500 → no throw (best-effort).

- [ ] **Step 6: Run + report + commit**

Run: `mvn -f printing-agent/pom.xml test` → green.

```bash
git add printing-agent/src/main/java/com/vanter/emberagent printing-agent/src/test PROGRESS.md reports/XX-task-print-agent-t3-pairing-discovery-client.md
git commit -m "feat(print-agent): pairing client and Windows printer enumeration"
```

---

## Task 4: Agent — Swing dashboard + tray + pair dialog, and the `Main` rewire

**Files:**
- Create: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentDashboard.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/ui/AgentTrayIcon.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/ui/PairDialog.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/DiagnosticsReport.java`
- Create: `printing-agent/src/main/java/com/vanter/emberagent/AgentRunner.java`
- Modify: `printing-agent/src/main/java/com/vanter/emberagent/Main.java`
- Modify: `printing-agent/src/main/java/com/vanter/emberagent/PrintJobHandler.java` *(only to accept an optional `StatusHub` and call `recordJob` — no logic change to dispatch)*
- Test: `printing-agent/src/test/java/com/vanter/emberagent/DiagnosticsReportTest.java`
- Test: `printing-agent/src/test/java/com/vanter/emberagent/AgentRunnerTest.java`

**Interfaces:**
- Consumes (T2/T3): `StatusHub`, `CredentialStore`, `CredentialStores.forThisMachine()`, `AgentConfig.resolve`, `AgentPaths`, `PairingClient`, `WindowsPrinterEnumerator`, `DiscoveredPrintersClient`. Consumes the existing core: `AuthClient`, `PrinterConfigClient`, `AgentConnection`, `PrintJobDispatcher`, `AckSender`, `PrintJobHandler`, `WindowsPrintQueueSender`.
- Produces:
  - `AgentRunner(CredentialStore store, StatusHub statusHub)` — `void runForever()`: the current `Main` while-loop, verbatim in behavior, but (a) reads config via `AgentConfig.resolve`, (b) on connect calls `WindowsPrinterEnumerator` + `DiscoveredPrintersClient.report`, (c) pushes phase/job state into `StatusHub`, (d) exits the loop cleanly if `store.load()` is empty (UNPAIRED) and re-checks every few seconds so pairing from the dashboard resumes it. `void requestReconnect()` nudges it.
  - `DiagnosticsReport.build(StatusHub.Snapshot, CredentialStore): String` — the "Copiar diagnóstico" text (agent version, OS, `java.version`, `backendBaseUrl` host only, `agentId`, `isEncrypted`, last 20 job lines).
  - `AgentDashboard` — `static void launch(StatusHub hub, CredentialStore store, Runnable onPaired)`; `AgentTrayIcon` — `static void install(JFrame window, StatusHub hub)`; `PairDialog` — `static Optional<AgentCredential> show(Window owner, CredentialStore store)`.
  - `Main.main` — `--headless` runs `new AgentRunner(...).runForever()` with no UI (service-style / dev); default constructs the `StatusHub`, starts `AgentRunner` on a daemon thread, and opens `AgentDashboard` (with `--tray` → start minimized to tray).

- [ ] **Step 1: `AgentRunner` — extract the loop from `Main`**

Move the body of the current `Main.main` while-loop into `AgentRunner.runForever()`, keeping the reconnect/backoff timing (`5s` alive-poll, `10s` error backoff) **unchanged**. Changes only at the marked points:

```java
package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.status.StatusHub;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.messaging.simp.stomp.StompSession;

public class AgentRunner {

    private final CredentialStore store;
    private final StatusHub status;
    private final AuthClient authClient = new AuthClient();
    private final PrinterConfigClient printerConfigClient = new PrinterConfigClient();
    private final AgentConnection agentConnection = new AgentConnection();
    private final AckSender ackSender = new AckSender();
    private final WindowsPrinterEnumerator enumerator = new WindowsPrinterEnumerator();
    private final DiscoveredPrintersClient discoveredPrintersClient = new DiscoveredPrintersClient();
    private final PrintJobDispatcher dispatcher = new PrintJobDispatcher(
            new NetworkPrinterSender(), new UsbPrinterSender(), new WindowsPrintQueueSender());

    private volatile boolean running = true;

    public AgentRunner(CredentialStore store, StatusHub status) {
        this.store = store;
        this.status = status;
    }

    public void stop() { running = false; }

    public void runForever() {
        while (running) {
            Optional<AgentConfig> maybeConfig =
                    AgentConfig.resolve(store, java.nio.file.Path.of("agent.properties"));
            if (maybeConfig.isEmpty()) {
                status.setPhase(StatusHub.Phase.UNPAIRED, "Sin emparejar");
                sleep(3);
                continue;
            }
            AgentConfig config = maybeConfig.get();
            try {
                status.setPhase(StatusHub.Phase.CONNECTING, "Conectando…");
                String jwt = authClient.fetchToken(config);
                String agentId = decodeAgentIdFromJwt(jwt);

                reportDiscoveredPrinters(config, jwt);

                List<PrinterConfigClient.PrinterConfigDto> myPrinters =
                        printerConfigClient.fetchMyPrinters(config.backendBaseUrl(), jwt);
                PrintJobHandler jobHandler = new PrintJobHandler(
                        printerConfigClient, dispatcher, config.backendBaseUrl(), jwt, status);

                AtomicReference<StompSession> sessionRef = new AtomicReference<>();
                StompSession session = agentConnection.connect(
                        toWsUrl(config.backendBaseUrl()), jwt, agentId,
                        job -> jobHandler.handle(job, (jobId, printerConfigId, result, error) ->
                                ackSender.send(sessionRef.get(), jobId, printerConfigId, result, error)));
                sessionRef.set(session);
                status.setConnected(agentId, myPrinters.size());

                while (running && session.isConnected()) {
                    TimeUnit.SECONDS.sleep(5);
                }
                status.setPhase(StatusHub.Phase.RETRYING, "Sesión desconectada, reintentando…");
            } catch (Exception e) {
                status.setPhase(StatusHub.Phase.RETRYING, "Conexión perdida, reintentando en 10s");
                sleep(10);
            }
        }
    }

    private void reportDiscoveredPrinters(AgentConfig config, String jwt) {
        try {
            discoveredPrintersClient.report(config.backendBaseUrl(), jwt, enumerator.enumerate());
        } catch (RuntimeException e) {
            // best-effort; never blocks the connection
        }
    }

    private static void sleep(int seconds) {
        try { TimeUnit.SECONDS.sleep(seconds); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // decodeAgentIdFromJwt / toWsUrl: moved verbatim from Main
}
```

- [ ] **Step 2: `PrintJobHandler` — thread the `StatusHub` through**

Add a `StatusHub status` constructor param (nullable for the existing tests, or add a 4-arg overload that passes `null`). In `handle(...)`, after the ACK result is known, call `status != null && status.recordJob(new JobRecord(Instant.now(), role, queue, result, error))`. **No change** to which sender is picked or how errors propagate. Update `PrintJobHandlerTest` for the new arg.

- [ ] **Step 3: `Main`**

```java
package com.vanter.emberagent;

import com.vanter.emberagent.credential.CredentialStore;
import com.vanter.emberagent.credential.CredentialStores;
import com.vanter.emberagent.status.StatusHub;
import com.vanter.emberagent.ui.AgentDashboard;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        boolean headless = Arrays.asList(args).contains("--headless");
        boolean startInTray = Arrays.asList(args).contains("--tray");

        CredentialStore store = CredentialStores.forThisMachine();
        StatusHub status = new StatusHub();
        AgentRunner runner = new AgentRunner(store, status);

        Thread worker = new Thread(runner::runForever, "ember-agent-runner");
        worker.setDaemon(!headless);
        worker.start();

        if (headless) {
            try { worker.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return;
        }
        AgentDashboard.launch(status, store, startInTray);
    }
}
```

Delete the `AgentConfig.load(Path)` shim added in T2 Step 6 and its `throws IOException` in the old signature — nothing calls it now (grep to confirm).

- [ ] **Step 4: `AgentDashboard` (Swing)**

`JFrame` titled `"Ember Agent"`, structured like `HubDashboard` (BorderLayout, status labels, button row). Zones per spec §2.1, scoped for v1:

- **Header:** `"Ember Agent"` + version (`Main.class.getPackage().getImplementationVersion()`, fallback `"dev"`) + a colored status dot label driven by `StatusHub.Phase` (green CONNECTED / amber CONNECTING|RETRYING / red UNPAIRED).
- **Conexión:** `backendBaseUrl` host (read-only label), phase detail text, "última vez visto" humanized like `HubDashboard.humanizeSince`.
- **Impresora (read-only + test):** a `JComboBox<String>` of `WindowsPrinterEnumerator.enumerate()` queue names, refreshed by a "Actualizar" button; a `"Imprimir página de prueba"` button that sends a canned ticket (`"*** Ember Agent ***\nPrueba de impresión\n" + timestamp`) straight through `new WindowsPrintQueueSender().print(dto, text)` with a synthetic `PrinterConfigDto` (RAW; if the selected queue's `inkjetGuess` → DRIVER). **No "Guardar"** — printer *registration* stays in the admin (spec §5 "auto-provisión … fuera de alcance"); this zone only proves the queue works locally. Note this deviation from the §2.1 table.
- **Actividad:** a `JTable` (non-editable) bound to `StatusHub`'s `recentJobs` (hora / rol / cola / estado / error), repopulated on every snapshot.
- **Pie:** `"Abrir carpeta de logs"` (`Desktop.getDesktop().open(AgentPaths.logsDir().toFile())`), `"Copiar diagnóstico"` (`Toolkit … getSystemClipboard().setContents(new StringSelection(DiagnosticsReport.build(...)))`).

On `launch`: if `store.load().isEmpty()` open `PairDialog.show(frame, store)` immediately; on a successful pair, `runner` (via `AgentConfig.resolve` on its next 3s poll) picks it up — no direct call needed. `setDefaultCloseOperation(DO_NOTHING_ON_CLOSE)` + window listener minimizes to tray (don't exit). `AgentTrayIcon.install(frame, status)`. If `startInTray`, `frame.setVisible(false)` after building.

- [ ] **Step 5: `AgentTrayIcon`**

Clone `com.vanter.ember.hub.tray.HubTrayIcon` almost verbatim: brand-red drawn 16×16 icon, popup menu `"Mostrar Ember Agent"` (toggles `window.setVisible(true)` + `toFront()`) / `"Salir"` (`System.exit(0)`), double-click shows the window. Tooltip = `"Ember Agent — " + phase` updated via a `StatusHub` listener. `SystemTray.isSupported()` guard with a log warning fallback (agent keeps running windowless).

- [ ] **Step 6: `PairDialog`**

Modal `JDialog` "Emparejar este agente": a `JTextField` for the code (uppercased on the fly, 10 chars), a `JTextField` for the backend URL pre-filled `https://api.ember.vanter.net/v1` (collapsed under an "Opciones avanzadas" toggle), an "Emparejar" button, and a `"Tengo una API key"` link that swaps to a 2-field form (URL + key) writing `store.save(new AgentCredential(key, url))` directly. "Emparejar" calls `new PairingClient(store).redeem(url, code)` on a background thread, shows the `PairingException` message inline on failure, closes on success. Returns `Optional<AgentCredential>`.

- [ ] **Step 7: `DiagnosticsReport` + tests**

- `DiagnosticsReportTest`: given a fabricated `Snapshot` + a fake `CredentialStore` (`isEncrypted()=true`), the string contains `"agentId="`, the backend **host only** (no full URL with a key), `"cifrada=sí"`, and one line per job.
- `AgentRunnerTest`: with a `CredentialStore` that returns `Optional.empty()`, `runForever` on a thread parks in `UNPAIRED` (assert `status.snapshot().phase()` within a short poll) and stops cleanly on `runner.stop()`. Keep it hermetic — no real network (empty store never reaches `authClient`).

Swing classes themselves are covered by T7 manual verification, not unit tests (same call the repo made for `HubDashboard`).

- [ ] **Step 8: Run + report + commit**

Run: `mvn -f printing-agent/pom.xml test` → green.
Run: `mvn -f printing-agent/pom.xml -DskipTests package` → shaded jar builds; smoke `java -jar target/printing-agent-0.1.0-SNAPSHOT.jar --headless` locally (it should print `Sin emparejar` state and idle).

```bash
git add printing-agent/src PROGRESS.md reports/XX-task-print-agent-t4-swing-dashboard.md
git commit -m "feat(print-agent): Swing dashboard, tray icon and code pairing dialog"
```

---

## Task 5: Frontend admin — pairing code, printer `<select>`, `.exe` download link

**Files:**
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/pages/admin/components/settings/printing/CreateAgentModal.tsx`
- Modify: `frontend/src/pages/admin/components/settings/PrintingSettings.tsx`
- Modify: `frontend/src/pages/admin/components/settings/printing/AddPrinterModal.tsx`
- Modify: `frontend/src/locales/es/admin.ts`
- Modify: `frontend/src/locales/en/admin.ts`
- Test: `frontend/src/pages/admin/components/settings/PrintingSettings.test.tsx` *(extend)*

**Interfaces:**
- Consumes (T1, via regenerated `backend-types.ts`): `PrintAgentResponse.paired`, `PrintAgentResponse.discoveredPrinters: { name; driverName; portName; inkjetGuess }[]`, `PairingCodeResponse`.
- Produces:
  - `printingService.createPairingCode(agentId: string): Promise<PairingCodeResponse>` → `POST /printing/admin/agents/${id}/pairing-code`.
  - `CreateAgentModal`: the post-create screen leads with the **pairing code** (large, monospace) + copy button; the raw API key drops under a `<details>` "Instalación avanzada".
  - `PrintingSettings`: per-agent **"Nuevo código"** button (opens a small dialog showing the fresh code) + an **Emparejado / Sin emparejar** badge from `agent.paired`; a **"Descargar Ember Agent (.exe)"** link in the card header pointing at `PUBLIC_AGENT_DOWNLOAD_URL` (a `import.meta.env` var, default `https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe`).
  - `AddPrinterModal`: when `connectionType === 'WINDOWS_QUEUE'` and the agent reported printers, `windowsQueueName` renders as a shadcn `<Select>` of `discoveredPrinters[].name` with a trailing `"Otra…"` item that reveals the free-text `Input`; picking a queue whose `inkjetGuess` is true sets `renderMode` to `DRIVER`.

- [ ] **Step 1: `api.ts`**

Add the type + method:

```ts
export type PairingCodeResponse = components['schemas']['PairingCodeResponse']
```

Inside `printingService`, after `regenerateKey`:

```ts
  createPairingCode: async (id: string): Promise<PairingCodeResponse> => {
    const { data } = await api.post<PairingCodeResponse>(`/printing/admin/agents/${id}/pairing-code`)
    return data
  },
```

- [ ] **Step 2: `CreateAgentModal` — pairing code first**

In the `apiKey` branch, replace the single `<code>{apiKey}</code>` block with: a heading `t('printingPairCodeTitle')`, the pairing code (fetch it right after create: chain `printingService.createPairingCode(created.id)` in `onSuccess`, store `pairCode` in state), rendered large + a copy button, helper text `t('printingPairCodeHint')` ("Abre Ember Agent en la PC de la impresora e ingresa este código."), then:

```tsx
<details className="mt-3">
  <summary className="cursor-pointer text-xs text-zinc-500">{t('printingAdvancedInstallLabel')}</summary>
  <code className="mt-2 block break-all rounded-xl bg-zinc-100 p-3 text-xs">{apiKey}</code>
</details>
```

- [ ] **Step 3: `PrintingSettings` — badge, "Nuevo código", download link**

- Card header: add next to the "Generar agente" button an anchor:

```tsx
<a
  href={import.meta.env.VITE_AGENT_DOWNLOAD_URL ?? 'https://downloads.ember.vanter.net/EmberAgentSetup-latest.exe'}
  className="text-sm text-[#7a1315] underline"
>
  {t('printingDownloadAgentLink')}
</a>
```

- In the per-agent row, next to the `status · connected` line, render:

```tsx
<span className={agent.paired ? 'text-xs text-emerald-600' : 'text-xs text-amber-600'}>
  {agent.paired ? t('printingPairedBadge') : t('printingUnpairedBadge')}
</span>
```

- Add a `NewPairingCodeButton` component (sibling of `RegenerateKeyButton`) that calls `printingService.createPairingCode(agentId)` and shows the returned `code` + `expiresAt` in a `Dialog` with a copy button. Wire it into the agent action row.

- [ ] **Step 4: `AddPrinterModal` — `<select>` from `discoveredPrinters`**

- `PrintingSettings` currently calls `openModal('ADD_PRINTER', agent.id)`. Change it to `openModal('ADD_PRINTER', { agentId: agent.id, discoveredPrinters: agent.discoveredPrinters ?? [] })`.
- In `AddPrinterModal`, read `const { agentId, discoveredPrinters } = (modalPayload ?? {}) as { agentId: string; discoveredPrinters: Array<{ name: string; inkjetGuess: boolean }> }`. Update the `disabled={... || !agentId}` and the `printingService.addPrinter(agentId, …)` call accordingly.
- Replace the `windowsQueueName` `Input` with: if `discoveredPrinters.length > 0`, a `<Select>` whose items are the names plus a final `__other__` → `t('printingQueueOtherOption')`; keep the `Input` shown only when the select value is `__other__` or the list is empty. On select change, if the picked printer's `inkjetGuess`, `form.setValue('renderMode', 'DRIVER')`.
- Keep `t('printingQueueNameHint')` under it.

- [ ] **Step 5: Locale keys (ES + EN, same step)**

Add to `frontend/src/locales/es/admin.ts` and the matching keys to `en/admin.ts`:

| key | es | en |
|---|---|---|
| `printingPairCodeTitle` | `Código de emparejamiento` | `Pairing code` |
| `printingPairCodeHint` | `Abre Ember Agent en la PC de la impresora e ingresa este código.` | `Open Ember Agent on the printer's PC and enter this code.` |
| `printingAdvancedInstallLabel` | `Instalación avanzada (API key)` | `Advanced install (API key)` |
| `printingNewPairCodeButton` | `Nuevo código` | `New code` |
| `printingPairCodeExpiresLabel` | `Vence` | `Expires` |
| `printingPairedBadge` | `Emparejado` | `Paired` |
| `printingUnpairedBadge` | `Sin emparejar` | `Not paired` |
| `printingDownloadAgentLink` | `Descargar Ember Agent (.exe)` | `Download Ember Agent (.exe)` |
| `printingQueueOtherOption` | `Otra…` | `Other…` |
| `printingCopyButton` | `Copiar` | `Copy` |

- [ ] **Step 6: Extend `PrintingSettings.test.tsx`**

Add cases: renders "Sin emparejar" when `paired:false`, "Emparejado" when `true`; the download link is present; "Nuevo código" click calls the mocked `createPairingCode` and shows the code. Mock `printingService.createPairingCode`.

- [ ] **Step 7: Build + report + commit**

Run: `cd frontend && pnpm run build` → PASS. `cd frontend && pnpm run lint` → 0 new errors.

```bash
git add frontend/src/lib/api.ts frontend/src/pages/admin/components/settings/printing frontend/src/pages/admin/components/settings/PrintingSettings.tsx frontend/src/pages/admin/components/settings/PrintingSettings.test.tsx frontend/src/locales/es/admin.ts frontend/src/locales/en/admin.ts PROGRESS.md reports/XX-task-print-agent-t5-frontend-admin.md
git commit -m "feat(frontend): print-agent pairing code, printer dropdown and installer download link"
```

---

## Task 6: Installer — jlink / jpackage / Inno Setup + CI, cloned from `ember-hub/`

**Files:**
- Create: `printing-agent/build-installer.ps1`
- Create: `printing-agent/jlink-modules.txt`
- Create: `printing-agent/build.env.example`
- Create: `printing-agent/installer/EmberAgent.iss`
- Create: `printing-agent/installer/Iniciar Ember Agent.cmd`
- Create: `printing-agent/installer/make-icon.ps1`
- Create: `printing-agent/installer/ember-agent.ico` *(generated by `make-icon.ps1`; commit the binary)*
- Modify: `printing-agent/.gitignore` *(add `dist/`, `build.env`)*
- Modify: `.github/workflows/lint.yml` *(add a `build-print-agent` job)*
- Modify: `printing-agent/README.md` *(replace the manual `java -jar` flow with the installer flow)*

**Interfaces:**
- Consumes: the shaded jar from `mvn -f printing-agent/pom.xml package` (main class `com.vanter.emberagent.Main`, already set by the shade plugin).
- Produces: `printing-agent/dist/EmberAgentSetup-<version>.exe`. Install layout per spec §2.5:
  - `%ProgramFiles%\Ember Agent\` — `Ember Agent.exe`, `runtime\`, `app\printing-agent.jar`, `uninstall.exe` (replaced wholesale on update).
  - `%ProgramData%\EmberAgent\` — `credential.bin`, `agent-state.json`, `logs\` (kept across updates; uninstaller asks before deleting).
  - `{commonstartup}\Ember Agent` shortcut → `Ember Agent.exe --tray`; Start-menu + desktop shortcuts → `Ember Agent.exe` (no `--tray`).

- [ ] **Step 1: `jlink-modules.txt`**

```
# Modules baked into the Ember Agent's embedded JRE (build-installer.ps1 -Stage runtime).
# The shaded agent jar is not modular, so this is a conservative superset:
# java.se covers Swing/AWT/desktop + java.net.http + JDBC; the jdk.* entries add crypto,
# JNA's native access path, charsets and the es/en locale data.
java.se
jdk.crypto.ec
jdk.crypto.cryptoki
jdk.unsupported
jdk.management
jdk.zipfs
jdk.localedata
jdk.charsets
```

- [ ] **Step 2: `build-installer.ps1`**

Clone `ember-hub/build-installer.ps1` and strip everything Hub-specific (no Postgres/MinIO vendor fetch, no `build-frontend.ps1`). Keep the three stages:

- `Get-AgentVersion` — read `printing-agent/pom.xml` `<version>`, strip `-SNAPSHOT`.
- `Build-Runtime` — `jlink --add-modules (jlink-modules.txt) --strip-debug --no-header-files --no-man-pages --compress=2 --include-locales=en,es --output dist/runtime`; assert `dist/runtime/bin/java.exe --version`.
- `Build-AppImage` — `mvn -f ../printing-agent/pom.xml -q -DskipTests package`; pick `target/printing-agent-*.jar` (exclude `original-`); copy to `dist/jpackage-input/printing-agent.jar`; `jpackage --type app-image --name "Ember Agent" --app-version (Get-AgentVersion) --vendor "Vanter" --input dist/jpackage-input --main-jar printing-agent.jar --main-class com.vanter.emberagent.Main --runtime-image dist/runtime --icon installer/ember-agent.ico --java-options "-Dfile.encoding=UTF-8" --dest dist/app-image`; copy `installer/Iniciar Ember Agent.cmd` next to the launcher; assert `dist/app-image/Ember Agent/Ember Agent.exe`.
- `Build-Installer` — locate `ISCC.exe` (same fallback list as the Hub script); `iscc /DAppVersion=$version /DAgentPairUrlHelp=... EmberAgent.iss`; assert `dist/EmberAgentSetup-$version.exe`.

- [ ] **Step 3: `EmberAgent.iss`**

Based on `ember-hub/installer/EmberHub.iss`:

```iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif

[Setup]
AppName=Ember Agent
AppVersion={#AppVersion}
AppPublisher=Vanter
DefaultDirName={autopf}\Ember Agent
DefaultGroupName=Ember Agent
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\Ember Agent.exe
OutputDir=..\dist
OutputBaseFilename=EmberAgentSetup-{#AppVersion}
SetupIconFile=ember-agent.ico
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=Ember Agent.exe,*.cmd

[Files]
Source: "..\dist\app-image\Ember Agent\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Dirs]
Name: "{commonappdata}\EmberAgent"
Name: "{commonappdata}\EmberAgent\logs"

[Icons]
Name: "{group}\Ember Agent";         Filename: "{app}\Ember Agent.exe"; WorkingDir: "{app}"
Name: "{commondesktop}\Ember Agent";  Filename: "{app}\Ember Agent.exe"; WorkingDir: "{app}"
Name: "{commonstartup}\Ember Agent";  Filename: "{app}\Ember Agent.exe"; Parameters: "--tray"; WorkingDir: "{app}"

; No [Run] firewall rule — the agent only makes OUTBOUND WebSocket connections, it never listens.

[Code]
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    if MsgBox('¿Eliminar también la credencial y los registros de Ember Agent en ' +
              ExpandConstant('{commonappdata}\EmberAgent') + '?  Elige "No" para conservarlos ' +
              '(así no habrá que volver a emparejar si reinstalás).',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
      DelTree(ExpandConstant('{commonappdata}\EmberAgent'), True, True, True);
end;
```

- [ ] **Step 4: `Iniciar Ember Agent.cmd` + `make-icon.ps1` + `build.env.example`**

- `Iniciar Ember Agent.cmd`: `@start "" "%~dp0Ember Agent.exe" %*` (parity with the Hub's launcher cmd; lets the Start-menu shortcut pass no args and the startup one pass `--tray`).
- `make-icon.ps1`: clone `ember-hub/installer/make-icon.ps1`, brand-red disc, output `ember-agent.ico`. Run it once, commit the `.ico`.
- `build.env.example`: just a header comment — the agent installer has no secrets or URLs to bake (the backend URL comes from `/pair`); keep the file so the script's "copy build.env.example" convention matches the Hub. Contains only `# (sin variables por ahora)`.

- [ ] **Step 5: CI job**

Add to `.github/workflows/lint.yml` a job `build-print-agent` (runs-on `windows-latest`, `temurin` JDK 17):

```yaml
  build-print-agent:
    runs-on: windows-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - name: Package agent fat jar
        run: mvn -f printing-agent/pom.xml -q -B package
      - name: jlink + jpackage app-image smoke
        run: pwsh printing-agent/build-installer.ps1 -Stage appimage
      - name: Assert launcher exists
        run: if (-not (Test-Path "printing-agent/dist/app-image/Ember Agent/Ember Agent.exe")) { exit 1 }
```

The Inno `.exe` stage stays a manual step (same call the Hub made — `iscc` isn't on the GH runner).

- [ ] **Step 6: README rewrite**

Replace `printing-agent/README.md` §1–§6 with: download `EmberAgentSetup-x.y.z.exe` from **Admin → Configuración → Impresión → Descargar Ember Agent**, run it (no Java needed), open Ember Agent, paste the pairing code from the admin, done — it auto-starts on log-on. Keep the Troubleshooting section; drop the Task Scheduler section (now automatic) and the `agent.properties` section (now advanced-only, one line pointing at `%ProgramData%\EmberAgent`).

- [ ] **Step 7: Verify locally + report + commit**

Run: `pwsh printing-agent/build-installer.ps1 -Stage appimage` on a Windows box with JDK 17 → app-image builds, `Ember Agent.exe` present. If Inno Setup is installed, `-Stage installer` → `.exe` produced (else defer to T7).

```bash
git add printing-agent/build-installer.ps1 printing-agent/jlink-modules.txt printing-agent/build.env.example printing-agent/installer printing-agent/.gitignore printing-agent/README.md .github/workflows/lint.yml PROGRESS.md reports/XX-task-print-agent-t6-installer.md
git commit -m "build(print-agent): jlink/jpackage/Inno Setup installer and CI app-image job"
```

---

## Task 7: Manual verification on a clean Windows PC without Java

**Files:**
- Create: `printing-agent/VERIFY.md`
- Create: `reports/XX-task-print-agent-t7-manual-verification.md`

**Interfaces:** none (verification only). Nothing depends on this task.

- [ ] **Step 1: Write `VERIFY.md` — the checklist**

A numbered checklist, each line a pass/fail box, covering:

1. **Clean install, no Java.** On a Windows 10/11 VM with no JDK/JRE: run `EmberAgentSetup-x.y.z.exe`, accept UAC, finish. `Ember Agent.exe` launches, dashboard shows **"Sin emparejar"**, tray icon present. `where java` still finds nothing (runtime is embedded).
2. **Pair by code.** In the admin, create an agent → copy the pairing code. In Ember Agent, enter it → dashboard flips to **Conectando… → Conectado**, `%ProgramData%\EmberAgent\credential.bin` exists and is not readable as text.
3. **Printer dropdown.** In the admin's "Agregar impresora", `Cola de impresión de Windows` → the `<select>` lists the PC's real queues (matches `Get-Printer`). Pick the inkjet → render mode auto-selects **Driver**.
4. **Test page.** Dashboard → select a queue → "Imprimir página de prueba" → paper comes out.
5. **Real job.** Send a kitchen ticket / bill receipt from the app → prints; the dashboard "Actividad" table shows the job as `OK`.
6. **Auto-start on log-on.** Reboot, log in, do nothing → Ember Agent is running in the tray within ~30 s, reconnects without re-pairing.
7. **Credential survives an update.** Install `x.y.(z+1)` over the top → no re-pair prompt, still connects; `%ProgramFiles%\Ember Agent\` refreshed, `%ProgramData%\EmberAgent\credential.bin` untouched.
8. **Uninstall — keep data.** Uninstall, answer **No** to the "delete data" prompt → `%ProgramData%\EmberAgent\` remains; reinstall → connects with no pairing.
9. **Uninstall — remove data.** Uninstall again, answer **Yes** → `%ProgramData%\EmberAgent\` gone.
10. **No inbound firewall prompt** at any point (agent is outbound-only).

- [ ] **Step 2: Execute the checklist**

Run it on a real/VM clean Windows machine. Record pass/fail + any deviation per line in the report. File bugs as their own follow-up tasks (do **not** fix inline — this task is verification).

- [ ] **Step 3: Report + PROGRESS.md + commit**

Report `reports/XX-task-print-agent-t7-manual-verification.md` with the filled checklist and a go/no-go. Update PROGRESS.md: move EMB-PRINT-AGENT to "Done (collapsed)" if all green, or leave the failing lines as open follow-ups.

```bash
git add printing-agent/VERIFY.md PROGRESS.md reports/XX-task-print-agent-t7-manual-verification.md
git commit -m "docs(print-agent): manual installer verification checklist and results"
```

---

## Self-Review

**Spec coverage:**
- §1 / §2.2 code pairing + persistent key + `%ProgramData%` → T1 (backend), T2 (`AgentConfig`/`CredentialStore`), T3 (`PairingClient`), T4 (`PairDialog`). ✓
- §2.1 Swing dashboard (header/conexión/impresora/actividad/pie) → T4 — with one noted v1 deviation: the "Impresora" zone is read-only + local test print, no "Guardar" (registration stays in admin per §5). ✓
- §2.3 printer discovery (`Get-Printer` → backend → `<select>` + inkjet→DRIVER) → T1 (`discovered_printers` column + endpoint), T3 (`WindowsPrinterEnumerator`), T5 (`AddPrinterModal`). ✓
- §2.4 DPAPI machine scope, `credential.bin`, F-24 → T2. ✓
- §2.5 packaging (jlink/jpackage/Inno, layout, `{commonstartup}`, no firewall rule) → T6. ✓
- §3 per-module change list → T1 (backend), T2–T4 (printing-agent), T5 (frontend), T6 (build). `Socket local de control` explicitly dropped (spec §6 decision 1 — single process). ✓
- §2.2 frontend (`CreateAgentModal` code, `PrintingSettings` "Nuevo código" + badge, download link) → T5. ✓
- §6 decisions: (1) startup shortcut not service → T6; (2) one module → all agent tasks; (3) `/pair` no rotate → T1 design note; (4) download link in admin → T5; (5) JSON column → T1; (6) own version → T6 `Get-AgentVersion`. ✓
- §5 out of scope (Tauri, mac/Linux, auto-provision, code-signing) — not planned. ✓
- §6 "quedan por decidir": Flyway `V10` → resolved (T1 Step 1); `*Sender` shared module → **deferred to the Hub-local-printer plan** (this plan leaves the senders in `printing-agent/`, T4 uses `WindowsPrintQueueSender` in-place); code-signing → still deferred. ✓

**Placeholder scan:** no "TBD"/"handle edge cases"/"similar to Task N". Swing UI bodies in T4 Steps 4–6 are described structurally rather than as full source — deliberate, matching how this repo planned `HubDashboard`; every non-UI helper (`AgentRunner`, `DiagnosticsReport`, `StatusHub`, all clients) has real code.

**Type consistency:** `DiscoveredPrinter(name, driverName, portName, inkjetGuess)` identical in `com.vanter.ember.printing.model` (T1) and `com.vanter.emberagent` (T3); `PairResponse`/`PairResult` fields `{apiKey, backendBaseUrl, agentId, agentName}` match across T1 ↔ T3; `PrintAgentResponse` 7-arg shape defined in T1 Step 8 and consumed in T5; `StatusHub.Phase` / `Snapshot` / `JobRecord` defined in T2 Step 5 and consumed in T4.
