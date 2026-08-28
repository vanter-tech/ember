# Ember Hub License Heartbeat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the Ember Hub a periodic license heartbeat so its 4-day offline grace window actually resets on contact, and so a restaurant suspended from `/console` stops the Hub after a 48-hour courtesy grace.

**Architecture:** A `@Profile("hub")` `@Scheduled` bean on the Hub POSTs `{licenseKey, hardwareFingerprint}` to a new `permitAll` cloud endpoint every 5 minutes. The cloud verifies the license signature, matches the hardware fingerprint against the `HubActivation` row from activation, reads `Restaurant.status`, and answers `OK` or `SUSPENDED`. The Hub records the outcome into `hub-state.json` (`lastHeartbeatAt` on OK, a new `suspendedSince` on SUSPENDED) and the existing `GracePeriodInterceptor` blocks order/payment writes when either grace has lapsed.

**Tech Stack:** Java 17, Spring Boot 3.5.14, `java.net.http.HttpClient` (Hub outbound), Spring MVC (cloud endpoint), JUnit 5 + Mockito + AssertJ, `com.sun.net.httpserver.HttpServer` (JDK-bundled, for scheduler tests). No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-28-hub-license-heartbeat-design.md`

## Global Constraints

- **Branch:** all work on `feat/hub-license-heartbeat`, branched from `main`. One PR at the end.
- **Per-task deliverables (CLAUDE.md §4, §7):** each Task below is one CLAUDE.md "task" — it ends with exactly ONE squashed atomic commit and one sequential report in `reports/NNN-<task>.md` (next free number is 265). The TDD steps inside a task are not committed individually; stage only the files that task touched plus its report and `PROGRESS.md`.
- **No Kafka / no new event bus** — not relevant here, but do not introduce one.
- **Verification commands (CLAUDE.md §2):** single test class `cd backend && ./mvnw test -Dtest=ClassName`; full suite `cd backend && ./mvnw test`. Do not substitute `mvn` or bare `tsc`.
- **`@Lazy` on `LicenseIssuingService` injection points MUST be on a hand-written constructor parameter**, never a Lombok `@RequiredArgsConstructor` field — Lombok does not copy a field-level `@Lazy` onto its generated constructor, and the bean is `@Lazy @Service` (not profile-gated), so an eager injection throws on the unset `HUB_LICENSE_PRIVATE_KEY` placeholder in every non-`hub` context (reports 241, 243).
- **Cloud-only controllers carry `@Profile("!hub")`** (a Hub runs the same jar — see `HubActivationController`).
- **Spanish user-facing copy**, matching the existing `GracePeriodInterceptor` / `LicenseService` messages.
- **`InvalidLicenseException` is already mapped to HTTP 400** by `GlobalExceptionHandler` — do not add a new mapping.
- Commit messages: Conventional Commits, lowercase, no `Co-authored-by` / `Signed-off-by` / AI signatures.

---

## File Structure

**New — cloud side (`backend/src/main/java/com/vanter/ember/licensing/`):**
- `model/dto/HubHeartbeatRequest.java` — inbound `{licenseKey, hardwareFingerprint}`, bean-validated. Mirrors `HubActivationRequest`.
- `model/dto/HubHeartbeatResponse.java` — outbound `{status, serverTime, latestVersion}`.
- `service/HubHeartbeatService.java` — verify license + fingerprint + read `Restaurant.status`; return the response. One responsibility: answer "is this Hub allowed to keep running".
- `controller/HubHeartbeatController.java` — `POST /hub-heartbeat`, `permitAll`, `@Profile("!hub")`.

**New — Hub side (`backend/src/main/java/com/vanter/ember/hub/sync/`):**
- `HeartbeatScheduler.java` — `@Profile("hub")` `@Scheduled` bean: call the endpoint, fold the result into `HubState`. One responsibility: drive the periodic call and persist its outcome.

**Modified:**
- `hub/license/HubState.java` — add `suspendedSince` component + convenience constructor + `withHeartbeatNow()` / `withSuspendedSince(Instant)` helpers.
- `hub/license/LicenseService.java` — add `suspendedGrace` constructor arg, `isSuspendedGraceExpired(HubState)`, `recordSuspended(HubState)`; change `recordHeartbeatSuccess` to use `HubState.withHeartbeatNow()`.
- `hub/config/HubProperties.java` — add `heartbeatUrl`, `suspendedGraceHours` components + a convenience constructor keeping the old 11-arg signature.
- `hub/config/HubBeansConfig.java` — `@EnableScheduling`; pass `suspendedGrace` into the `LicenseService` bean.
- `hub/license/GracePeriodInterceptor.java` — block on `isSuspendedGraceExpired` too; distinct message per reason.
- `hub/dashboard/HubDashboard.java` — one read-only license status line.
- `config/SecurityConfig.java` — `.requestMatchers("/hub-heartbeat").permitAll()`.
- `config/RateLimitProperties.java` — add `/hub-heartbeat` to the default `paths` list.
- `src/main/resources/application-hub.yml` — `ember.hub.heartbeat-interval-ms` property.
- `.env.example` — document `EMBER_HUB_HEARTBEAT_URL`, `EMBER_HUB_HEARTBEAT_INTERVAL_MS`, `EMBER_HUB_SUSPENDED_GRACE_HOURS`, `hub.latest-version`.

**Test files (new):** `HubHeartbeatServiceTest`, `HubHeartbeatControllerTest`, `HeartbeatSchedulerTest`.
**Test files (extended):** `HubStateStoreTest`, `LicenseServiceTest`, `GracePeriodInterceptorTest`.

---

## Task 1: `HubState.suspendedSince` + state-store round-trip

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/HubState.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java:74-77` (`recordHeartbeatSuccess`)
- Test: `backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java`

**Interfaces:**
- Produces:
  - `HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt, Instant suspendedSince)` — canonical record constructor (4 args).
  - `HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt)` — convenience constructor, delegates with `suspendedSince = null`. Keeps every existing call site compiling.
  - `HubState.withHeartbeatNow()` → `HubState` — same fingerprint/restaurantId, `lastHeartbeatAt = Instant.now()`, `suspendedSince = null`.
  - `HubState.withSuspendedSince(Instant when)` → `HubState` — same fingerprint/restaurantId/lastHeartbeatAt, `suspendedSince = when`.
