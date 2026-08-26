# HUB-01: Portable Database Bootstrap and Licensing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the existing `backend/` Spring Boot app a `hub` profile that, on a Windows PC with no prior setup, verifies a signed license against this machine's hardware, starts a portable local Postgres (initializing it on first run), and shows a system tray icon — before Spring's own `DataSource` bean ever tries to connect.

**Architecture:** Ember Hub is not a new application — it's `backend/`'s existing JAR, started under `spring.profiles.active=hub`. License and Postgres-portable checks happen in `EmberApplication.main()`, *before* `SpringApplication.run()`, because Spring's `DataSource` autoconfiguration connects during context refresh — too late to start Postgres from a normal `ApplicationRunner`. License/Hub state (hardware fingerprint, last successful heartbeat) lives in a local JSON file, independent of Postgres, so the app can report *why* it won't start even if Postgres itself is the thing that's broken. A `HandlerInterceptor`, active only under the `hub` profile, blocks order/payment-creating endpoints once the 4-day license grace period (spec §2.8) lapses.

**Tech Stack:** Java 17 (existing `backend/` toolchain), OSHI (new dependency, hardware fingerprint), `java.security` (RSA signature, no new dependency), Jackson (existing, JSON state file), `java.awt.SystemTray` (JDK built-in), `ProcessBuilder` (JDK built-in, drives `pg_ctl`/`initdb`).

**Spec:** `docs/superpowers/specs/ember_hub.md` — this plan implements §2.2 (Licenciamiento), §2.3's "Persistencia" bullet's portable-Postgres part, and §4's "Manejo de errores de arranque" requirement (Postgres corrupto, puerto ocupado, licencia inválida). It does **not** implement §2.1/§2.7 (sync — HUB-02), §2.3's packaging/jpackage bullet or §2.3's SystemTray-as-update-notifier behavior beyond a bare icon (HUB-03), or §2.9–2.11 (backup, auto-updater, hardware bridge extension — HUB-04). Those get their own plans once this one is implemented and reviewed.

## Global Constraints

- **Windows-only for v1** (spec §2.2, decided 2026-08-24) — no Mac/Linux code paths, no cross-platform abstraction to maintain.
- **No new Maven module.** All code in this plan lives inside the existing `backend/` project, in a new `com.vanter.ember.hub` package tree, gated by `@Profile("hub")`. `ember-hub/` at the repo root (packaging/installer config) is HUB-03's concern, not this plan's.
- **No new database tables.** License/Hub state (hardware fingerprint, `restaurantId`, last successful heartbeat) is a local JSON file, not a Postgres row — it has to be readable even when Postgres itself fails to start.
- **Local Postgres credentials are `ember`/`ember`**, matching the existing cloud-dev convention (`.env.example`) — deliberate: this Postgres only ever binds to `localhost` on the restaurant's own PC, never reachable over the network, so there's no meaningful attack surface to justify inventing a generated-password-and-secure-storage mechanism for v1.
- **License grace period is 4 days** (spec §2.8), measured from the last successful heartbeat. This plan initializes `lastHeartbeatAt` to the activation timestamp on first run — HUB-02's real network heartbeat will refresh it going forward; until HUB-02 exists, every fresh Hub install effectively gets a 4-day trial window from install time.
- Run `cd backend && ./mvnw test` after every task — do not move to the next task on a red suite. Every class in this plan except `PortableDatabaseBootstrap`'s two process-shelling methods is plain Java, unit-testable without Spring context or real Postgres/`pg_ctl` binaries.

---

### Task 1: Add OSHI dependency and `HubProperties`

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java`

**Interfaces:**
- Produces: `HubProperties` — a plain record with a `fromEnvironment()` static factory reading `EMBER_HUB_*` env vars with defaults. Used both from `EmberApplication.main()` (before Spring exists) and later as a `@Bean` (Task 8), so it must have zero Spring dependency.

- [ ] **Step 1: Add the OSHI dependency**

In `backend/pom.xml`, inside `<dependencies>`, add:

```xml
<dependency>
    <groupId>com.github.oshi</groupId>
    <artifactId>oshi-core</artifactId>
    <version>6.6.5</version>