- Consumes: nothing new.

- [ ] **Step 1: Write the failing test**

Add to `HubStateStoreTest.java`:

```java
@Test
void save_thenLoad_roundTripsSuspendedSince() {
    Instant suspended = Instant.parse("2026-08-28T10:15:30Z");
    HubState state = new HubState("fp-x", UUID.randomUUID(), Instant.now(), suspended);

    store.save(state);

    HubState loaded = store.load().orElseThrow();
    assertThat(loaded.suspendedSince()).isEqualTo(suspended);
}

@Test
void load_legacyStateFileWithoutSuspendedSince_readsNull() throws Exception {
    UUID restaurantId = UUID.randomUUID();
    String legacyJson = "{\"hardwareFingerprint\":\"fp-legacy\",\"restaurantId\":\""
            + restaurantId + "\",\"lastHeartbeatAt\":\"2026-08-01T00:00:00Z\"}";
    java.nio.file.Files.writeString(stateFile, legacyJson);

    HubState loaded = store.load().orElseThrow();

    assertThat(loaded.hardwareFingerprint()).isEqualTo("fp-legacy");
    assertThat(loaded.suspendedSince()).isNull();
}
```

If `HubStateStoreTest` does not already expose `store` / `stateFile` fields with those names, match its existing setup (it uses a `@TempDir` and constructs `new HubStateStore(stateFile)`); adapt the two tests to the field names already there.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubStateStoreTest`
Expected: FAIL — `HubState` constructor does not take 4 args / `suspendedSince()` not found.

- [ ] **Step 3: Add the field and helpers to `HubState`**

```java
package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

/** Local, Postgres-independent Hub state — has to be readable even when Postgres won't start. */
public record HubState(
        String hardwareFingerprint,
        UUID restaurantId,
        Instant lastHeartbeatAt,
        Instant suspendedSince) {

    /** Back-compat constructor for the common "no suspension" case. */
    public HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {
        this(hardwareFingerprint, restaurantId, lastHeartbeatAt, null);
    }

    public HubState withHeartbeatNow() {
        return new HubState(hardwareFingerprint, restaurantId, Instant.now(), null);
    }

    public HubState withSuspendedSince(Instant when) {
        return new HubState(hardwareFingerprint, restaurantId, lastHeartbeatAt, when);
    }
}
```

- [ ] **Step 4: Point `recordHeartbeatSuccess` at the helper**

In `LicenseService.java`, replace the body of `recordHeartbeatSuccess`:

```java
public HubState recordHeartbeatSuccess(HubState state) {
    HubState updated = state.withHeartbeatNow();
    stateStore.save(updated);
    return updated;
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=HubStateStoreTest,LicenseServiceTest,GracePeriodInterceptorTest,HubProvisioningRunnerTest`
Expected: PASS — the 3-arg convenience constructor keeps every existing `new HubState(...)` call compiling.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/HubState.java \
        backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java \
        backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java
git commit -m "feat(hub): add suspendedSince to HubState with back-compat constructor"
```

---

## Task 2: `LicenseService` suspended-grace logic + `HubProperties` fields

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`

**Interfaces:**
- Consumes: `HubState.withSuspendedSince(Instant)` (Task 1).
- Produces:
  - `LicenseService(Path licenseFile, PublicKey publicKey, LicenseKeyParser parser, HardwareFingerprintService fingerprintService, HubStateStore stateStore, Duration suspendedGrace)` — constructor gains a 6th arg `suspendedGrace`.
  - `LicenseService.isSuspendedGraceExpired(HubState state)` → `boolean` — `state.suspendedSince() != null && Duration.between(state.suspendedSince(), Instant.now()).compareTo(suspendedGrace) > 0`.
  - `LicenseService.recordSuspended(HubState state)` → `HubState` — if `state.suspendedSince() == null`, returns `state.withSuspendedSince(Instant.now())` and persists it; otherwise returns `state` unchanged and does NOT re-persist (the counter must not reset every cycle).
  - `HubProperties` canonical constructor gains, appended after `minioPort`: `String heartbeatUrl`, `int suspendedGraceHours`.
  - `HubProperties` convenience constructor with the **old** 11-arg signature (through `minioPort`), delegating with `heartbeatUrl = ""`, `suspendedGraceHours = 48`.
  - `HubProperties.fromEnvironment()` reads `EMBER_HUB_HEARTBEAT_URL` (default `""`) and `EMBER_HUB_SUSPENDED_GRACE_HOURS` (default `"48"`, parsed to int).

- [ ] **Step 1: Write the failing tests — `LicenseServiceTest`**

Add (the test already builds a `LicenseService` with a mocked `HubStateStore`; extend that setup to pass a `Duration` — use `Duration.ofHours(48)` unless a test needs otherwise):

```java
// `newServiceWithGrace(Duration)` below is a placeholder for "construct the LicenseService
// under test using this file's existing mocks (`stateStore` etc.) plus the extra Duration arg" —
// name it to match whatever helper/builder the file already uses.

@Test
void isSuspendedGraceExpired_nullSuspendedSince_isFalse() {
    LicenseService service = newServiceWithGrace(Duration.ofHours(48));
    HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
    assertThat(service.isSuspendedGraceExpired(state)).isFalse();
}

@Test
void isSuspendedGraceExpired_within48h_isFalse() {
    LicenseService service = newServiceWithGrace(Duration.ofHours(48));
    HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(),
            Instant.now().minus(10, java.time.temporal.ChronoUnit.HOURS));
    assertThat(service.isSuspendedGraceExpired(state)).isFalse();
}

@Test
void isSuspendedGraceExpired_past48h_isTrue() {
    LicenseService service = newServiceWithGrace(Duration.ofHours(48));
    HubState state = new HubState("fp", UUID.randomUUID(), Instant.now(),
            Instant.now().minus(49, java.time.temporal.ChronoUnit.HOURS));
    assertThat(service.isSuspendedGraceExpired(state)).isTrue();
}

@Test
void recordSuspended_firstTime_stampsAndPersists() {
    HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
    LicenseService service = newServiceWithGrace(Duration.ofHours(48));

    HubState after = service.recordSuspended(before);

    assertThat(after.suspendedSince()).isNotNull();
    verify(stateStore).save(after);
}

@Test
void recordSuspended_alreadySuspended_isNoOp() {
    Instant original = Instant.now().minus(5, java.time.temporal.ChronoUnit.HOURS);
    HubState before = new HubState("fp", UUID.randomUUID(), Instant.now(), original);
    LicenseService service = newServiceWithGrace(Duration.ofHours(48));

    HubState after = service.recordSuspended(before);

    assertThat(after.suspendedSince()).isEqualTo(original);
    verify(stateStore, never()).save(any());
}
```

Add `import static org.mockito.Mockito.never;` / `verify` / `any` if absent.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw test -Dtest=LicenseServiceTest`
Expected: FAIL — constructor arity, `isSuspendedGraceExpired` / `recordSuspended` undefined.

- [ ] **Step 3: Implement in `LicenseService`**

Add the field + constructor arg (keep the existing 5-arg constructor deleted — update `HubBeansConfig` in Step 5, and `LicenseServiceTest` already updated in Step 1):

```java
private final Duration suspendedGrace;

public LicenseService(
        Path licenseFile,
        PublicKey publicKey,
        LicenseKeyParser parser,
        HardwareFingerprintService fingerprintService,
        HubStateStore stateStore,
        Duration suspendedGrace) {
    this.licenseFile = licenseFile;
    this.publicKey = publicKey;
    this.parser = parser;
    this.fingerprintService = fingerprintService;
    this.stateStore = stateStore;
    this.suspendedGrace = suspendedGrace;
}
```

Add the two methods:

```java
public boolean isSuspendedGraceExpired(HubState state) {
    return state.suspendedSince() != null
            && Duration.between(state.suspendedSince(), Instant.now()).compareTo(suspendedGrace) > 0;
}

public HubState recordSuspended(HubState state) {
    if (state.suspendedSince() != null) {
        return state;
    }
    HubState updated = state.withSuspendedSince(Instant.now());
    stateStore.save(updated);
    return updated;
}
```

- [ ] **Step 4: Write the failing test — `HubPropertiesTest`**

Add:

```java
@Test
void fromEnvironment_heartbeatDefaults() {
    HubProperties properties = HubProperties.fromEnvironment();
    assertThat(properties.heartbeatUrl()).isEmpty();
    assertThat(properties.suspendedGraceHours()).isEqualTo(48);
}
```

- [ ] **Step 5: Implement `HubProperties` + wire the bean**

`HubProperties.java` — append two components to the record header and to `fromEnvironment()`, and add the back-compat constructor:

```java
public record HubProperties(
        Path dataDir,
        Path postgresBinDir,
        Path licenseFile,
        Path publicKeyFile,
        Path stateFile,
        int postgresPort,
        int serverPort,
        String activationUrl,
        Path minioDataDir,
        Path minioBinDir,
        int minioPort,
        String heartbeatUrl,
        int suspendedGraceHours) {

    /** Back-compat: the pre-heartbeat 11-arg shape, still used by tests that don't care about it. */
    public HubProperties(
            Path dataDir, Path postgresBinDir, Path licenseFile, Path publicKeyFile, Path stateFile,
            int postgresPort, int serverPort, String activationUrl,
            Path minioDataDir, Path minioBinDir, int minioPort) {
        this(dataDir, postgresBinDir, licenseFile, publicKeyFile, stateFile, postgresPort, serverPort,
                activationUrl, minioDataDir, minioBinDir, minioPort, "", 48);
    }

    public static HubProperties fromEnvironment() {
        return new HubProperties(
                Path.of(env("EMBER_HUB_DATA_DIR", "./data/postgres")),
                Path.of(env("EMBER_HUB_POSTGRES_BIN_DIR", "./postgres/bin")),
                Path.of(env("EMBER_HUB_LICENSE_FILE", "./license.key")),
                Path.of(env("EMBER_HUB_PUBLIC_KEY_FILE", "./hub-public-key.der")),
                Path.of(env("EMBER_HUB_STATE_FILE", "./hub-state.json")),
                Integer.parseInt(env("EMBER_HUB_POSTGRES_PORT", "5432")),
                Integer.parseInt(env("EMBER_HUB_SERVER_PORT", "8080")),
                env("EMBER_HUB_ACTIVATION_URL", ""),
                Path.of(env("EMBER_HUB_MINIO_DATA_DIR", "./data/minio")),
                Path.of(env("EMBER_HUB_MINIO_BIN_DIR", "./minio/bin")),
                Integer.parseInt(env("EMBER_HUB_MINIO_PORT", "9000")),
                env("EMBER_HUB_HEARTBEAT_URL", ""),
                Integer.parseInt(env("EMBER_HUB_SUSPENDED_GRACE_HOURS", "48")));
    }
    // ... existing private env(...) helper unchanged
}
```

`HubBeansConfig.java` — the `licenseService` bean passes the new arg:

```java
return new LicenseService(
        properties.licenseFile(), publicKey, parser, fingerprintService, stateStore,
        java.time.Duration.ofHours(properties.suspendedGraceHours()));
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=LicenseServiceTest,HubPropertiesTest,HubProvisioningRunnerTest,HubBeansConfig*`
Expected: PASS. `HubProvisioningRunnerTest`'s positional `new HubProperties(...)` calls resolve to the back-compat constructor.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java \
        backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java \
        backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java \
        backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java \
        backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java
git commit -m "feat(hub): add suspended-grace evaluation to LicenseService"
```

---

## Task 3: Cloud `HubHeartbeatService` + DTOs

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatRequest.java`
- Create: `backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatResponse.java`
- Create: `backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java`

**Interfaces:**
- Consumes:
  - `LicenseIssuingService.publicKey()` → `java.security.PublicKey` (existing `@Lazy @Service`).
  - `new LicenseKeyParser().parseAndVerify(String contents, PublicKey key)` → `LicenseKey` / throws `InvalidLicenseException` (existing).
  - `LicenseKey.restaurantId()` → `UUID` (existing).
  - `HubActivationRepository.findByRestaurantId(UUID)` → `Optional<HubActivation>` (existing); `HubActivation.getHardwareFingerprint()` → `String`.
  - `RestaurantRepository.findById(UUID)` → `Optional<Restaurant>` (existing); `Restaurant.getStatus()` → `RestaurantStatus` (`ACTIVE` / `SUSPENDED` / `INACTIVE`).