</dependency>
```

- [ ] **Step 2: Write the failing test**

```java
package com.vanter.ember.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class HubPropertiesTest {

    @Test
    void fromEnvironment_usesDefaultsWhenNoEnvVarsSet() {
        HubProperties properties = HubProperties.fromEnvironment();

        assertThat(properties.dataDir()).isEqualTo(Path.of("./data/postgres"));
        assertThat(properties.postgresBinDir()).isEqualTo(Path.of("./postgres/bin"));
        assertThat(properties.licenseFile()).isEqualTo(Path.of("./license.key"));
        assertThat(properties.publicKeyFile()).isEqualTo(Path.of("./hub-public-key.der"));
        assertThat(properties.stateFile()).isEqualTo(Path.of("./hub-state.json"));
        assertThat(properties.postgresPort()).isEqualTo(5432);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubPropertiesTest`
Expected: FAIL — `HubProperties` does not exist yet (compilation error).

- [ ] **Step 4: Write the implementation**

```java
package com.vanter.ember.hub.config;

import java.nio.file.Path;

/**
 * Deliberately NOT a Spring {@code @ConfigurationProperties} bean: {@link
 * com.vanter.ember.EmberApplication#main} needs these values before {@code SpringApplication.run}
 * is even called (to start portable Postgres before Spring's own DataSource autoconfiguration
 * tries to connect), so this has to be constructible with zero Spring context. The same instance
 * shape is reused as a {@code @Bean} later (see HubBeansConfig) for post-boot consumers.
 */
public record HubProperties(
        Path dataDir,
        Path postgresBinDir,
        Path licenseFile,
        Path publicKeyFile,
        Path stateFile,
        int postgresPort) {

    public static HubProperties fromEnvironment() {
        return new HubProperties(
                Path.of(env("EMBER_HUB_DATA_DIR", "./data/postgres")),
                Path.of(env("EMBER_HUB_POSTGRES_BIN_DIR", "./postgres/bin")),
                Path.of(env("EMBER_HUB_LICENSE_FILE", "./license.key")),
                Path.of(env("EMBER_HUB_PUBLIC_KEY_FILE", "./hub-public-key.der")),
                Path.of(env("EMBER_HUB_STATE_FILE", "./hub-state.json")),
                Integer.parseInt(env("EMBER_HUB_POSTGRES_PORT", "5432")));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value != null ? value : fallback;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubPropertiesTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/java/com/vanter/ember/hub/config/HubProperties.java backend/src/test/java/com/vanter/ember/hub/config/HubPropertiesTest.java
git commit -m "feat(hub): add oshi dependency and HubProperties"
```

---

### Task 2: `HardwareFingerprintService`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/HardwareFingerprintService.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/HardwareFingerprintServiceTest.java`

**Interfaces:**
- Produces: `HardwareFingerprintService.currentFingerprint(): String` — a stable, opaque hex string derived from this machine's CPU + motherboard identifiers. Consumed by `LicenseService` (Task 5).

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HardwareFingerprintServiceTest {

    @Test
    void currentFingerprint_isStableAcrossCalls() {
        HardwareFingerprintService service = new HardwareFingerprintService();

        String first = service.currentFingerprint();
        String second = service.currentFingerprint();

        assertThat(first).isNotBlank();
        assertThat(first).isEqualTo(second);
        assertThat(first).matches("[0-9a-f]{64}");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HardwareFingerprintServiceTest`
Expected: FAIL — `HardwareFingerprintService` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.hub.license;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * Derives a stable per-machine fingerprint from CPU + motherboard serials via OSHI. Windows-only
 * for v1 (spec §2.2) — OSHI itself is cross-platform, but nothing here has been tried on
 * Mac/Linux and the rest of the Hub packaging route wouldn't run there anyway.
 */
public class HardwareFingerprintService {

    public String currentFingerprint() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        String cpuId = hardware.getProcessor().getProcessorIdentifier().getProcessorID();
        String boardSerial = hardware.getComputerSystem().getBaseboard().getSerialNumber();
        return sha256Hex(cpuId + "|" + boardSerial);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HardwareFingerprintServiceTest`
Expected: PASS. (Runs on whatever machine executes the test — CI or your own PC — and reads that machine's real CPU/board identifiers via OSHI; it doesn't need Windows specifically to pass, only the packaged Hub install does.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/HardwareFingerprintService.java backend/src/test/java/com/vanter/ember/hub/license/HardwareFingerprintServiceTest.java
git commit -m "feat(hub): add OSHI-based hardware fingerprint service"
```

---

### Task 3: `LicenseKey`, `LicenseKeyParser`, `InvalidLicenseException`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/LicenseKey.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/license/InvalidLicenseException.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/license/LicenseKeyParser.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/LicenseKeyParserTest.java`

**Interfaces:**
- Produces: `LicenseKey(UUID restaurantId, Instant issuedAt)`; `LicenseKeyParser.parseAndVerify(String contents, PublicKey publicKey): LicenseKey throws InvalidLicenseException`; `LicenseKeyParser.sign(LicenseKey key, PrivateKey privateKey): String` (the admin-side signing helper — not wired into any endpoint by this plan, but needed to produce test fixtures and eventually a real `license.key` for a pilot customer); `LicenseKeyParser.loadPublicKey(Path publicKeyFile): PublicKey throws InvalidLicenseException`. Consumed by `LicenseService` (Task 5) and `EmberApplication.main` (Task 7).
- File format: `license.key` is `base64(payloadJson) + "." + base64(signature)`, RSA-SHA256 over the raw payload bytes. Payload JSON: `{"restaurantId":"<uuid>","issuedAt":"<ISO-8601 instant>"}`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LicenseKeyParserTest {

    @Test
    void sign_thenParseAndVerify_roundTrips() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();
        LicenseKey original = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));

        String signed = LicenseKeyParser.sign(original, keyPair.getPrivate());
        LicenseKey parsed = parser.parseAndVerify(signed, keyPair.getPublic());

        assertThat(parsed).isEqualTo(original);
    }

    @Test
    void parseAndVerify_rejectsSignatureFromADifferentKeyPair() throws Exception {
        KeyPair signingKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        KeyPair wrongKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();
        LicenseKey original = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        String signed = LicenseKeyParser.sign(original, signingKeyPair.getPrivate());

        assertThatThrownBy(() -> parser.parseAndVerify(signed, wrongKeyPair.getPublic()))
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("firma");
    }

    @Test
    void parseAndVerify_rejectsMalformedContent() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        LicenseKeyParser parser = new LicenseKeyParser();

        assertThatThrownBy(() -> parser.parseAndVerify("not-a-license-key", keyPair.getPublic()))
                .isInstanceOf(InvalidLicenseException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=LicenseKeyParserTest`
Expected: FAIL — none of the three classes exist yet.

- [ ] **Step 3: Write the implementation**

`InvalidLicenseException.java`:

```java
package com.vanter.ember.hub.license;

/** Anything that stops Ember Hub from starting under a valid, matching license. */
public class InvalidLicenseException extends Exception {

    public InvalidLicenseException(String message) {
        super(message);
    }

    public InvalidLicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`LicenseKey.java`:

```java
package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

public record LicenseKey(UUID restaurantId, Instant issuedAt) {}
```

`LicenseKeyParser.java`:

```java
package com.vanter.ember.hub.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * (De)serializes and RSA-signs {@code license.key}: {@code base64(payloadJson).base64(signature)},
 * signature algorithm SHA256withRSA over the raw payload bytes.
 */
public class LicenseKeyParser {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    public LicenseKey parseAndVerify(String licenseKeyContents, PublicKey publicKey)
            throws InvalidLicenseException {
        String[] parts = licenseKeyContents.strip().split("\\.");
        if (parts.length != 2) {
            throw new InvalidLicenseException("El formato de license.key no es válido.");
        }

        byte[] payloadBytes;
        byte[] signatureBytes;
        try {
            payloadBytes = Base64.getDecoder().decode(parts[0]);
            signatureBytes = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw new InvalidLicenseException("El formato de license.key no es válido.", e);
        }

        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(payloadBytes);
            if (!signature.verify(signatureBytes)) {
                throw new InvalidLicenseException("La firma de license.key no es válida.");
            }
        } catch (GeneralSecurityException e) {
            throw new InvalidLicenseException("No se pudo verificar la firma de license.key.", e);
        }

        try {
            LicensePayload payload = MAPPER.readValue(payloadBytes, LicensePayload.class);
            return new LicenseKey(payload.restaurantId(), payload.issuedAt());
        } catch (IOException e) {
            throw new InvalidLicenseException("El contenido de license.key no es válido.", e);
        }
    }

    /** Admin-side helper: produces the `license.key` file contents for a given restaurant. */
    public static String sign(LicenseKey key, PrivateKey privateKey) throws GeneralSecurityException, IOException {
        byte[] payloadBytes = MAPPER.writeValueAsBytes(new LicensePayload(key.restaurantId(), key.issuedAt()));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(payloadBytes);
        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(payloadBytes)
                + "." + Base64.getEncoder().encodeToString(signatureBytes);
    }

    public static PublicKey loadPublicKey(Path publicKeyFile) throws InvalidLicenseException {
        try {
            byte[] keyBytes = Files.readAllBytes(publicKeyFile);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (IOException | GeneralSecurityException e) {
            throw new InvalidLicenseException(
                    "No se pudo leer la clave pública en " + publicKeyFile + ".", e);
        }
    }

    private record LicensePayload(UUID restaurantId, Instant issuedAt) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=LicenseKeyParserTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/LicenseKey.java backend/src/main/java/com/vanter/ember/hub/license/InvalidLicenseException.java backend/src/main/java/com/vanter/ember/hub/license/LicenseKeyParser.java backend/src/test/java/com/vanter/ember/hub/license/LicenseKeyParserTest.java
git commit -m "feat(hub): add license.key RSA signing/verification"
```

---

### Task 4: `HubState` and `HubStateStore`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/HubState.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/license/HubStateStore.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java`

**Interfaces:**
- Produces: `HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt)`; `HubStateStore.load(): Optional<HubState>`; `HubStateStore.save(HubState state): void`. Consumed by `LicenseService` (Task 5) and `GracePeriodInterceptor` (Task 9).

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HubStateStoreTest {

    @TempDir Path tempDir;

    @Test
    void load_returnsEmptyWhenFileDoesNotExist() {
        HubStateStore store = new HubStateStore(tempDir.resolve("hub-state.json"));

        assertThat(store.load()).isEmpty();
    }

    @Test
    void save_thenLoad_roundTrips() {
        Path stateFile = tempDir.resolve("nested/hub-state.json");
        HubStateStore store = new HubStateStore(stateFile);
        HubState state = new HubState(
                "abc123", UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));

        store.save(state);
        Optional<HubState> loaded = store.load();

        assertThat(loaded).contains(state);
    }
}
```

Add `import java.util.Optional;` to the test's imports.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubStateStoreTest`
Expected: FAIL — `HubState`/`HubStateStore` do not exist yet.

- [ ] **Step 3: Write the implementation**

`HubState.java`:

```java
package com.vanter.ember.hub.license;

import java.time.Instant;
import java.util.UUID;

/** Local, Postgres-independent Hub state — has to be readable even when Postgres won't start. */
public record HubState(String hardwareFingerprint, UUID restaurantId, Instant lastHeartbeatAt) {}
```

`HubStateStore.java`:

```java
package com.vanter.ember.hub.license;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class HubStateStore {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path stateFile;

    public HubStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    public Optional<HubState> load() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(stateFile.toFile(), HubState.class));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer " + stateFile, e);
        }
    }

    public void save(HubState state) {
        try {
            Path parent = stateFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(stateFile.toFile(), state);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo escribir " + stateFile, e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubStateStoreTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/HubState.java backend/src/main/java/com/vanter/ember/hub/license/HubStateStore.java backend/src/test/java/com/vanter/ember/hub/license/HubStateStoreTest.java
git commit -m "feat(hub): add local hub-state.json store"
```

---

### Task 5: `LicenseService`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java`

**Interfaces:**
- Consumes: `LicenseKeyParser`, `HardwareFingerprintService`, `HubStateStore`, `HubState`, `LicenseKey`, `InvalidLicenseException` (Tasks 2–4).
- Produces: `LicenseService.validateOrActivate(): HubState throws InvalidLicenseException`; `LicenseService.isWithinGracePeriod(HubState): boolean`; `LicenseService.recordHeartbeatSuccess(HubState): HubState`. Consumed by `EmberApplication.main` (Task 7) and `GracePeriodInterceptor` (Task 9); `recordHeartbeatSuccess` has no caller in this plan — HUB-02's sync client calls it after each successful heartbeat.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseServiceTest {

    @TempDir Path tempDir;

    private KeyPair keyPair;
    private Path licenseFile;
    private HardwareFingerprintService fingerprintService;
    private HubStateStore stateStore;
    private LicenseService licenseService;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        licenseFile = tempDir.resolve("license.key");
        fingerprintService = mock(HardwareFingerprintService.class);
        when(fingerprintService.currentFingerprint()).thenReturn("fingerprint-a");
        stateStore = new HubStateStore(tempDir.resolve("hub-state.json"));
        licenseService = new LicenseService(
                licenseFile, keyPair.getPublic(), new LicenseKeyParser(), fingerprintService, stateStore);
    }

    private void writeValidLicenseFile() throws Exception {
        LicenseKey key = new LicenseKey(UUID.randomUUID(), Instant.now().truncatedTo(ChronoUnit.SECONDS));
        Files.writeString(licenseFile, LicenseKeyParser.sign(key, keyPair.getPrivate()));
    }

    @Test
    void validateOrActivate_withNoLicenseFile_throws() {
        assertThatThrownBy(() -> licenseService.validateOrActivate())
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("license.key");
    }

    @Test
    void validateOrActivate_firstRun_activatesAndStoresState() throws Exception {
        writeValidLicenseFile();

        HubState state = licenseService.validateOrActivate();

        assertThat(state.hardwareFingerprint()).isEqualTo("fingerprint-a");
        assertThat(stateStore.load()).contains(state);
    }

    @Test
    void validateOrActivate_secondRunSameHardware_returnsExistingState() throws Exception {
        writeValidLicenseFile();
        HubState firstRun = licenseService.validateOrActivate();

        HubState secondRun = licenseService.validateOrActivate();

        assertThat(secondRun).isEqualTo(firstRun);
    }

    @Test
    void validateOrActivate_secondRunDifferentHardware_throws() throws Exception {
        writeValidLicenseFile();
        licenseService.validateOrActivate();
        when(fingerprintService.currentFingerprint()).thenReturn("fingerprint-b");

        assertThatThrownBy(() -> licenseService.validateOrActivate())
                .isInstanceOf(InvalidLicenseException.class)
                .hasMessageContaining("otra PC");
    }

    @Test
    void isWithinGracePeriod_trueJustAfterHeartbeat_falseAfterFiveDays() {
        HubState recent = new HubState("fp", UUID.randomUUID(), Instant.now());
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));

        assertThat(licenseService.isWithinGracePeriod(recent)).isTrue();
        assertThat(licenseService.isWithinGracePeriod(stale)).isFalse();
    }

    @Test
    void recordHeartbeatSuccess_updatesTimestampAndPersists() {
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        stateStore.save(stale);

        HubState updated = licenseService.recordHeartbeatSuccess(stale);

        assertThat(licenseService.isWithinGracePeriod(updated)).isTrue();
        assertThat(stateStore.load()).contains(updated);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=LicenseServiceTest`
Expected: FAIL — `LicenseService` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.hub.license;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class LicenseService {

    private static final Duration GRACE_PERIOD = Duration.ofDays(4);

    private final Path licenseFile;
    private final PublicKey publicKey;
    private final LicenseKeyParser parser;
    private final HardwareFingerprintService fingerprintService;
    private final HubStateStore stateStore;

    public LicenseService(
            Path licenseFile,
            PublicKey publicKey,
            LicenseKeyParser parser,
            HardwareFingerprintService fingerprintService,
            HubStateStore stateStore) {
        this.licenseFile = licenseFile;
        this.publicKey = publicKey;
        this.parser = parser;
        this.fingerprintService = fingerprintService;
        this.stateStore = stateStore;
    }

    /**
     * First run: verifies the signed license, stamps this machine's fingerprint, and persists it.
     * Later runs: verifies the fingerprint still matches what was stamped — a mismatch means this
     * license.key was copied onto a different PC.
     */
    public HubState validateOrActivate() throws InvalidLicenseException {
        if (!Files.exists(licenseFile)) {
            throw new InvalidLicenseException(
                    "No se encontró license.key en " + licenseFile + ". Contacta a soporte de Ember "
                            + "para obtener tu licencia.");
        }
        String contents;
        try {
            contents = Files.readString(licenseFile);
        } catch (IOException e) {
            throw new InvalidLicenseException("No se pudo leer license.key.", e);
        }
        LicenseKey licenseKey = parser.parseAndVerify(contents, publicKey);
        String currentFingerprint = fingerprintService.currentFingerprint();

        Optional<HubState> existing = stateStore.load();
        if (existing.isPresent()) {
            HubState state = existing.get();
            if (!state.hardwareFingerprint().equals(currentFingerprint)) {
                throw new InvalidLicenseException(
                        "Esta licencia ya está activada en otra PC. Contacta a soporte de Ember "
                                + "para reasignarla.");
            }
            return state;
        }

        HubState activated = new HubState(currentFingerprint, licenseKey.restaurantId(), Instant.now());
        stateStore.save(activated);
        return activated;
    }

    public boolean isWithinGracePeriod(HubState state) {
        return Duration.between(state.lastHeartbeatAt(), Instant.now()).compareTo(GRACE_PERIOD) <= 0;
    }

    public HubState recordHeartbeatSuccess(HubState state) {
        HubState updated = new HubState(state.hardwareFingerprint(), state.restaurantId(), Instant.now());
        stateStore.save(updated);
        return updated;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=LicenseServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/LicenseService.java backend/src/test/java/com/vanter/ember/hub/license/LicenseServiceTest.java
git commit -m "feat(hub): add LicenseService activation, validation, grace period"
```

---

### Task 6: `PortableDatabaseBootstrap`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseException.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrap.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/bootstrap/PortableDatabaseBootstrapTest.java`

**Interfaces:**
- Produces: `PortableDatabaseBootstrap.ensureRunning(): void throws PortableDatabaseException` — checks the port, `initdb`s an empty data directory if needed, then `pg_ctl start`s it, covering all three boot-error cases from spec §4 (port in use, corrupt/failed data directory, missing binaries surface as the same "start failed" path). Consumed by `EmberApplication.main` (Task 7).

**Testing note:** `initdb`/`pg_ctl` aren't available in this repo or CI (no bundled portable Postgres binaries yet — that's HUB-03's job). Only `isPortInUse`, the one piece of pure decision logic that doesn't need those binaries, gets a unit test here. The full `ensureRunning` flow against real binaries is verified manually in Task 11, against an actual portable Postgres distribution on Windows.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PortableDatabaseBootstrapTest {

    @Test
    void isPortInUse_falseWhenPortIsFree() throws Exception {
        PortableDatabaseBootstrap bootstrap =
                new PortableDatabaseBootstrap(Path.of("unused"), Path.of("unused"), 59999);

        assertThat(bootstrap.isPortInUse(59999)).isFalse();
    }

    @Test
    void isPortInUse_trueWhenSomethingIsAlreadyBoundToIt() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("localhost"))) {
            int port = occupied.getLocalPort();
            PortableDatabaseBootstrap bootstrap =
                    new PortableDatabaseBootstrap(Path.of("unused"), Path.of("unused"), port);

            assertThat(bootstrap.isPortInUse(port)).isTrue();
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=PortableDatabaseBootstrapTest`
Expected: FAIL — `PortableDatabaseBootstrap` does not exist yet.

- [ ] **Step 3: Write the implementation**

`PortableDatabaseException.java`:

```java
package com.vanter.ember.hub.bootstrap;

/** Anything that stops the portable local Postgres from being ready to accept connections. */
public class PortableDatabaseException extends Exception {

    public PortableDatabaseException(String message) {
        super(message);
    }

    public PortableDatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

`PortableDatabaseBootstrap.java`:

```java
package com.vanter.ember.hub.bootstrap;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Starts the portable, unpackaged Postgres binaries (see spec §2.3) before Spring's own
 * DataSource ever tries to connect. Covers the three boot-error scenarios spec §4 calls out:
 * port already in use, an empty data directory (first run — runs {@code initdb}), and a data
 * directory {@code pg_ctl} refuses to start (corruption or any other reason) — each gets a
 * distinct, actionable error message rather than a generic stack trace.
 */
public class PortableDatabaseBootstrap {

    private final Path dataDir;
    private final Path postgresBinDir;
    private final int port;

    public PortableDatabaseBootstrap(Path dataDir, Path postgresBinDir, int port) {
        this.dataDir = dataDir;
        this.postgresBinDir = postgresBinDir;
        this.port = port;
    }

    public void ensureRunning() throws PortableDatabaseException {
        if (isPortInUse(port)) {
            throw new PortableDatabaseException(
                    "El puerto " + port + " ya está en uso. Cierra la otra aplicación que lo está "
                            + "usando (o revisa si Ember Hub ya está corriendo) e intenta de nuevo.");
        }
        if (!Files.exists(dataDir.resolve("PG_VERSION"))) {
            initializeDataDirectory();
        }
        startServer();
    }

    boolean isPortInUse(int port) {
        try (ServerSocket ignored = new ServerSocket(port, 1, InetAddress.getByName("localhost"))) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private void initializeDataDirectory() throws PortableDatabaseException {
        Path pwFile = null;
        try {
            Files.createDirectories(dataDir);
            pwFile = Files.createTempFile("ember-hub-initdb", ".txt");
            Files.writeString(pwFile, "ember");

            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("initdb").toString(),
                    "-D", dataDir.toString(),
                    "-U", "ember",
                    "--pwfile", pwFile.toString(),
                    "-E", "UTF8")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PortableDatabaseException(
                        "No se pudo inicializar la base de datos local. Detalle:\n" + output);
            }
        } catch (IOException e) {
            throw new PortableDatabaseException("Error inicializando la base de datos local.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error inicializando la base de datos local.", e);
        } finally {
            if (pwFile != null) {
                try {
                    Files.deleteIfExists(pwFile);
                } catch (IOException ignored) {
                    // Best-effort cleanup of a temp file containing a throwaway local password.
                }
            }
        }
    }

    private void startServer() throws PortableDatabaseException {
        Path logFile = dataDir.resolveSibling("postgres.log");
        try {
            Process process = new ProcessBuilder(
                    postgresBinDir.resolve("pg_ctl").toString(),
                    "start",
                    "-D", dataDir.toString(),
                    "-l", logFile.toString(),
                    "-o", "-p " + port,
                    "-w")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new PortableDatabaseException(
                        "La base de datos local no pudo arrancar (posible corrupción de datos). "
                                + "Revisa el log en " + logFile + ":\n" + output + "\n"
                                + readLogTail(logFile));
            }
        } catch (IOException e) {
            throw new PortableDatabaseException("Error arrancando la base de datos local.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PortableDatabaseException("Error arrancando la base de datos local.", e);
        }
    }

    private String readLogTail(Path logFile) {
        try {
            List<String> lines = Files.readAllLines(logFile);
            int from = Math.max(0, lines.size() - 20);
            return String.join("\n", lines.subList(from, lines.size()));
        } catch (IOException e) {
            return "";
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=PortableDatabaseBootstrapTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/bootstrap/ backend/src/test/java/com/vanter/ember/hub/bootstrap/
git commit -m "feat(hub): add portable postgres bootstrap with actionable boot errors"
```

---

### Task 7: Wire pre-Spring bootstrap into `EmberApplication.main`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/EmberApplication.java`
- Create: `backend/src/main/resources/application-hub.yml`

**Interfaces:**
- Consumes: `HubProperties` (Task 1), `LicenseService`/`LicenseKeyParser`/`HardwareFingerprintService`/`HubStateStore`/`InvalidLicenseException` (Tasks 2–5), `PortableDatabaseBootstrap`/`PortableDatabaseException` (Task 6).

This task has no unit test of its own — `main()` methods that call `System.exit` aren't practically unit-testable, and the real behavior (does Windows Postgres actually start?) can only be verified manually (Task 11). What's verified here is that the **existing** test suite still passes unmodified, proving this change doesn't affect the non-hub (default-profile) boot path at all.

- [ ] **Step 1: Modify `EmberApplication.java`**

Replace its full contents:

```java
package com.vanter.ember;

import com.vanter.ember.hub.bootstrap.PortableDatabaseBootstrap;
import com.vanter.ember.hub.bootstrap.PortableDatabaseException;
import com.vanter.ember.hub.config.HubProperties;
import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.hub.license.LicenseService;
import java.security.PublicKey;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EmberApplication {

    public static void main(String[] args) {
        if (isHubProfile()) {
            bootstrapHub();
        }
        SpringApplication.run(EmberApplication.class, args);
    }

    /**
     * Reads the profile straight from the environment, not from Spring — Spring hasn't started
     * yet. This is deliberately the ONLY place that matters: Spring's own DataSource
     * autoconfiguration connects to Postgres during context refresh, which runs before any
     * {@code ApplicationRunner} — too late to start portable Postgres from inside the Spring
     * lifecycle.
     */
    private static boolean isHubProfile() {
        String profiles = System.getenv("SPRING_PROFILES_ACTIVE");
        if (profiles == null) {
            profiles = System.getProperty("spring.profiles.active", "");
        }
        return Arrays.asList(profiles.split(",")).contains("hub");
    }

    private static void bootstrapHub() {
        HubProperties properties = HubProperties.fromEnvironment();

        try {
            PublicKey publicKey = LicenseKeyParser.loadPublicKey(properties.publicKeyFile());
            LicenseService licenseService = new LicenseService(
                    properties.licenseFile(),
                    publicKey,
                    new LicenseKeyParser(),
                    new HardwareFingerprintService(),
                    new HubStateStore(properties.stateFile()));
            licenseService.validateOrActivate();
        } catch (InvalidLicenseException e) {
            failToStart(e.getMessage());
        }

        PortableDatabaseBootstrap dbBootstrap = new PortableDatabaseBootstrap(
                properties.dataDir(), properties.postgresBinDir(), properties.postgresPort());
        try {
            dbBootstrap.ensureRunning();
        } catch (PortableDatabaseException e) {
            failToStart(e.getMessage());
        }
    }

    private static void failToStart(String message) {
        System.err.println("Ember Hub no puede iniciar: " + message);
        System.exit(1);
    }
}
```

- [ ] **Step 2: Add `application-hub.yml`**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:${EMBER_HUB_POSTGRES_PORT:5432}/ember
    username: ember
    password: ember
  jpa:
    hibernate:
      ddl-auto: validate

server:
  port: ${EMBER_HUB_SERVER_PORT:8080}
```

- [ ] **Step 3: Run the full test suite to confirm the default (non-hub) boot path is unaffected**

Run: `cd backend && ./mvnw test`
Expected: PASS, same count as before this task — nothing under `SPRING_PROFILES_ACTIVE=hub` runs during the test suite, so `isHubProfile()` is always false there and `bootstrapHub()` never executes.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/EmberApplication.java backend/src/main/resources/application-hub.yml
git commit -m "feat(hub): bootstrap license and portable postgres before spring starts"
```

---

### Task 8: `HubBeansConfig` — expose Hub services as Spring beans

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/config/HubBeansConfigTest.java`

**Interfaces:**
- Produces: Spring `@Bean`s for `HubProperties`, `HardwareFingerprintService`, `HubStateStore`, `LicenseKeyParser`, `LicenseService` — all `@Profile("hub")`, so none of them exist in the app context under any other profile. Consumed by `GracePeriodInterceptor` (Task 9) and `HubTrayIcon` (Task 10).

- [ ] **Step 1: Write the failing test**

`HubProperties.fromEnvironment()` reads real OS environment variables, not Spring-bound
properties (Task 1's deliberate design — it has to work before Spring exists), so this test
can't redirect it to a temp file via `ApplicationContextRunner.withPropertyValues(...)`. It
therefore only asserts on the beans that don't depend on a real `license.key`/public-key file
being present; `licenseService`'s own `@Bean` method is exercised for real in Task 11's manual
verification, against actual files on disk.

```java
package com.vanter.ember.hub.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseKeyParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HubBeansConfigTest {

    @Test
    void hubProfile_registersFingerprintAndStateBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(HubBeansConfig.class)
                .withPropertyValues("spring.profiles.active=hub")
                .run(context -> {
                    assertThat(context).hasSingleBean(HubProperties.class);
                    assertThat(context).hasSingleBean(HardwareFingerprintService.class);
                    assertThat(context).hasSingleBean(HubStateStore.class);
                    assertThat(context).hasSingleBean(LicenseKeyParser.class);
                });
    }

    @Test
    void defaultProfile_registersNoHubBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(HubBeansConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(HubProperties.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubBeansConfigTest`
Expected: FAIL — `HubBeansConfig` does not exist yet.

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.hub.config;

import com.vanter.ember.hub.license.HardwareFingerprintService;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.InvalidLicenseException;
import com.vanter.ember.hub.license.LicenseKeyParser;
import com.vanter.ember.hub.license.LicenseService;
import java.security.PublicKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("hub")
public class HubBeansConfig {

    @Bean
    public HubProperties hubProperties() {
        return HubProperties.fromEnvironment();
    }

    @Bean
    public HardwareFingerprintService hardwareFingerprintService() {
        return new HardwareFingerprintService();
    }

    @Bean
    public HubStateStore hubStateStore(HubProperties properties) {
        return new HubStateStore(properties.stateFile());
    }

    @Bean
    public LicenseKeyParser licenseKeyParser() {
        return new LicenseKeyParser();
    }

    @Bean
    public LicenseService licenseService(
            HubProperties properties,
            LicenseKeyParser parser,
            HardwareFingerprintService fingerprintService,
            HubStateStore stateStore)
            throws InvalidLicenseException {
        PublicKey publicKey = LicenseKeyParser.loadPublicKey(properties.publicKeyFile());
        return new LicenseService(properties.licenseFile(), publicKey, parser, fingerprintService, stateStore);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubBeansConfigTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/config/HubBeansConfig.java backend/src/test/java/com/vanter/ember/hub/config/HubBeansConfigTest.java
git commit -m "feat(hub): expose hub license services as profile-gated spring beans"
```

---

### Task 9: `GracePeriodInterceptor`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java`
- Create: `backend/src/main/java/com/vanter/ember/hub/config/HubWebConfig.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java`

**Interfaces:**
- Consumes: `LicenseService.isWithinGracePeriod`, `HubStateStore.load` (Tasks 4–5).
- Produces: a `HandlerInterceptor` blocking write requests to order/payment-creating endpoints with `403` once the grace period (spec §2.8) has lapsed, registered only under the `hub` profile.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.license;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GracePeriodInterceptorTest {

    @Test
    void preHandle_allowsRequestWithinGracePeriod() throws Exception {
        HubState fresh = new HubState("fp", UUID.randomUUID(), Instant.now());
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(fresh));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(fresh)).thenReturn(true);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        boolean allowed = interceptor.preHandle(
                mock(HttpServletRequest.class), mock(HttpServletResponse.class), new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void preHandle_blocksRequestPastGracePeriod() throws Exception {
        HubState stale = new HubState("fp", UUID.randomUUID(), Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS));
        HubStateStore stateStore = mock(HubStateStore.class);
        when(stateStore.load()).thenReturn(Optional.of(stale));
        LicenseService licenseService = mock(LicenseService.class);
        when(licenseService.isWithinGracePeriod(stale)).thenReturn(false);
        GracePeriodInterceptor interceptor = new GracePeriodInterceptor(licenseService, stateStore);

        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        boolean allowed = interceptor.preHandle(mock(HttpServletRequest.class), response, new Object());

        assertThat(allowed).isFalse();
        org.mockito.Mockito.verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertThat(body.toString()).contains("license_grace_period_expired");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=GracePeriodInterceptorTest`
Expected: FAIL — `GracePeriodInterceptor` does not exist yet.

- [ ] **Step 3: Write the implementation**

`GracePeriodInterceptor.java`:

```java
package com.vanter.ember.hub.license;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks order/payment-creating requests once the license grace period (spec §2.8) has lapsed —
 * read-only endpoints are untouched, and no local data is ever deleted; this only stops NEW
 * writes until the Hub reconnects and a heartbeat succeeds again.
 */
public class GracePeriodInterceptor implements HandlerInterceptor {

    private final LicenseService licenseService;
    private final HubStateStore stateStore;

    public GracePeriodInterceptor(LicenseService licenseService, HubStateStore stateStore) {
        this.licenseService = licenseService;
        this.stateStore = stateStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HubState state = stateStore.load()
                .orElseThrow(() -> new IllegalStateException(
                        "hub-state.json missing after startup license validation"));
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
    }
}
```

`HubWebConfig.java` — the interceptor is built once in the constructor (not as a `@Bean`-method
parameter injected at `addInterceptors` call time, which `WebMvcConfigurer` doesn't support) and
reused for both the bean registration and the interceptor registry:

```java
package com.vanter.ember.hub.config;

import com.vanter.ember.hub.license.GracePeriodInterceptor;
import com.vanter.ember.hub.license.HubStateStore;
import com.vanter.ember.hub.license.LicenseService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("hub")
public class HubWebConfig implements WebMvcConfigurer {

    private final GracePeriodInterceptor gracePeriodInterceptor;

    public HubWebConfig(LicenseService licenseService, HubStateStore stateStore) {
        this.gracePeriodInterceptor = new GracePeriodInterceptor(licenseService, stateStore);
    }

    @Bean
    public GracePeriodInterceptor gracePeriodInterceptor() {
        return gracePeriodInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(gracePeriodInterceptor)
                .addPathPatterns("/sessions/*/items", "/sessions/*/participants/*/confirm", "/billing/**");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=GracePeriodInterceptorTest`
Expected: PASS

- [ ] **Step 5: Run the full suite to confirm the interceptor doesn't affect the default profile**

Run: `cd backend && ./mvnw test`
Expected: PASS, same count — `HubWebConfig` is `@Profile("hub")`, never loaded by any existing test's context.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/license/GracePeriodInterceptor.java backend/src/main/java/com/vanter/ember/hub/config/HubWebConfig.java backend/src/test/java/com/vanter/ember/hub/license/GracePeriodInterceptorTest.java
git commit -m "feat(hub): block order/payment writes once the license grace period lapses"
```

---

### Task 10: `HubTrayIcon`

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java`

**Interfaces:**
- Consumes: nothing from earlier tasks — a standalone `ApplicationListener` that fires once Spring is fully up.

No automated test: `java.awt.SystemTray` requires a real desktop session (headless CI has none — `SystemTray.isSupported()` returns `false` there, which is exactly the fallback path this task codes for, not something worth asserting on in a unit test). Manually verified in Task 11.

- [ ] **Step 1: Write the implementation**

```java
package com.vanter.ember.hub.tray;

import java.awt.AWTException;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Minimal v1 tray icon: presence + a way to open/quit, per spec §2.3. Auto-updater notifications
 * (spec §2.10) are HUB-04's job, once there's a version to compare against from HUB-02's sync
 * response.
 */
@Component
@Profile("hub")
public class HubTrayIcon {

    private static final Logger log = LoggerFactory.getLogger(HubTrayIcon.class);

    @EventListener(ApplicationReadyEvent.class)
    public void show() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray no soportado en este sistema; Ember Hub sigue corriendo sin ícono.");
            return;
        }
        try {
            SystemTray tray = SystemTray.getSystemTray();

            PopupMenu menu = new PopupMenu();
            MenuItem open = new MenuItem("Abrir Ember Hub");
            open.addActionListener(e -> openBrowser());
            menu.add(open);
            MenuItem exit = new MenuItem("Salir");
            exit.addActionListener(e -> System.exit(0));
            menu.add(exit);

            TrayIcon trayIcon = new TrayIcon(createIcon(), "Ember Hub", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> openBrowser());
            tray.add(trayIcon);
        } catch (AWTException e) {
            log.error("No se pudo mostrar el ícono de bandeja del sistema", e);
        }
    }

    /**
     * Drawn at runtime instead of bundling a PNG resource — a real designed icon is a packaging
     * polish concern (HUB-03), not a blocker for the tray mechanism itself.
     */
    private Image createIcon() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0x8c, 0x17, 0x17));
        g.fillOval(0, 0, 16, 16);
        g.dispose();
        return image;
    }

    private void openBrowser() {
        try {
            Desktop.getDesktop().browse(new URI("http://localhost:8080"));
        } catch (IOException | URISyntaxException e) {
            log.error("No se pudo abrir el navegador", e);
        }
    }
}
```

- [ ] **Step 2: Run the full suite to confirm this compiles and doesn't break anything under the default profile**

Run: `cd backend && ./mvnw test`
Expected: PASS, same count — `@Profile("hub")` again.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/hub/tray/HubTrayIcon.java
git commit -m "feat(hub): add minimal system tray icon"
```

---

### Task 11: Manual end-to-end verification (Windows, real portable Postgres)

**Files:** none — verification only. This is the one part of HUB-01 no automated test can cover (spec §4 already flags this: "no hay CI que cubra esto, es verificación manual").

- [ ] **Step 1: Get a real portable Postgres distribution**

Download the official EDB "binaries only" zip for Postgres 16 (matching the version already used elsewhere in this project — see `docker-compose.yml`'s `postgres:16` image) for Windows x64. Extract its `bin/` folder somewhere, e.g. `C:\ember-hub-test\postgres\bin`.

- [ ] **Step 2: Generate a real test keypair and license.key**

```powershell
# Generate an RSA keypair for testing (NOT the real Vanter signing key — that's a separate,
# carefully-guarded secret to generate later, outside this test).
openssl genrsa -out test-private.pem 2048
openssl rsa -in test-private.pem -pubout -outform DER -out hub-public-key.der
```

Write a tiny throwaway Java program (or a JUnit `@Disabled` scratch test, deleted after use) that calls `LicenseKeyParser.sign(new LicenseKey(UUID.randomUUID(), Instant.now()), privateKey)` using `test-private.pem`, and save its output to `license.key`.

- [ ] **Step 3: Boot Ember Hub with the `hub` profile**

```powershell
$env:SPRING_PROFILES_ACTIVE = "hub"
$env:EMBER_HUB_DATA_DIR = "C:\ember-hub-test\data"
$env:EMBER_HUB_POSTGRES_BIN_DIR = "C:\ember-hub-test\postgres\bin"
$env:EMBER_HUB_LICENSE_FILE = "C:\ember-hub-test\license.key"
$env:EMBER_HUB_PUBLIC_KEY_FILE = "C:\ember-hub-test\hub-public-key.der"
$env:EMBER_HUB_STATE_FILE = "C:\ember-hub-test\hub-state.json"
cd backend
.\mvnw.cmd spring-boot:run
```

Expected: console shows `initdb` running (first boot only), then Postgres starting, then the normal Spring Boot startup log, ending in `Started EmberApplication`. A red icon appears in the Windows system tray. `hub-state.json` now exists at the configured path with a `hardwareFingerprint`, this run's `restaurantId`, and a recent `lastHeartbeatAt`.

- [ ] **Step 4: Verify grace period enforcement**

Manually edit `hub-state.json`, setting `lastHeartbeatAt` to a timestamp more than 4 days in the past. Restart the app (same command as Step 3). Try creating a session/adding an item through the frontend (or `curl`) — expect `403` with `license_grace_period_expired`. Restore `lastHeartbeatAt` to now and confirm requests succeed again.

- [ ] **Step 5: Verify the three boot-error paths**

- **Port in use:** start any other process listening on 5432 (or run Ember Hub twice in a row without stopping the first), confirm the clear "puerto ya está en uso" message and a non-zero exit — not a raw stack trace.
- **Invalid license:** point `EMBER_HUB_LICENSE_FILE` at a nonexistent path, confirm the "No se encontró license.key" message.
- **Wrong hardware:** after a successful first activation, manually edit `hub-state.json`'s `hardwareFingerprint` to a bogus value, restart, confirm the "ya está activada en otra PC" message.

- [ ] **Step 6: Record the results**

Update `PROGRESS.md` with a bullet noting HUB-01 is implemented and manually verified against a real Windows portable Postgres, including which Postgres/EDB build version was used (for HUB-03 to bundle the same one later).