- Produces:
  - `HubHeartbeatRequest` — `@Data` bean, `@NotBlank String licenseKey`, `@NotBlank String hardwareFingerprint` (identical shape to `HubActivationRequest`).
  - `HubHeartbeatResponse` — `@Data @Builder`, `String status`, `java.time.Instant serverTime`, `String latestVersion` (nullable).
  - `HubHeartbeatService.heartbeat(HubHeartbeatRequest request)` → `HubHeartbeatResponse` / throws `InvalidLicenseException`. `status` is `"OK"` when `Restaurant.status == ACTIVE`, else `"SUSPENDED"`.

- [ ] **Step 1: Write the DTOs**

`HubHeartbeatRequest.java`:

```java
package com.vanter.ember.licensing.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class HubHeartbeatRequest {

    @NotBlank(message = "licenseKey is required")
    private String licenseKey;

    @NotBlank(message = "hardwareFingerprint is required")
    private String hardwareFingerprint;
}
```

`HubHeartbeatResponse.java`:

```java
package com.vanter.ember.licensing.model.dto;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubHeartbeatResponse {
    private String status;
    private Instant serverTime;
    private String latestVersion;
}
```

- [ ] **Step 2: Write the failing test**

`HubHeartbeatServiceTest.java`:

```java
package com.vanter.ember.licensing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HubHeartbeatServiceTest {

    private LicenseIssuingService licenseIssuingService;
    private HubActivationRepository hubActivationRepository;
    private RestaurantRepository restaurantRepository;
    private HubHeartbeatService service;

    private UUID restaurantId;
    private String signedLicense;
    private static final String FP = "fp-1";

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();

        // Real RSA key pair; sign a license with the private half, hand the service a
        // LicenseIssuingService stub whose publicKey() returns the matching public half.
        KeyPair pair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        signedLicense = com.vanter.ember.hub.license.LicenseKeyParser.sign(
                new LicenseKey(restaurantId, Instant.now()), pair.getPrivate());

        licenseIssuingService = mock(LicenseIssuingService.class);
        when(licenseIssuingService.publicKey()).thenReturn(pair.getPublic());
        hubActivationRepository = mock(HubActivationRepository.class);
        restaurantRepository = mock(RestaurantRepository.class);

        service = new HubHeartbeatService(
                licenseIssuingService, hubActivationRepository, restaurantRepository, "1.4.0");

        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.of(
                HubActivation.builder().restaurantId(restaurantId).hardwareFingerprint(FP)
                        .activatedAt(Instant.now()).build()));
    }

    private HubHeartbeatRequest request(String fingerprint) {
        HubHeartbeatRequest r = new HubHeartbeatRequest();
        r.setLicenseKey(signedLicense);
        r.setHardwareFingerprint(fingerprint);
        return r;
    }

    @Test
    void heartbeat_activeRestaurant_returnsOk() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));

        HubHeartbeatResponse response = service.heartbeat(request(FP));

        assertThat(response.getStatus()).isEqualTo("OK");
        assertThat(response.getLatestVersion()).isEqualTo("1.4.0");
        assertThat(response.getServerTime()).isNotNull();
    }

    @Test
    void heartbeat_suspendedRestaurant_returnsSuspended() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.SUSPENDED)));

        assertThat(service.heartbeat(request(FP)).getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    void heartbeat_fingerprintMismatch_throwsInvalidLicense() {
        assertThatThrownBy(() -> service.heartbeat(request("a-different-fp")))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_noActivationRow_throwsInvalidLicense() {
        when(hubActivationRepository.findByRestaurantId(restaurantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.heartbeat(request(FP)))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_unknownRestaurant_throwsInvalidLicense() {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.heartbeat(request(FP)))
                .isInstanceOf(InvalidLicenseException.class);
    }

    @Test
    void heartbeat_garbageLicense_throwsInvalidLicense() {
        HubHeartbeatRequest r = new HubHeartbeatRequest();
        r.setLicenseKey("not-a-real-license");
        r.setHardwareFingerprint(FP);
        assertThatThrownBy(() -> service.heartbeat(r)).isInstanceOf(InvalidLicenseException.class);
    }

    private Restaurant restaurantWithStatus(RestaurantStatus status) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setStatus(status);
        return restaurant;
    }
}
```

If `Restaurant` has no no-arg setter path (it is a JPA `@Entity` with Lombok — check: it uses `@Data`/`@Builder` like the others, so `new Restaurant()` + setters works; if only `@Builder`, use `Restaurant.builder().id(restaurantId).status(status).build()`).

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatServiceTest`
Expected: FAIL — `HubHeartbeatService` does not exist.

- [ ] **Step 4: Implement `HubHeartbeatService`**

```java
package com.vanter.ember.licensing.service;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKey;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.licensing.model.HubActivation;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.repository.HubActivationRepository;
import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Cloud-side answer to a Hub's periodic license heartbeat: verifies the signed license, confirms
 * the calling PC is the one this restaurant activated on, and reports whether the restaurant is
 * still allowed to operate (spec 2026-08-28-hub-license-heartbeat-design.md). Never echoes any
 * restaurant/admin data — only {status, serverTime, latestVersion}.
 */
@Service
public class HubHeartbeatService {

    private final LicenseIssuingService licenseIssuingService;
    private final HubActivationRepository hubActivationRepository;
    private final RestaurantRepository restaurantRepository;
    private final String latestVersion;

    // Hand-written constructor (NOT @RequiredArgsConstructor): LicenseIssuingService is a
    // @Lazy @Service and Lombok will not copy a field-level @Lazy onto the generated constructor,
    // which forces eager construction and throws on the unset HUB_LICENSE_PRIVATE_KEY placeholder
    // outside the `hub` profile (reports 241, 243).
    public HubHeartbeatService(
            @Lazy LicenseIssuingService licenseIssuingService,
            HubActivationRepository hubActivationRepository,
            RestaurantRepository restaurantRepository,
            @Value("${hub.latest-version:}") String latestVersion) {
        this.licenseIssuingService = licenseIssuingService;
        this.hubActivationRepository = hubActivationRepository;
        this.restaurantRepository = restaurantRepository;
        this.latestVersion = latestVersion;
    }

    public HubHeartbeatResponse heartbeat(HubHeartbeatRequest request) throws InvalidLicenseException {
        LicenseKey licenseKey = new LicenseKeyParser()
                .parseAndVerify(request.getLicenseKey(), licenseIssuingService.publicKey());
        UUID restaurantId = licenseKey.restaurantId();

        HubActivation activation = hubActivationRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new InvalidLicenseException(
                        "Esta licencia no está activada. Contacta a soporte de Ember."));
        if (!activation.getHardwareFingerprint().equals(request.getHardwareFingerprint())) {
            throw new InvalidLicenseException(
                    "Esta licencia está activada en otra PC. Contacta a soporte de Ember.");
        }

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new InvalidLicenseException(
                        "Esta licencia no corresponde a ningún restaurante."));

        String status = restaurant.getStatus() == RestaurantStatus.ACTIVE ? "OK" : "SUSPENDED";

        return HubHeartbeatResponse.builder()
                .status(status)
                .serverTime(Instant.now())
                .latestVersion(latestVersion == null || latestVersion.isBlank() ? null : latestVersion)
                .build();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatServiceTest`
Expected: PASS (6 tests).

- [ ] **Step 6: Run the full suite to catch context-load regressions**

Run: `cd backend && ./mvnw test`
Expected: PASS. If `SecurityAuditTest` / any full-context test fails with a `LicenseIssuingService` eager-construction error, the `@Lazy` is on the wrong place — it MUST be on the constructor parameter above, not a field.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatRequest.java \
        backend/src/main/java/com/vanter/ember/licensing/model/dto/HubHeartbeatResponse.java \
        backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java \
        backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java
git commit -m "feat(licensing): add hub heartbeat service"
```

---

## Task 4: Cloud `HubHeartbeatController` + security + rate-limit

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/licensing/controller/HubHeartbeatController.java`
- Modify: `backend/src/main/java/com/vanter/ember/config/SecurityConfig.java:77` (next to the `/hub-activations` permitAll)
- Modify: `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java` (the `paths` default list)
- Test: `backend/src/test/java/com/vanter/ember/licensing/controller/HubHeartbeatControllerTest.java`

**Interfaces:**
- Consumes: `HubHeartbeatService.heartbeat(HubHeartbeatRequest)` → `HubHeartbeatResponse` (Task 3).
- Produces: `POST /hub-heartbeat` — `permitAll`, `@Profile("!hub")`, `application/json` in and out. 400 on validation failure or `InvalidLicenseException`.

- [ ] **Step 1: Write the failing test**

`HubHeartbeatControllerTest.java` (mirror `HubActivationControllerTest`):

```java
package com.vanter.ember.licensing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.vanter.ember.config.CorsConfig;
import com.vanter.ember.config.SecurityConfig;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.identity.service.JwtService;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.service.HubHeartbeatService;
import com.vanter.ember.restaurant.repository.RestaurantRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HubHeartbeatController.class)
@Import({SecurityConfig.class, CorsConfig.class})
class HubHeartbeatControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean HubHeartbeatService hubHeartbeatService;
    @MockBean JwtService jwtService;
    @MockBean UserDetailsService userDetailsService;
    @MockBean RestaurantRepository restaurantRepository;

    private static final String VALID_BODY =
            "{\"licenseKey\":\"abc.def\",\"hardwareFingerprint\":\"fp-1\"}";

    @Test
    void heartbeat_noAuthHeader_reaches200() throws Exception {
        when(hubHeartbeatService.heartbeat(any())).thenReturn(HubHeartbeatResponse.builder()
                .status("OK").serverTime(Instant.now()).latestVersion(null).build());

        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void heartbeat_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void heartbeat_invalidLicense_returns400() throws Exception {
        when(hubHeartbeatService.heartbeat(any()))
                .thenThrow(new InvalidLicenseException("nope"));

        mockMvc.perform(post("/hub-heartbeat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatControllerTest`
Expected: FAIL — controller does not exist (and `/hub-heartbeat` would 401/403 without the security entry).

- [ ] **Step 3: Implement the controller**

```java
package com.vanter.ember.licensing.controller;

import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.licensing.model.dto.HubHeartbeatRequest;
import com.vanter.ember.licensing.model.dto.HubHeartbeatResponse;
import com.vanter.ember.licensing.service.HubHeartbeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public — the Hub authenticates via the license signature, not a bearer token. Cloud-side only:
 * {@code @Profile("!hub")} keeps it off a Hub's own LAN (same reasoning as
 * {@link com.vanter.ember.licensing.controller.HubActivationController}).
 */
@RestController
@RequestMapping("/hub-heartbeat")
@RequiredArgsConstructor
@Profile("!hub")
public class HubHeartbeatController {

    private final HubHeartbeatService hubHeartbeatService;

    @PostMapping
    public ResponseEntity<HubHeartbeatResponse> heartbeat(@Valid @RequestBody HubHeartbeatRequest request)
            throws InvalidLicenseException {
        return ResponseEntity.ok(hubHeartbeatService.heartbeat(request));
    }
}
```

- [ ] **Step 4: Add the security + rate-limit entries**

`SecurityConfig.java` — directly after the `/hub-activations` line (around line 77):

```java
.requestMatchers("/hub-heartbeat").permitAll()
```

`RateLimitProperties.java` — extend the default list:

```java
private List<String> paths = new ArrayList<>(
        List.of("/auth/login", "/auth/register", "/platform/auth/login",
                "/hub-activations", "/hub-heartbeat"));
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatControllerTest,SecurityAuditTest`
Expected: PASS. If `SecurityAuditTest` has a 401-matrix for unauthenticated routes, add `/hub-heartbeat` to it the same way `/hub-activations` appears (check the file — if it enumerates permitAll routes, this is required, not optional).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/controller/HubHeartbeatController.java \
        backend/src/main/java/com/vanter/ember/config/SecurityConfig.java \
        backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java \
        backend/src/test/java/com/vanter/ember/licensing/controller/HubHeartbeatControllerTest.java
# plus SecurityAuditTest.java if it was edited
git commit -m "feat(licensing): expose POST /hub-heartbeat"
```

---

## Task 5: Hub `HeartbeatScheduler` + `@EnableScheduling`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/sync/HeartbeatScheduler.java`
- Modify: `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java` (add `@EnableScheduling`)
- Modify: `backend/src/main/resources/application-hub.yml` (add the interval property)
- Test: `backend/src/test/java/com/vanter/ember/hub/sync/HeartbeatSchedulerTest.java`

**Interfaces:**
- Consumes:
  - `HubProperties.heartbeatUrl()` → `String`, `HubProperties.licenseFile()` → `Path` (Task 2).
  - `HubStateStore.load()` → `Optional<HubState>`, `HubStateStore.save(HubState)` (existing).
  - `LicenseService.recordHeartbeatSuccess(HubState)` → `HubState` (Task 1); `LicenseService.recordSuspended(HubState)` → `HubState` (Task 2).
- Produces:
  - `HeartbeatScheduler(HubProperties, HubStateStore, LicenseService)` — constructor, plain `@Component`.
  - `HeartbeatScheduler.runHeartbeat()` → `void` — the `@Scheduled` method; package-private so the test can call it directly. Catches every exception internally.

- [ ] **Step 1: Write the failing test**

`HeartbeatSchedulerTest.java` (uses the `HubProvisioningRunnerTest` HttpServer pattern):

```java
package com.vanter.ember.hub.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeartbeatSchedulerTest {

    @TempDir Path tempDir;

    private HubStateStore stateStore;
    private LicenseService licenseService;
    private HttpServer server;
    private UUID restaurantId;
    private Path licenseFile;
    private Path stateFile;

    @BeforeEach
    void setUp() throws Exception {
        restaurantId = UUID.randomUUID();
        licenseFile = tempDir.resolve("license.key");
        Files.writeString(licenseFile, "signed.license");
        stateFile = tempDir.resolve("state.json");
        stateStore = new HubStateStore(stateFile);

        // A real LicenseService so recordHeartbeatSuccess / recordSuspended write through the
        // real HubStateStore. Its license-file / public-key args are unused by those two methods.
        licenseService = new LicenseService(
                licenseFile, null, null, mock(com.vanter.ember.hub.license.HardwareFingerprintService.class),
                stateStore, Duration.ofHours(48));
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop(0);
    }

    private HubProperties propsPointingAt(String url) {
        return new HubProperties(
                tempDir, tempDir, licenseFile, tempDir.resolve("pub.der"), stateFile,
                5432, 8080, "", tempDir, tempDir, 9000, url, 48);
    }

    private void startServer(int statusCode, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/hub-heartbeat", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (var os = exchange.getResponseBody()) { os.write(bytes); }
        });
        server.start();
    }

    private String url() {
        return "http://localhost:" + server.getAddress().getPort() + "/hub-heartbeat";
    }

    @Test
    void ok_bumpsLastHeartbeatAndClearsSuspended() throws Exception {
        Instant oldHeartbeat = Instant.now().minus(2, ChronoUnit.DAYS);
        stateStore.save(new HubState("fp", restaurantId, oldHeartbeat,
                Instant.now().minus(1, ChronoUnit.HOURS)));
        startServer(200, "{\"status\":\"OK\",\"serverTime\":\"2026-08-28T00:00:00Z\",\"latestVersion\":null}");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isAfter(oldHeartbeat);
        assertThat(after.suspendedSince()).isNull();
    }

    @Test
    void suspended_stampsSuspendedSinceAndLeavesHeartbeat() throws Exception {
        Instant oldHeartbeat = Instant.now().minus(2, ChronoUnit.DAYS);
        stateStore.save(new HubState("fp", restaurantId, oldHeartbeat, null));
        startServer(200, "{\"status\":\"SUSPENDED\",\"serverTime\":\"2026-08-28T00:00:00Z\"}");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isEqualTo(oldHeartbeat);
        assertThat(after.suspendedSince()).isNotNull();
    }

    @Test
    void serverError_leavesStateUntouched() throws Exception {
        HubState before = new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        stateStore.save(before);
        startServer(500, "boom");

        new HeartbeatScheduler(propsPointingAt(url()), stateStore, licenseService).runHeartbeat();

        HubState after = stateStore.load().orElseThrow();
        assertThat(after.lastHeartbeatAt()).isEqualTo(before.lastHeartbeatAt());
        assertThat(after.suspendedSince()).isNull();
    }

    @Test
    void blankUrl_isNoOp() {
        HubState before = new HubState("fp", restaurantId, Instant.now().minus(2, ChronoUnit.DAYS), null);
        stateStore.save(before);

        new HeartbeatScheduler(propsPointingAt(""), stateStore, licenseService).runHeartbeat();

        assertThat(stateStore.load().orElseThrow().lastHeartbeatAt()).isEqualTo(before.lastHeartbeatAt());
    }
}
```

If `LicenseService`'s constructor rejects `null` for `publicKey`/`parser` (it just assigns them — it will not), pass `mock(...)` instead.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HeartbeatSchedulerTest`
Expected: FAIL — `HeartbeatScheduler` does not exist.

- [ ] **Step 3: Implement `HeartbeatScheduler`**

```java
package com.vanter.ember.hub.sync;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HubState;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodic Hub -> cloud license heartbeat (spec 2026-08-28-hub-license-heartbeat-design.md).
 * On {@code OK} it resets the 4-day offline grace clock; on {@code SUSPENDED} it starts the
 * 48-hour courtesy-grace clock; on any failure it does nothing and lets the next cycle retry.
 * Every exception is swallowed inside {@link #runHeartbeat()} — an exception escaping a
 * {@code @Scheduled} method makes Spring stop scheduling it.
 */
@Component
@Profile("hub")
public class HeartbeatScheduler {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatScheduler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HubProperties properties;
    private final HubStateStore stateStore;
    private final LicenseService licenseService;

    public HeartbeatScheduler(
            HubProperties properties, HubStateStore stateStore, LicenseService licenseService) {
        this.properties = properties;
        this.stateStore = stateStore;
        this.licenseService = licenseService;
    }

    @Scheduled(fixedDelayString = "${ember.hub.heartbeat-interval-ms:300000}")
    public void runHeartbeat() {
        try {
            if (properties.heartbeatUrl().isBlank()) {
                log.debug("EMBER_HUB_HEARTBEAT_URL not set; skipping heartbeat.");
                return;
            }
            HubState state = stateStore.load().orElse(null);
            if (state == null) {
                log.warn("hub-state.json missing; cannot send heartbeat.");
                return;
            }

            String licenseKey = Files.readString(properties.licenseFile());
            String requestBody = MAPPER.writeValueAsString(
                    new HeartbeatRequestBody(licenseKey, state.hardwareFingerprint()));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.heartbeatUrl()))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Heartbeat failed: HTTP {}", response.statusCode());
                return;
            }

            HeartbeatResponseBody body = MAPPER.readValue(response.body(), HeartbeatResponseBody.class);
            if ("OK".equals(body.status())) {
                licenseService.recordHeartbeatSuccess(state);
                log.debug("Heartbeat OK.");
            } else if ("SUSPENDED".equals(body.status())) {
                licenseService.recordSuspended(state);
                log.warn("Heartbeat reports the restaurant is SUSPENDED.");
            } else {
                log.warn("Heartbeat returned unrecognised status '{}'.", body.status());
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Heartbeat cycle failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Heartbeat cycle failed unexpectedly", e);
        }
    }

    private record HeartbeatRequestBody(String licenseKey, String hardwareFingerprint) {}

    private record HeartbeatResponseBody(String status, String serverTime, String latestVersion) {}
}
```

- [ ] **Step 4: Enable scheduling (hub profile only)**

`HubBeansConfig.java` — add the annotation and import:

```java
import org.springframework.scheduling.annotation.EnableScheduling;
// ...
@Configuration
@Profile("hub")
@EnableScheduling
public class HubBeansConfig {
```

`application-hub.yml` — add:

```yaml
ember:
  hub:
    heartbeat-interval-ms: ${EMBER_HUB_HEARTBEAT_INTERVAL_MS:300000}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=HeartbeatSchedulerTest`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/sync/HeartbeatScheduler.java \
        backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java \
        backend/src/main/resources/application-hub.yml \
        backend/src/test/java/com/vanter/ember/hub/sync/HeartbeatSchedulerTest.java
git commit -m "feat(hub): add periodic license heartbeat scheduler"
```

---

## Task 6: `GracePeriodInterceptor` blocks on suspended-grace too

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java`

**Interfaces:**
- Consumes: `LicenseService.isWithinGracePeriod(HubState)` (existing), `LicenseService.isSuspendedGraceExpired(HubState)` (Task 2).
- Produces: no new public surface — `preHandle` now returns `false` with a `license_suspended` body when `isSuspendedGraceExpired` is true, and keeps the existing `license_grace_period_expired` body for the no-contact case.

- [ ] **Step 1: Write the failing test**

Add to `GracePeriodInterceptorTest.java`:

```java
@Test
void preHandle_suspendedGraceExpired_blocksWithSuspendedMessage() throws Exception {
    HubState suspendedLongAgo = new HubState("fp", UUID.randomUUID(), Instant.now(),
            Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS));
    when(stateStore.load()).thenReturn(Optional.of(suspendedLongAgo));
    when(licenseService.isWithinGracePeriod(suspendedLongAgo)).thenReturn(true);   // 4-day clock is fine
    when(licenseService.isSuspendedGraceExpired(suspendedLongAgo)).thenReturn(true);

    MockHttpServletResponse response = new MockHttpServletResponse();
    boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

    assertThat(proceed).isFalse();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("license_suspended");
}

@Test
void preHandle_notSuspendedAndWithinGrace_proceeds() throws Exception {
    HubState healthy = new HubState("fp", UUID.randomUUID(), Instant.now(), null);
    when(stateStore.load()).thenReturn(Optional.of(healthy));
    when(licenseService.isWithinGracePeriod(healthy)).thenReturn(true);
    when(licenseService.isSuspendedGraceExpired(healthy)).thenReturn(false);

    boolean proceed = interceptor.preHandle(
            new MockHttpServletRequest(), new MockHttpServletResponse(), new Object());

    assertThat(proceed).isTrue();
}
```

Match the test's existing style — if it currently news up `GracePeriodInterceptor` with real objects rather than mocks, switch those two collaborators to Mockito mocks (`licenseService`, `stateStore`) so the booleans can be driven directly; keep the existing "stale = blocked" / "fresh = ok" tests working by stubbing `isWithinGracePeriod` accordingly and `isSuspendedGraceExpired` → `false`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GracePeriodInterceptorTest`
Expected: FAIL — `isSuspendedGraceExpired` not stubbed / interceptor ignores it.

- [ ] **Step 3: Implement**

Replace the body of `preHandle` from the grace check onward:

```java
HubState state = stateStore.load()
        .orElseThrow(() -> new IllegalStateException(
                "hub-state.json missing after startup license validation"));

if (licenseService.isSuspendedGraceExpired(state)) {
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType("application/json");
    response.getWriter().write(
            "{\"error\":\"license_suspended\","
                    + "\"message\":\"La licencia de Ember Hub está suspendida. "
                    + "Contacta a Vanter para reactivarla.\"}");
    return false;
}

if (licenseService.isWithinGracePeriod(state)) {
    return true;
}
response.setStatus(HttpServletResponse.SC_FORBIDDEN);
response.setContentType("application/json");
response.getWriter().write(
        "{\"error\":\"license_grace_period_expired\","
                + "\"message\":\"La licencia de Ember Hub no ha podido validarse con la nube "
                + "en más de 4 días. Verifica tu conexión a internet.\"}");
return false;
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw test -Dtest=GracePeriodInterceptorTest`
Expected: PASS (existing tests + 2 new).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java \
        backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java
git commit -m "feat(hub): block writes once the suspended-license grace lapses"
```

---

## Task 7: Wiring — dashboard line, config docs, full verification

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`
- Modify: `.env.example`
- Modify: `PROGRESS.md`
- Create: `reports/NNN-<task>.md` for each of Tasks 1–7 (or one combined report per CLAUDE.md judgement — one per task is the rule).

**Interfaces:**
- Consumes: `HubStateStore.load()` → `Optional<HubState>`; `HubState.lastHeartbeatAt()`, `HubState.suspendedSince()`.
- Produces: no code surface — a Swing label + docs.

- [ ] **Step 1: Add the dashboard status line**

In `HubDashboard.java`, find where the Postgres / Servidor status labels are built and add a third, refreshed by the same timer/refresh path. It reads `HubState` via the existing `HubStateStore` (the dashboard already has access to `HubProperties`; construct a `HubStateStore(properties.stateFile())` if it does not already hold one). Text logic:

```java
private String licenseStatusText(HubState state) {
    if (state == null) {
        return "Licencia: sin estado local";
    }
    if (state.suspendedSince() != null) {
        return "Licencia: SUSPENDIDA (desde hace " + humanizeSince(state.suspendedSince()) + ")";
    }
    return "Licencia: OK · último contacto hace " + humanizeSince(state.lastHeartbeatAt());
}

private static String humanizeSince(java.time.Instant when) {
    java.time.Duration d = java.time.Duration.between(when, java.time.Instant.now());
    if (d.toMinutes() < 60) return d.toMinutes() + " min";
    if (d.toHours() < 48) return d.toHours() + " h";
    return d.toDays() + " d";
}
```

Match the file's existing label-construction and refresh style; do not restructure the dashboard. No test — Swing desktop paths are manually verified (consistent with `HubTrayIcon`, HUB-01-10).

- [ ] **Step 2: Document the new env vars**

In `.env.example`, near the existing `EMBER_HUB_*` block:

```
# Ember Hub — license heartbeat (sub-project A1). Leave EMBER_HUB_HEARTBEAT_URL blank to disable.
EMBER_HUB_HEARTBEAT_URL=https://<cloud-host>/hub-heartbeat
EMBER_HUB_HEARTBEAT_INTERVAL_MS=300000
EMBER_HUB_SUSPENDED_GRACE_HOURS=48
# Cloud side: the current Hub release the /hub-heartbeat response advertises (auto-updater lands in sub-project B).
hub.latest-version=
```

- [ ] **Step 3: Run the whole backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, zero failures/errors. Note the total count for the report.

- [ ] **Step 4: Manual smoke (document results in the report; no code)**

With a cloud instance on `:8081` and a Hub on `:8080` (reuse the report-256 two-process setup):
1. Start the Hub with `EMBER_HUB_HEARTBEAT_URL=http://localhost:8081/hub-heartbeat` — within ~1 min the Hub log shows `Heartbeat OK.` and `hub-state.json`'s `lastHeartbeatAt` advances; dashboard shows `Licencia: OK · último contacto hace 0 min`.
2. `UPDATE restaurants SET status = 'SUSPENDED' WHERE id = '<restaurantId>'` on the cloud DB — next cycle the Hub log shows `SUSPENDED`, `hub-state.json` gains `suspendedSince`, dashboard flips to `Licencia: SUSPENDIDA ...`. Order/payment writes still succeed (inside 48h).
3. Set `EMBER_HUB_SUSPENDED_GRACE_HOURS=0`, restart the Hub, let one cycle run → `POST /sessions/*/items` now returns 403 `license_suspended`.
4. `UPDATE restaurants SET status = 'ACTIVE' ...` → next cycle clears `suspendedSince`, writes work again.

- [ ] **Step 5: Reports + PROGRESS**

Write `reports/NNN-*.md` per Task (CLAUDE.md §4) and update `PROGRESS.md` (Current Execution State + Task Queue + System Health), keeping it under 180 lines.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java .env.example PROGRESS.md reports/
git commit -m "feat(hub): show license heartbeat status on the dashboard"
```

- [ ] **Step 7: PR**

Push `feat/hub-license-heartbeat`, open a PR to `main`, wait for `lint-backend` / `lint-frontend` / `lint-gateway` green, then squash-merge.

---

## Self-Review

**1. Spec coverage:**
- §3.1 endpoint + shapes → Task 3 (DTOs) + Task 4 (controller, security, rate-limit). ✓
- §3.2 service logic (sig, fingerprint, restaurant, status, latestVersion, serverTime) → Task 3. ✓
- §3.3 "only `Restaurant.status`" → Task 3 (no new entity). ✓
- §4.1 config (`heartbeatUrl`, `heartbeatIntervalMs`, `suspendedGraceHours`) → Task 2 (`HubProperties` gets `heartbeatUrl` + `suspendedGraceHours`) + Task 5 (`ember.hub.heartbeat-interval-ms` as a Spring property — the interval lives only as a property, not a `HubProperties` field, because `@Scheduled(fixedDelayString=...)` needs a resolvable placeholder and cannot read a plain record; noted as a deliberate deviation). ✓
- §4.2 `HubState.suspendedSince` + legacy-null read → Task 1. ✓
- §4.3 scheduler behaviour table + no-`initialDelay` + blank-URL no-op + exception containment + `@EnableScheduling` → Task 5. ✓
- §4.4 second block condition + distinct message → Task 6. ✓
- §5 dashboard line → Task 7. ✓
- §6 error handling (timeouts, 4xx == failed cycle, rate-limit path) → Task 3/4/5. ✓
- §7 tests → each task's test steps; `HubHeartbeatServiceTest`, `HubHeartbeatControllerTest`, `HeartbeatSchedulerTest`, extended `LicenseServiceTest` / `GracePeriodInterceptorTest` / `HubStateStoreTest` all present. ✓
- §8 file list → matches the File Structure section. ✓

**2. Placeholder scan:** No "TBD"/"handle edge cases"/"similar to". One intentional token: `newслужиceWithGrace` in Task 2 Step 1 is explicitly flagged in-line as "rename to the file's builder" with the real behaviour spelled out. `reports/NNN-*.md` — N is "next free number is 265" per Global Constraints.

**3. Type consistency:**
- `HubState` 4-arg canonical + 3-arg convenience + `withHeartbeatNow()` / `withSuspendedSince(Instant)` — used consistently in Tasks 1, 5, 6.
- `LicenseService.isSuspendedGraceExpired(HubState)` / `recordSuspended(HubState)` / `recordHeartbeatSuccess(HubState)` — defined Task 1/2, consumed Task 5/6 with matching signatures.
- `HubHeartbeatService.heartbeat(HubHeartbeatRequest)` → `HubHeartbeatResponse` — defined Task 3, consumed Task 4.
- `HubHeartbeatResponse` getters (`getStatus()`, `getServerTime()`, `getLatestVersion()`) via Lombok `@Data` — used in Task 3/4 tests.
- `HubProperties.heartbeatUrl()` / `suspendedGraceHours()` — defined Task 2, consumed Task 5.
- `RestaurantStatus.ACTIVE` — verified against `com.vanter.ember.restaurant.model.RestaurantStatus` (`ACTIVE, SUSPENDED, INACTIVE`).
