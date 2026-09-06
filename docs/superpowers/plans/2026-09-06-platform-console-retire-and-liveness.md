# Platform Console — Retire Suspended Tenants + Hub Liveness — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the `/platform` operator console the ability to soft-delete a *suspended* restaurant (reversibly) and to see whether each customer's Hub is alive, driven by persisted heartbeat timestamps.

**Architecture:** A new `RestaurantStatus.DELETED` value carries the soft-delete state (every existing `!= ACTIVE` gate already covers it); `deleted_at`/`deleted_by` are audit metadata. Each verified Hub heartbeat writes `hub_activations.last_heartbeat_at`/`last_heartbeat_ip` via a targeted `@Modifying` update (best-effort — a write failure never fails the heartbeat). The console derives `HubStatus` (`NEVER`/`ONLINE`/`STALE`/`OFFLINE`) from `last_heartbeat_at` vs now. One Flyway migration `V8`.

**Tech Stack:** Java 17, Spring Boot 3.5.14, Spring Data JPA, Flyway, PostgreSQL; JUnit 5 + Mockito + `@DataJpaTest` + `@WebMvcTest`. Frontend: React 19 + TypeScript + Vite, TanStack Query 5, Vitest + Testing Library.

**Spec:** `docs/superpowers/specs/2026-09-06-platform-console-retire-and-liveness-design.md` — read it alongside this plan.

## Global Constraints

- Branch: `spec/platform-console-retire-liveness`, off `main`. It already contains the spec commit.
- Backend build/test command (canonical, per root `CLAUDE.md` §2): `cd backend && ./mvnw test`. Do **not** use bare `mvn` or `tsc`.
- Frontend: `cd frontend && pnpm run build` (`tsc -b && vite build`) and `pnpm run lint` must both be clean. Tests: `pnpm run test:run`.
- Commits: Conventional Commits, lowercase, one squashed atomic commit per task. **No** `Co-authored-by:`, `Signed-off-by:`, or AI-signature trailers. Stage only the files the task touched (`git add <paths>`) — never `git add -A`/`git add .`.
- Reports: after the final task, one report in `reports/` — next free number is **384** (`main` has through 383). Sequential naming per `CLAUDE.md` §4.
- `PlatformAuditLog` convention: `operatorId`/`operatorEmail` are write-time snapshots; `restaurantId` has no FK.
- The console frontend (`frontend/src/pages/console/**`, `platformApi.ts`) has **no i18n layer** — all strings are inline Spanish. Match that; do not add i18n (that is piece D).
- `GlobalExceptionHandler` maps **both** `IllegalArgumentException` and `IllegalStateException` to **HTTP 409**. Use `IllegalStateException` for wrong-state transition guards; `ResourceNotFoundException` → 404.
- Flyway baseline caveat: an environment whose `flyway_schema_history` is a single BASELINE row at `version 15` (local dev, likely prod) **skips `V8`**. This plan's `@DataJpaTest`s run against a fresh schema so they are unaffected; the deployment note in the spec §4.1 / §6 covers the manual `ALTER TABLE`.

---

## File Structure

**Backend — create:**
- `backend/src/main/resources/db/migration/V8__restaurant_soft_delete_and_hub_heartbeat.sql`
- `backend/src/main/java/com/vanter/ember/platform/model/dto/HubStatus.java` — the derived enum + `from(Instant lastHeartbeatAt, Instant now)` factory.
- `backend/src/test/java/com/vanter/ember/platform/model/dto/HubStatusTest.java`
- `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositorySoftDeleteTest.java` (`@DataJpaTest`)
- `backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationHeartbeatTest.java` (`@DataJpaTest`)

**Backend — modify:**
- `restaurant/model/RestaurantStatus.java` — add `DELETED`.
- `restaurant/model/Restaurant.java` — add `deletedAt`, `deletedBy`.
- `restaurant/repository/RestaurantRepository.java` — add `findByStatusNot(...)`.
- `licensing/model/HubActivation.java` — add `lastHeartbeatAt`, `lastHeartbeatIp`.
- `licensing/repository/HubActivationRepository.java` — add `recordHeartbeat(...)`, `findByRestaurantIdIn(...)`.
- `licensing/service/HubHeartbeatService.java` — accept caller IP, record heartbeat best-effort.
- `licensing/controller/HubHeartbeatController.java` — resolve + pass caller IP.
- `platform/model/dto/PlatformRestaurantSummaryResponse.java` / `PlatformRestaurantDetailResponse.java` — add Hub fields.
- `platform/service/PlatformRestaurantService.java` — `delete`, `restore`, `updateStatus` guards, populate Hub fields.
- `platform/controller/PlatformRestaurantController.java` — `DELETE`, `POST /{id}/restore`, `?includeDeleted`.
- Their test files (all under `backend/src/test/java/com/vanter/ember/...`).

**Frontend — modify:**
- `frontend/src/lib/platformApi.ts` — `DELETED` in status unions, Hub fields on the interfaces, `deleteRestaurant`, `restoreRestaurant`, `getAll(page, includeDeleted)`.
- `frontend/src/pages/console/ConsoleRestaurants.tsx` — Hub column, "Ver eliminados" checkbox.
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx` — Hub panel, delete/restore buttons + confirm dialog.

**Frontend — create:**
- `frontend/src/pages/console/ConsoleRestaurants.test.tsx`
- `frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx`

---

## Task 1: Migration `V8` + entity fields + `RestaurantStatus.DELETED`

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__restaurant_soft_delete_and_hub_heartbeat.sql`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/model/RestaurantStatus.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/model/Restaurant.java`
- Modify: `backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/licensing/model/HubActivation.java`
- Test: `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositorySoftDeleteTest.java`

**Interfaces:**
- Produces:
  - `RestaurantStatus.DELETED` (enum constant).
  - `Restaurant#getDeletedAt(): Instant`, `Restaurant#setDeletedAt(Instant)`, `Restaurant#getDeletedBy(): UUID`, `Restaurant#setDeletedBy(UUID)` (Lombok `@Data`).
  - `RestaurantRepository#findByStatusNot(RestaurantStatus status, Pageable pageable): Page<Restaurant>`.
  - `HubActivation#getLastHeartbeatAt(): Instant` / `setLastHeartbeatAt(Instant)`, `HubActivation#getLastHeartbeatIp(): String` / `setLastHeartbeatIp(String)`.

- [ ] **Step 1: Write the migration**

Create `V8__restaurant_soft_delete_and_hub_heartbeat.sql`:

```sql
-- Piece B: soft-delete a suspended tenant (reversible). DELETED joins the
-- RestaurantStatus enum as another "not ACTIVE" value; deleted_at/deleted_by are audit metadata.
ALTER TABLE restaurants
    ADD COLUMN deleted_at timestamp(6) with time zone,
    ADD COLUMN deleted_by uuid;

-- Piece C: cloud records each verified Hub heartbeat so the console can show liveness.
ALTER TABLE hub_activations
    ADD COLUMN last_heartbeat_at timestamp(6) with time zone,
    ADD COLUMN last_heartbeat_ip varchar(45);
```

- [ ] **Step 2: Add the enum value**

`RestaurantStatus.java`:

```java
public enum RestaurantStatus {
    ACTIVE, SUSPENDED, INACTIVE, DELETED
}
```

- [ ] **Step 3: Add the entity fields**

In `Restaurant.java`, after the `createdAt` field (before `prePersist`):

```java
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private UUID deletedBy;
```

In `HubActivation.java`, after the `activatedAt` field:

```java
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_heartbeat_ip")
    private String lastHeartbeatIp;
```

- [ ] **Step 4: Add the repository finder**

In `RestaurantRepository.java` add the imports `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`, `com.vanter.ember.restaurant.model.RestaurantStatus`, then:

```java
    /** Console directory default view: every restaurant except soft-deleted ones. */
    Page<Restaurant> findByStatusNot(RestaurantStatus status, Pageable pageable);
```

- [ ] **Step 5: Write the failing `@DataJpaTest`**

Create `RestaurantRepositorySoftDeleteTest.java`:

```java
package com.vanter.ember.restaurant.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.restaurant.model.Restaurant;
import com.vanter.ember.restaurant.model.RestaurantStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class RestaurantRepositorySoftDeleteTest {

    @Autowired RestaurantRepository restaurantRepository;

    private Restaurant persisted(String slug, RestaurantStatus status) {
        return restaurantRepository.save(Restaurant.builder().name(slug).slug(slug).status(status).build());
    }

    @Test
    void deletedColumnsRoundTrip() {
        Restaurant r = persisted("round-trip", RestaurantStatus.SUSPENDED);
        UUID operator = UUID.randomUUID();
        r.setStatus(RestaurantStatus.DELETED);
        r.setDeletedAt(Instant.parse("2026-09-06T12:00:00Z"));
        r.setDeletedBy(operator);
        restaurantRepository.saveAndFlush(r);
        restaurantRepository.findById(r.getId()).ifPresentOrElse(loaded -> {
            assertThat(loaded.getStatus()).isEqualTo(RestaurantStatus.DELETED);
            assertThat(loaded.getDeletedAt()).isEqualTo(Instant.parse("2026-09-06T12:00:00Z"));
            assertThat(loaded.getDeletedBy()).isEqualTo(operator);
        }, () -> { throw new AssertionError("restaurant not found"); });
    }

    @Test
    void findByStatusNotExcludesDeleted() {
        persisted("alive-1", RestaurantStatus.ACTIVE);
        persisted("alive-2", RestaurantStatus.SUSPENDED);
        persisted("gone", RestaurantStatus.DELETED);

        var page = restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Restaurant::getSlug)
                .containsExactlyInAnyOrder("alive-1", "alive-2");
    }
}
```

- [ ] **Step 6: Run it — verify it fails**

Run: `cd backend && ./mvnw test -Dtest=RestaurantRepositorySoftDeleteTest`
Expected: FAIL before steps 2–4 are in place (compile error on `RestaurantStatus.DELETED` / `setDeletedAt` / `findByStatusNot`). After steps 2–4, it should pass on the fresh test schema.

- [ ] **Step 7: Run it — verify it passes**

Run: `cd backend && ./mvnw test -Dtest=RestaurantRepositorySoftDeleteTest`
Expected: PASS (2 tests).

- [ ] **Step 8: Full backend suite (schema change touches `ddl-auto=validate` boot in every `@SpringBootTest`)**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V8__restaurant_soft_delete_and_hub_heartbeat.sql \
  backend/src/main/java/com/vanter/ember/restaurant/model/RestaurantStatus.java \
  backend/src/main/java/com/vanter/ember/restaurant/model/Restaurant.java \
  backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java \
  backend/src/main/java/com/vanter/ember/licensing/model/HubActivation.java \
  backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositorySoftDeleteTest.java
git commit -m "feat(platform): V8 migration — restaurant soft-delete + hub heartbeat columns"
```

---

## Task 2: `HubStatus` enum + derivation

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/platform/model/dto/HubStatus.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/model/dto/HubStatusTest.java`

**Interfaces:**
- Produces: `HubStatus` enum with values `NEVER`, `ONLINE`, `STALE`, `OFFLINE`, and
  `static HubStatus from(Instant lastHeartbeatAt, Instant now)`.

- [ ] **Step 1: Write the failing test**

Create `HubStatusTest.java`:

```java
package com.vanter.ember.platform.model.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HubStatusTest {

    private static final Instant NOW = Instant.parse("2026-09-06T12:00:00Z");

    @Test
    void nullLastHeartbeatIsNever() {
        assertThat(HubStatus.from(null, NOW)).isEqualTo(HubStatus.NEVER);
    }

    @Test
    void withinFifteenMinutesIsOnline() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofMinutes(14)), NOW)).isEqualTo(HubStatus.ONLINE);
    }

    @Test
    void betweenFifteenMinutesAndTwentyFourHoursIsStale() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofMinutes(20)), NOW)).isEqualTo(HubStatus.STALE);
        assertThat(HubStatus.from(NOW.minus(Duration.ofHours(23)), NOW)).isEqualTo(HubStatus.STALE);
    }

    @Test
    void olderThanTwentyFourHoursIsOffline() {
        assertThat(HubStatus.from(NOW.minus(Duration.ofHours(25)), NOW)).isEqualTo(HubStatus.OFFLINE);
    }
}
```

- [ ] **Step 2: Run it — verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubStatusTest`
Expected: FAIL — `HubStatus` does not exist.

- [ ] **Step 3: Implement `HubStatus`**

```java
package com.vanter.ember.platform.model.dto;

import java.time.Duration;
import java.time.Instant;

/**
 * Console-facing liveness of a customer's Hub, derived from
 * {@code hub_activations.last_heartbeat_at}. The Hub beats every 5 minutes, so ONLINE tolerates
 * two missed beats. Never persisted — computed on read.
 */
public enum HubStatus {
    NEVER, ONLINE, STALE, OFFLINE;

    private static final Duration ONLINE_WITHIN = Duration.ofMinutes(15);
    private static final Duration OFFLINE_AFTER = Duration.ofHours(24);

    public static HubStatus from(Instant lastHeartbeatAt, Instant now) {
        if (lastHeartbeatAt == null) {
            return NEVER;
        }
        Duration since = Duration.between(lastHeartbeatAt, now);
        if (since.compareTo(ONLINE_WITHIN) <= 0) {
            return ONLINE;
        }
        if (since.compareTo(OFFLINE_AFTER) <= 0) {
            return STALE;
        }
        return OFFLINE;
    }
}
```

- [ ] **Step 4: Run it — verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubStatusTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/model/dto/HubStatus.java \
  backend/src/test/java/com/vanter/ember/platform/model/dto/HubStatusTest.java
git commit -m "feat(platform): HubStatus enum derived from last heartbeat"
```

---

## Task 3: Heartbeat persistence — repo update + service + controller IP

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/licensing/repository/HubActivationRepository.java`
- Modify: `backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java`
- Modify: `backend/src/main/java/com/vanter/ember/licensing/controller/HubHeartbeatController.java`
- Test: `backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java` (modify)
- Test: `backend/src/test/java/com/vanter/ember/licensing/controller/HubHeartbeatControllerTest.java` (modify)
- Test: `backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationHeartbeatTest.java` (create)

**Interfaces:**
- Consumes: `HubActivation#setLastHeartbeatAt` / `setLastHeartbeatIp` (Task 1).
- Produces:
  - `HubActivationRepository#recordHeartbeat(UUID restaurantId, Instant at, String ip): int` (`@Modifying`).
  - `HubActivationRepository#findByRestaurantIdIn(Collection<UUID> ids): List<HubActivation>`.
  - `HubHeartbeatService#heartbeat(HubHeartbeatRequest request, String callerIp): HubHeartbeatResponse` — **signature change** (was `heartbeat(HubHeartbeatRequest)`).

- [ ] **Step 1: Add the repository methods**

In `HubActivationRepository.java` add imports `java.time.Instant`, `java.util.Collection`, `java.util.List`, `org.springframework.data.jpa.repository.Modifying`, `org.springframework.data.jpa.repository.Query`, `org.springframework.data.repository.query.Param`, `org.springframework.transaction.annotation.Transactional`, then:

```java
    List<HubActivation> findByRestaurantIdIn(Collection<UUID> restaurantIds);

    /**
     * Best-effort liveness stamp written on every verified heartbeat. A targeted UPDATE (not an
     * entity save) so it neither reloads the row nor bumps a version. Returns rows affected
     * (0 if the restaurant has no activation row — a caller can ignore that).
     */
    @Modifying
    @Transactional
    @Query("update HubActivation h set h.lastHeartbeatAt = :at, h.lastHeartbeatIp = :ip "
            + "where h.restaurantId = :restaurantId")
    int recordHeartbeat(@Param("restaurantId") UUID restaurantId,
                        @Param("at") Instant at,
                        @Param("ip") String ip);
```

- [ ] **Step 2: Write the failing `@DataJpaTest`**

Create `HubActivationHeartbeatTest.java`:

```java
package com.vanter.ember.licensing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.licensing.model.HubActivation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class HubActivationHeartbeatTest {

    @Autowired HubActivationRepository repository;

    private UUID activated(String fp) {
        UUID restaurantId = UUID.randomUUID();
        repository.save(HubActivation.builder()
                .restaurantId(restaurantId).hardwareFingerprint(fp).activatedAt(Instant.now()).build());
        return restaurantId;
    }

    @Test
    void recordHeartbeatUpdatesOnlyThatRow() {
        UUID a = activated("fp-a");
        UUID b = activated("fp-b");
        Instant at = Instant.parse("2026-09-06T12:00:00Z");

        int rows = repository.recordHeartbeat(a, at, "203.0.113.7");

        assertThat(rows).isEqualTo(1);
        assertThat(repository.findByRestaurantId(a)).get()
                .satisfies(h -> {
                    assertThat(h.getLastHeartbeatAt()).isEqualTo(at);
                    assertThat(h.getLastHeartbeatIp()).isEqualTo("203.0.113.7");
                });
        assertThat(repository.findByRestaurantId(b)).get()
                .satisfies(h -> assertThat(h.getLastHeartbeatAt()).isNull());
    }

    @Test
    void recordHeartbeatReturnsZeroForUnknownRestaurant() {
        assertThat(repository.recordHeartbeat(UUID.randomUUID(), Instant.now(), "203.0.113.7")).isZero();
    }

    @Test
    void findByRestaurantIdInReturnsMatches() {
        UUID a = activated("fp-a");
        activated("fp-b");
        List<HubActivation> found = repository.findByRestaurantIdIn(List.of(a));
        assertThat(found).extracting(HubActivation::getRestaurantId).containsExactly(a);
    }
}
```

- [ ] **Step 3: Run it — verify it fails, then passes**

Run: `cd backend && ./mvnw test -Dtest=HubActivationHeartbeatTest`
Expected: FAIL before Step 1 compiles in; PASS (3 tests) after.

- [ ] **Step 4: Change `HubHeartbeatService` — write the failing service test first**

In `HubHeartbeatServiceTest.java`:
- Change every `service.heartbeat(request(FP))` call to `service.heartbeat(request(FP), "203.0.113.7")` and every `service.heartbeat(request("a-different-fp"))` / `service.heartbeat(r)` similarly (add `, "203.0.113.7"`).
- Add these tests:

```java
    @Test
    void heartbeat_activeRestaurant_recordsHeartbeat() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));

        service.heartbeat(request(FP), "203.0.113.7");

        org.mockito.Mockito.verify(hubActivationRepository)
                .recordHeartbeat(org.mockito.ArgumentMatchers.eq(restaurantId),
                        org.mockito.ArgumentMatchers.any(java.time.Instant.class),
                        org.mockito.ArgumentMatchers.eq("203.0.113.7"));
    }

    @Test
    void heartbeat_fingerprintMismatch_doesNotRecord() {
        assertThatThrownBy(() -> service.heartbeat(request("a-different-fp"), "203.0.113.7"))
                .isInstanceOf(InvalidLicenseException.class);
        org.mockito.Mockito.verify(hubActivationRepository, org.mockito.Mockito.never())
                .recordHeartbeat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void heartbeat_recordFailureIsSwallowed() throws InvalidLicenseException {
        when(restaurantRepository.findById(restaurantId)).thenReturn(Optional.of(
                restaurantWithStatus(RestaurantStatus.ACTIVE)));
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("db down"))
                .when(hubActivationRepository).recordHeartbeat(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());

        HubHeartbeatResponse response = service.heartbeat(request(FP), "203.0.113.7");

        assertThat(response.getStatus()).isEqualTo("OK");
    }
```

- [ ] **Step 5: Run the service test — verify it fails**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatServiceTest`
Expected: FAIL — `heartbeat(request, String)` signature does not exist / no `recordHeartbeat` interaction.

- [ ] **Step 6: Implement the service change**

In `HubHeartbeatService.java`:
- Add imports `java.time.Instant` (already present), `org.slf4j.Logger`, `org.slf4j.LoggerFactory`, `org.springframework.dao.DataAccessException`.
- Add a logger field: `private static final Logger log = LoggerFactory.getLogger(HubHeartbeatService.class);`
- Change the method signature and add the record call:

```java
    public HubHeartbeatResponse heartbeat(HubHeartbeatRequest request, String callerIp)
            throws InvalidLicenseException {
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

        recordHeartbeatQuietly(restaurantId, callerIp);

        String status = restaurant.getStatus() == RestaurantStatus.ACTIVE ? "OK" : "SUSPENDED";

        return HubHeartbeatResponse.builder()
                .status(status)
                .serverTime(Instant.now())
                .latestVersion(latestVersion == null || latestVersion.isBlank() ? null : latestVersion)
                .build();
    }

    /** Liveness telemetry: a write failure here must not turn a valid heartbeat into an error. */
    private void recordHeartbeatQuietly(UUID restaurantId, String callerIp) {
        try {
            hubActivationRepository.recordHeartbeat(restaurantId, Instant.now(), callerIp);
        } catch (DataAccessException e) {
            log.warn("Could not record heartbeat for restaurant {}: {}", restaurantId, e.getMessage());
        }
    }
```

- [ ] **Step 7: Run the service test — verify it passes**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatServiceTest`
Expected: PASS (all — 6 original with updated calls + 3 new).

- [ ] **Step 8: Update `HubHeartbeatController` + its test**

In `HubHeartbeatController.java`:
- Add imports `jakarta.servlet.http.HttpServletRequest`, `org.springframework.util.StringUtils`.
- Change the handler:

```java
    @PostMapping
    public ResponseEntity<HubHeartbeatResponse> heartbeat(
            @Valid @RequestBody HubHeartbeatRequest request, HttpServletRequest servletRequest)
            throws InvalidLicenseException {
        return ResponseEntity.ok(hubHeartbeatService.heartbeat(request, callerIp(servletRequest)));
    }

    /**
     * Best-effort client IP for liveness telemetry only (never a security decision), so no
     * trusted-proxy validation: prefer Cloudflare's header (prod is behind CF), then the first
     * X-Forwarded-For hop, then the socket peer.
     */
    private static String callerIp(HttpServletRequest request) {
        String cf = request.getHeader("CF-Connecting-IP");
        if (StringUtils.hasText(cf)) {
            return cf.trim();
        }
        String xff = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
```

In `HubHeartbeatControllerTest.java`:
- Change `when(hubHeartbeatService.heartbeat(any()))` → `when(hubHeartbeatService.heartbeat(any(), any()))` in all three tests.
- Add a test:

```java
    @Test
    void heartbeat_passesCfConnectingIpToService() throws Exception {
        when(hubHeartbeatService.heartbeat(any(), any())).thenReturn(HubHeartbeatResponse.builder()
                .status("OK").serverTime(Instant.now()).latestVersion(null).build());

        mockMvc.perform(post("/hub-heartbeat")
                        .header("CF-Connecting-IP", "198.51.100.9")
                        .header("X-Forwarded-For", "10.0.0.1, 172.16.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(hubHeartbeatService).heartbeat(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("198.51.100.9"));
    }
```

- [ ] **Step 9: Run the controller test**

Run: `cd backend && ./mvnw test -Dtest=HubHeartbeatControllerTest`
Expected: PASS (3 updated + 1 new).

- [ ] **Step 10: Full backend suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/licensing/repository/HubActivationRepository.java \
  backend/src/main/java/com/vanter/ember/licensing/service/HubHeartbeatService.java \
  backend/src/main/java/com/vanter/ember/licensing/controller/HubHeartbeatController.java \
  backend/src/test/java/com/vanter/ember/licensing/service/HubHeartbeatServiceTest.java \
  backend/src/test/java/com/vanter/ember/licensing/controller/HubHeartbeatControllerTest.java \
  backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationHeartbeatTest.java
git commit -m "feat(licensing): persist last heartbeat timestamp + caller ip"
```

---

## Task 4: Delete / restore service + `updateStatus` transition guards

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`

**Interfaces:**
- Consumes: `Restaurant#setDeletedAt/#setDeletedBy`, `RestaurantStatus.DELETED` (Task 1).
- Produces:
  - `PlatformRestaurantService#delete(UUID id, String operatorEmail): void`
  - `PlatformRestaurantService#restore(UUID id, String operatorEmail): PlatformRestaurantSummaryResponse`
  - `PlatformRestaurantService#getAll(Pageable pageable, boolean includeDeleted): Page<PlatformRestaurantSummaryResponse>` — **signature change** (was `getAll(Pageable)`).
  - `updateStatus` now throws `IllegalStateException` (→ 409) when the current status is `DELETED`, and `IllegalArgumentException` (→ 409) when the *target* status is `DELETED`.

- [ ] **Step 1: Write the failing service tests**

Add to `PlatformRestaurantServiceTest.java` (helpers `restaurant()` and the mocks already exist):

```java
    private com.vanter.ember.platform.model.PlatformOperator operator() {
        return com.vanter.ember.platform.model.PlatformOperator.builder()
                .id(UUID.randomUUID()).email("operator@ember.local").build();
    }

    @Test
    void delete_softDeletesSuspendedRestaurantAndAudits() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.SUSPENDED);
        var op = operator();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(op));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        platformRestaurantService.delete(r.getId(), "operator@ember.local");

        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        org.mockito.Mockito.verify(restaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(RestaurantStatus.DELETED);
        assertThat(saved.getValue().getDeletedAt()).isNotNull();
        assertThat(saved.getValue().getDeletedBy()).isEqualTo(op.getId());

        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> log =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("RESTAURANT_DELETED");
        assertThat(log.getValue().getOldValue()).isEqualTo("SUSPENDED");
        assertThat(log.getValue().getNewValue()).isEqualTo("DELETED");
    }

    @Test
    void delete_rejectedWhenNotSuspended() {
        Restaurant r = restaurant(); // ACTIVE
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.delete(r.getId(), "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
        org.mockito.Mockito.verify(restaurantRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(platformAuditLogRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restore_movesDeletedBackToSuspendedAndAudits() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.DELETED);
        r.setDeletedAt(Instant.now());
        r.setDeletedBy(UUID.randomUUID());
        var op = operator();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(op));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(restaurantRepository.save(org.mockito.ArgumentMatchers.any(Restaurant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        var result = platformRestaurantService.restore(r.getId(), "operator@ember.local");

        assertThat(result.getStatus()).isEqualTo(RestaurantStatus.SUSPENDED);
        ArgumentCaptor<Restaurant> saved = ArgumentCaptor.forClass(Restaurant.class);
        org.mockito.Mockito.verify(restaurantRepository).save(saved.capture());
        assertThat(saved.getValue().getDeletedAt()).isNull();
        assertThat(saved.getValue().getDeletedBy()).isNull();
        ArgumentCaptor<com.vanter.ember.platform.model.PlatformAuditLog> log =
                ArgumentCaptor.forClass(com.vanter.ember.platform.model.PlatformAuditLog.class);
        org.mockito.Mockito.verify(platformAuditLogRepository).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo("RESTAURANT_RESTORED");
    }

    @Test
    void restore_rejectedWhenNotDeleted() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.SUSPENDED);
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.restore(r.getId(), "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void getAll_excludesDeletedByDefault() {
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable))
                .thenReturn(new PageImpl<>(List.of(restaurant())));

        platformRestaurantService.getAll(pageable, false);

        org.mockito.Mockito.verify(restaurantRepository).findByStatusNot(RestaurantStatus.DELETED, pageable);
        org.mockito.Mockito.verify(restaurantRepository, org.mockito.Mockito.never()).findAll(pageable);
    }

    @Test
    void getAll_includesDeletedWhenRequested() {
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(restaurant())));

        platformRestaurantService.getAll(pageable, true);

        org.mockito.Mockito.verify(restaurantRepository).findAll(pageable);
    }

    @Test
    void updateStatus_rejectsDeletedTarget() {
        Restaurant r = restaurant();
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                r.getId(), RestaurantStatus.DELETED, "operator@ember.local"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateStatus_rejectedWhenCurrentlyDeleted() {
        Restaurant r = restaurant();
        r.setStatus(RestaurantStatus.DELETED);
        when(platformOperatorRepository.findByEmail("operator@ember.local")).thenReturn(Optional.of(operator()));
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> platformRestaurantService.updateStatus(
                r.getId(), RestaurantStatus.ACTIVE, "operator@ember.local"))
                .isInstanceOf(IllegalStateException.class);
    }
```

Also update the existing `getAll_mapsRestaurantsToSummaries` test: change
`platformRestaurantService.getAll(pageable)` → `platformRestaurantService.getAll(pageable, true)`
and its stub `when(restaurantRepository.findAll(pageable))` stays as-is.

- [ ] **Step 2: Run — verify failure**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: FAIL — `delete`/`restore` missing, `getAll(Pageable, boolean)` missing.

- [ ] **Step 3: Implement in `PlatformRestaurantService.java`**

Change `getAll`:

```java
    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable, boolean includeDeleted) {
        Page<Restaurant> page = includeDeleted
                ? restaurantRepository.findAll(pageable)
                : restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable);
        return page.map(PlatformRestaurantSummaryResponse::from);
    }
```

Add `delete` and `restore` (place after `updateStatus`):

```java
    /**
     * Soft-delete a churned tenant. Only a SUSPENDED restaurant may be deleted — the operator has
     * to suspend it first, a deliberate two-step gate. Reversible via {@link #restore}. Nothing is
     * physically removed; DELETED is just another "not ACTIVE" status, so every access gate
     * (SecurityConfig, SessionService, HubHeartbeatService) already blocks it.
     */
    @Transactional
    public void delete(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        if (restaurant.getStatus() != RestaurantStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "El restaurante debe estar suspendido antes de eliminarlo.");
        }
        String oldStatus = restaurant.getStatus().name();
        restaurant.setStatus(RestaurantStatus.DELETED);
        restaurant.setDeletedAt(Instant.now());
        restaurant.setDeletedBy(operator.getId());
        restaurantRepository.save(restaurant);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_DELETED")
                .oldValue(oldStatus)
                .newValue(RestaurantStatus.DELETED.name())
                .build());
    }

    /** Reverse a {@link #delete}: DELETED -> SUSPENDED (never straight to ACTIVE). */
    @Transactional
    public PlatformRestaurantSummaryResponse restore(UUID restaurantId, String operatorEmail) {
        PlatformOperator operator = platformOperatorRepository.findByEmail(operatorEmail)
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found: " + restaurantId));
        if (restaurant.getStatus() != RestaurantStatus.DELETED) {
            throw new IllegalStateException("El restaurante no está eliminado.");
        }
        restaurant.setStatus(RestaurantStatus.SUSPENDED);
        restaurant.setDeletedAt(null);
        restaurant.setDeletedBy(null);
        Restaurant saved = restaurantRepository.save(restaurant);

        platformAuditLogRepository.save(PlatformAuditLog.builder()
                .operatorId(operator.getId())
                .operatorEmail(operator.getEmail())
                .restaurantId(restaurantId)
                .action("RESTAURANT_RESTORED")
                .oldValue(RestaurantStatus.DELETED.name())
                .newValue(RestaurantStatus.SUSPENDED.name())
                .build());

        return PlatformRestaurantSummaryResponse.from(saved);
    }
```

In `updateStatus`, immediately after the `restaurant` is fetched and before `RestaurantStatus oldStatus = ...`:

```java
        if (newStatus == RestaurantStatus.DELETED) {
            throw new IllegalArgumentException(
                    "Usa DELETE /platform/restaurants/{id} para eliminar un restaurante.");
        }
        if (restaurant.getStatus() == RestaurantStatus.DELETED) {
            throw new IllegalStateException(
                    "Restaura el restaurante antes de cambiar su estado.");
        }
```

Add imports if missing: `java.time.Instant` is likely already present via other files — add `import java.time.Instant;` to `PlatformRestaurantService.java` if the build complains.

- [ ] **Step 4: Run — verify pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java \
  backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java
git commit -m "feat(platform): soft-delete + restore suspended restaurants in the service"
```

---

## Task 5: DTO Hub fields + service population in `getAll` / `getById`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantSummaryResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantDetailResponse.java`
- Modify: `backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java`

**Interfaces:**
- Consumes: `HubStatus.from(...)` (Task 2), `HubActivationRepository#findByRestaurantId` / `#findByRestaurantIdIn` (Task 3).
- Produces:
  - `PlatformRestaurantSummaryResponse` gains `HubStatus hubStatus`.
  - `PlatformRestaurantDetailResponse` gains `HubStatus hubStatus`, `Instant hubActivatedAt`, `Instant lastHeartbeatAt`, `String lastHeartbeatIp`.
  - New factory overloads: `PlatformRestaurantSummaryResponse.from(Restaurant, HubActivation)` (nullable activation) and `PlatformRestaurantDetailResponse.from(Restaurant, List<admins>, HubActivation)`.
  - `PlatformRestaurantService` gets a private field `HubActivationRepository hubActivationRepository` (constructor param — the class uses a **manual constructor**, add the param there).

- [ ] **Step 1: Extend the DTOs**

`PlatformRestaurantSummaryResponse.java` — add field + overloaded factory (keep the existing `from(Restaurant)` for callers that have no Hub data, delegating with `null`):

```java
    private HubStatus hubStatus;

    public static PlatformRestaurantSummaryResponse from(Restaurant restaurant) {
        return from(restaurant, null);
    }

    public static PlatformRestaurantSummaryResponse from(Restaurant restaurant, HubActivation activation) {
        Instant lastBeat = activation == null ? null : activation.getLastHeartbeatAt();
        return PlatformRestaurantSummaryResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .plan(restaurant.getPlan())
                .status(restaurant.getStatus())
                .createdAt(restaurant.getCreatedAt())
                .hubStatus(HubStatus.from(lastBeat, Instant.now()))
                .build();
    }
```

Add imports: `com.vanter.ember.licensing.model.HubActivation`. (`java.time.Instant` already imported.)

`PlatformRestaurantDetailResponse.java` — same pattern:

```java
    private HubStatus hubStatus;
    private Instant hubActivatedAt;
    private Instant lastHeartbeatAt;
    private String lastHeartbeatIp;

    public static PlatformRestaurantDetailResponse from(
            Restaurant restaurant, List<PlatformRestaurantAdminResponse> admins) {
        return from(restaurant, admins, null);
    }

    public static PlatformRestaurantDetailResponse from(
            Restaurant restaurant, List<PlatformRestaurantAdminResponse> admins, HubActivation activation) {
        Instant lastBeat = activation == null ? null : activation.getLastHeartbeatAt();
        return PlatformRestaurantDetailResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .slug(restaurant.getSlug())
                .plan(restaurant.getPlan())
                .status(restaurant.getStatus())
                .createdAt(restaurant.getCreatedAt())
                .admins(admins)
                .hubStatus(HubStatus.from(lastBeat, Instant.now()))
                .hubActivatedAt(activation == null ? null : activation.getActivatedAt())
                .lastHeartbeatAt(lastBeat)
                .lastHeartbeatIp(activation == null ? null : activation.getLastHeartbeatIp())
                .build();
    }
```

- [ ] **Step 2: Write the failing service tests**

Add to `PlatformRestaurantServiceTest.java`. Add a `@Mock` for the new dependency at the top with the others:

```java
    @Mock com.vanter.ember.licensing.repository.HubActivationRepository hubActivationRepository;
```

(`@InjectMocks` will supply it to the manual constructor once the param is added.)

```java
    @Test
    void getById_populatesHubStatusFromActivation() {
        Restaurant r = restaurant();
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(userRepository.findByRestaurantId_IdAndRole(r.getId(), Role.ADMIN)).thenReturn(List.of());
        when(hubActivationRepository.findByRestaurantId(r.getId())).thenReturn(Optional.of(
                com.vanter.ember.licensing.model.HubActivation.builder()
                        .restaurantId(r.getId()).hardwareFingerprint("fp")
                        .activatedAt(Instant.now())
                        .lastHeartbeatAt(Instant.now().minusSeconds(60))
                        .lastHeartbeatIp("203.0.113.7")
                        .build()));

        var result = platformRestaurantService.getById(r.getId());

        assertThat(result.getHubStatus())
                .isEqualTo(com.vanter.ember.platform.model.dto.HubStatus.ONLINE);
        assertThat(result.getLastHeartbeatIp()).isEqualTo("203.0.113.7");
    }

    @Test
    void getById_hubStatusIsNeverWhenNoActivation() {
        Restaurant r = restaurant();
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));
        when(userRepository.findByRestaurantId_IdAndRole(r.getId(), Role.ADMIN)).thenReturn(List.of());
        when(hubActivationRepository.findByRestaurantId(r.getId())).thenReturn(Optional.empty());

        assertThat(platformRestaurantService.getById(r.getId()).getHubStatus())
                .isEqualTo(com.vanter.ember.platform.model.dto.HubStatus.NEVER);
    }

    @Test
    void getAll_populatesHubStatusPerRow() {
        Restaurant r = restaurant();
        Pageable pageable = PageRequest.of(0, 20);
        when(restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable))
                .thenReturn(new PageImpl<>(List.of(r)));
        when(hubActivationRepository.findByRestaurantIdIn(List.of(r.getId()))).thenReturn(List.of(
                com.vanter.ember.licensing.model.HubActivation.builder()
                        .restaurantId(r.getId()).hardwareFingerprint("fp").activatedAt(Instant.now())
                        .lastHeartbeatAt(Instant.now().minusSeconds(3600)).build()));

        var page = platformRestaurantService.getAll(pageable, false);

        assertThat(page.getContent().get(0).getHubStatus())
                .isEqualTo(com.vanter.ember.platform.model.dto.HubStatus.STALE);
    }
```

- [ ] **Step 3: Run — verify failure**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: FAIL — constructor arity / `getHubStatus()` missing / no `hubActivationRepository` wired.

- [ ] **Step 4: Implement service population**

In `PlatformRestaurantService.java`:
- Add the field `private final HubActivationRepository hubActivationRepository;` and the matching **constructor parameter** (the class has a hand-written constructor — add `HubActivationRepository hubActivationRepository` to the signature and `this.hubActivationRepository = hubActivationRepository;` to the body). Add import `com.vanter.ember.licensing.repository.HubActivationRepository`.
- Rewrite `getAll`:

```java
    public Page<PlatformRestaurantSummaryResponse> getAll(Pageable pageable, boolean includeDeleted) {
        Page<Restaurant> page = includeDeleted
                ? restaurantRepository.findAll(pageable)
                : restaurantRepository.findByStatusNot(RestaurantStatus.DELETED, pageable);
        List<UUID> ids = page.getContent().stream().map(Restaurant::getId).toList();
        Map<UUID, HubActivation> byRestaurant = ids.isEmpty()
                ? Map.of()
                : hubActivationRepository.findByRestaurantIdIn(ids).stream()
                        .collect(java.util.stream.Collectors.toMap(HubActivation::getRestaurantId, a -> a));
        return page.map(r -> PlatformRestaurantSummaryResponse.from(r, byRestaurant.get(r.getId())));
    }
```

- Rewrite `getById`'s return:

```java
        HubActivation activation = hubActivationRepository.findByRestaurantId(id).orElse(null);
        return PlatformRestaurantDetailResponse.from(restaurant, admins, activation);
```

Add imports: `java.util.Map`, `com.vanter.ember.licensing.model.HubActivation` (and `java.util.List`, `java.util.UUID` if not present).

- [ ] **Step 5: Run — verify pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantSummaryResponse.java \
  backend/src/main/java/com/vanter/ember/platform/model/dto/PlatformRestaurantDetailResponse.java \
  backend/src/main/java/com/vanter/ember/platform/service/PlatformRestaurantService.java \
  backend/src/test/java/com/vanter/ember/platform/service/PlatformRestaurantServiceTest.java
git commit -m "feat(platform): expose derived Hub status on restaurant summary + detail"
```

---

## Task 6: Controller endpoints — `DELETE`, `POST /{id}/restore`, `?includeDeleted`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java`
- Test: `backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java`

**Interfaces:**
- Consumes: `PlatformRestaurantService#delete`, `#restore`, `#getAll(Pageable, boolean)` (Tasks 4–5).
- Produces HTTP: `DELETE /platform/restaurants/{id}` → 204; `POST /platform/restaurants/{id}/restore` → 200 summary; `GET /platform/restaurants?includeDeleted=true`.

- [ ] **Step 1: Write the failing controller tests**

Add to `PlatformRestaurantControllerTest.java` (imports: add `delete` and put/patch already there; add `import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;`):

```java
    @Test
    void deleteRestaurant_returns401WithoutAuthHeader() throws Exception {
        mockMvc.perform(delete("/platform/restaurants/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRestaurant_returns204OnSuccess() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/platform/restaurants/" + id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isNoContent());

        org.mockito.Mockito.verify(platformRestaurantService).delete(id, OPERATOR_EMAIL);
    }

    @Test
    void deleteRestaurant_returns409WhenNotSuspended() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new IllegalStateException("must be suspended"))
                .when(platformRestaurantService).delete(id, OPERATOR_EMAIL);

        mockMvc.perform(delete("/platform/restaurants/" + id).header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isConflict());
    }

    @Test
    void restoreRestaurant_returns200WithSummary() throws Exception {
        authenticate();
        UUID id = UUID.randomUUID();
        when(platformRestaurantService.restore(id, OPERATOR_EMAIL)).thenReturn(
                PlatformRestaurantSummaryResponse.builder()
                        .id(id).name("Tenant Grill").slug("tenant-grill")
                        .plan(RestaurantPlan.PRO).status(RestaurantStatus.SUSPENDED)
                        .createdAt(Instant.now()).build());

        mockMvc.perform(post("/platform/restaurants/" + id + "/restore")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void getAll_forwardsIncludeDeleted() throws Exception {
        authenticate();
        when(platformRestaurantService.getAll(any(), eq(true))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/platform/restaurants")
                        .param("includeDeleted", "true")
                        .header("Authorization", "Bearer " + TOKEN))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(platformRestaurantService)
                .getAll(any(), eq(true));
    }
```

Update the existing `getAll_returns200WithPageOfSummaries` test: change
`when(platformRestaurantService.getAll(any()))` → `when(platformRestaurantService.getAll(any(), eq(false)))`.

- [ ] **Step 2: Run — verify failure**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantControllerTest`
Expected: FAIL — no `DELETE`/`restore` mappings; `getAll` arity.

- [ ] **Step 3: Implement the controller**

In `PlatformRestaurantController.java` add imports `org.springframework.web.bind.annotation.DeleteMapping`, `org.springframework.web.bind.annotation.RequestParam`, `org.springframework.http.HttpStatus`, `org.springframework.web.bind.annotation.ResponseStatus`.

Change `getAll`:

```java
    @Operation(summary = "List all tenants, paginated; soft-deleted excluded unless includeDeleted=true")
    @GetMapping
    public ResponseEntity<Page<PlatformRestaurantSummaryResponse>> getAll(
            Pageable pageable,
            @RequestParam(name = "includeDeleted", defaultValue = "false") boolean includeDeleted) {
        return ResponseEntity.ok(platformRestaurantService.getAll(pageable, includeDeleted));
    }
```

Add:

```java
    @Operation(summary = "Soft-delete a restaurant (must be SUSPENDED); reversible via restore")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication authentication) {
        platformRestaurantService.delete(id, authentication.getName());
    }

    @Operation(summary = "Restore a soft-deleted restaurant to SUSPENDED")
    @PostMapping("/{id}/restore")
    public ResponseEntity<PlatformRestaurantSummaryResponse> restore(
            @PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(platformRestaurantService.restore(id, authentication.getName()));
    }
```

- [ ] **Step 4: Run — verify pass**

Run: `cd backend && ./mvnw test -Dtest=PlatformRestaurantControllerTest`
Expected: PASS.

- [ ] **Step 5: Full backend suite**

Run: `cd backend && ./mvnw test`
Expected: BUILD SUCCESS. If any other test calls `platformRestaurantService.getAll(pageable)` with one arg or `HubHeartbeatService#heartbeat(request)` with one arg, fix those call sites now (grep: `grep -rn "getAll(" backend/src/test | grep -i platform` and `grep -rn "\.heartbeat(" backend/src`).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/platform/controller/PlatformRestaurantController.java \
  backend/src/test/java/com/vanter/ember/platform/controller/PlatformRestaurantControllerTest.java
git commit -m "feat(platform): DELETE + restore endpoints and includeDeleted query param"
```

---

## Task 7: Frontend API client — `platformApi.ts`

**Files:**
- Modify: `frontend/src/lib/platformApi.ts`

**Interfaces:**
- Produces:
  - `PlatformRestaurantSummary` / `PlatformRestaurantDetail` status union gains `'DELETED'`; both gain `hubStatus: HubStatus`.
  - `PlatformRestaurantDetail` also gains `hubActivatedAt: string | null`, `lastHeartbeatAt: string | null`, `lastHeartbeatIp: string | null`.
  - `type HubStatus = 'NEVER' | 'ONLINE' | 'STALE' | 'OFFLINE'`.
  - `platformRestaurantService.deleteRestaurant(id: string): Promise<void>`
  - `platformRestaurantService.restoreRestaurant(id: string): Promise<PlatformRestaurantSummary>`
  - `platformRestaurantService.getAll(page?, size?, includeDeleted?)` — new trailing optional param.

- [ ] **Step 1: Edit `platformApi.ts`**

- Add near the other interfaces:

```ts
export type HubStatus = 'NEVER' | 'ONLINE' | 'STALE' | 'OFFLINE'
```

- In `PlatformRestaurantSummary`: change `status` union to
  `'ACTIVE' | 'SUSPENDED' | 'INACTIVE' | 'DELETED'` and add `hubStatus: HubStatus`.
- In `PlatformRestaurantDetail`: same `status` change, add:

```ts
  hubStatus: HubStatus
  hubActivatedAt: string | null
  lastHeartbeatAt: string | null
  lastHeartbeatIp: string | null
```

- Replace `getAll` and add the two new methods in `platformRestaurantService`:

```ts
  getAll: async (
    page = 0,
    size = 10,
    includeDeleted = false
  ): Promise<Page<PlatformRestaurantSummary>> => {
    const { data } = await platformApi.get<Page<PlatformRestaurantSummary>>(
      '/platform/restaurants',
      { params: { page, size, includeDeleted } }
    )
    return data
  },

  deleteRestaurant: async (id: string): Promise<void> => {
    await platformApi.delete<void>(`/platform/restaurants/${id}`)
  },

  restoreRestaurant: async (id: string): Promise<PlatformRestaurantSummary> => {
    const { data } = await platformApi.post<PlatformRestaurantSummary>(
      `/platform/restaurants/${id}/restore`
    )
    return data
  },
```

- [ ] **Step 2: Type-check**

Run: `cd frontend && pnpm run build`
Expected: `tsc -b` reports errors in `ConsoleRestaurants.tsx` / `ConsoleRestaurantDetail.tsx` only if they already consume the changed shapes — at this point they don't, so **build should pass**. If `getAll`'s callers break (they pass only `page`), that's expected to still compile because the new params are optional.

- [ ] **Step 3: Lint**

Run: `cd frontend && pnpm run lint`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/lib/platformApi.ts
git commit -m "feat(console): platformApi — delete/restore + hub status types"
```

---

## Task 8: `ConsoleRestaurants` — Hub column + "Ver eliminados"

**Files:**
- Modify: `frontend/src/pages/console/ConsoleRestaurants.tsx`
- Test: `frontend/src/pages/console/ConsoleRestaurants.test.tsx` (create)

**Interfaces:**
- Consumes: `platformRestaurantService.getAll(page, size?, includeDeleted?)`, `HubStatus`, `PlatformRestaurantSummary.hubStatus`.

- [ ] **Step 1: Write the failing test**

Create `ConsoleRestaurants.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleRestaurants from '@/pages/console/ConsoleRestaurants'
import { platformRestaurantService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: { ...actual.platformRestaurantService, getAll: vi.fn() },
  }
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  )

const page = (rows: unknown[]) => ({
  content: rows,
  totalElements: rows.length,
  totalPages: 1,
  size: 10,
  number: 0,
})

const row = (over: Record<string, unknown> = {}) => ({
  id: 'r-1',
  name: 'Tenant Grill',
  slug: 'tenant-grill',
  plan: 'PRO',
  status: 'ACTIVE',
  hubStatus: 'ONLINE',
  createdAt: '2026-09-01T00:00:00Z',
  ...over,
})

describe('ConsoleRestaurants', () => {
  beforeEach(() => vi.clearAllMocks())

  test('renders the Hub status for each row', async () => {
    vi.mocked(platformRestaurantService.getAll).mockResolvedValue(page([row({ hubStatus: 'OFFLINE' })]) as never)
    wrap(<ConsoleRestaurants />)
    expect(await screen.findByText('OFFLINE')).toBeVisible()
  })

  test('"Ver eliminados" refetches with includeDeleted=true', async () => {
    vi.mocked(platformRestaurantService.getAll).mockResolvedValue(page([row()]) as never)
    wrap(<ConsoleRestaurants />)
    await screen.findByText('Tenant Grill')

    fireEvent.click(screen.getByLabelText('Ver eliminados'))

    await waitFor(() =>
      expect(platformRestaurantService.getAll).toHaveBeenLastCalledWith(0, 10, true)
    )
  })
})
```

- [ ] **Step 2: Run — verify failure**

Run: `cd frontend && pnpm run test:run ConsoleRestaurants`
Expected: FAIL — no "Ver eliminados" control, `getAll` not called with 3 args.

- [ ] **Step 3: Implement**

In `ConsoleRestaurants.tsx`:
- Add a `hubStatus` → dot helper near `statusBadgeClass`:

```tsx
const hubDot = (hubStatus: string) => {
  switch (hubStatus) {
    case 'ONLINE':
      return { cls: 'bg-green-500', label: 'ONLINE' }
    case 'STALE':
      return { cls: 'bg-amber-500', label: 'STALE' }
    case 'OFFLINE':
      return { cls: 'bg-zinc-400', label: 'OFFLINE' }
    default:
      return { cls: 'bg-transparent', label: '—' }
  }
}
```

- Add `includeDeleted` state and thread it into the query:

```tsx
  const [page, setPage] = useState(0)
  const [includeDeleted, setIncludeDeleted] = useState(false)

  const { data: restaurantsPage, isLoading, isError } = useQuery({
    queryKey: ['platformRestaurants', page, includeDeleted],
    queryFn: () => platformRestaurantService.getAll(page, 10, includeDeleted),
  })
```

- Above the table (inside the header `div` row or just below it), add:

```tsx
      <label className="flex items-center gap-2 text-sm text-zinc-600">
        <input
          type="checkbox"
          checked={includeDeleted}
          onChange={(e) => {
            setIncludeDeleted(e.target.checked)
            setPage(0)
          }}
        />
        Ver eliminados
      </label>
```

- Add a `Hub` column header (`<th … >Hub</th>`) after `Estado`, and a cell in the row body:

```tsx
                <td className="px-4 py-2">
                  {(() => {
                    const dot = hubDot(restaurant.hubStatus)
                    return (
                      <span className="inline-flex items-center gap-1.5 text-xs text-zinc-600">
                        <span className={`h-2 w-2 rounded-full ${dot.cls}`} />
                        {dot.label}
                      </span>
                    )
                  })()}
                </td>
```

- Bump the empty-state `colSpan` from `5` to `6`.
- On `DELETED` rows, mute the row: on the `<tr>` add
  `className={restaurant.status === 'DELETED' ? 'opacity-50' : undefined}` and extend
  `statusBadgeClass` with `case 'DELETED': return 'bg-zinc-200 text-zinc-500'`.

- [ ] **Step 4: Run — verify pass**

Run: `cd frontend && pnpm run test:run ConsoleRestaurants`
Expected: PASS (2 tests).

- [ ] **Step 5: Build + lint**

Run: `cd frontend && pnpm run build && pnpm run lint`
Expected: both clean.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/console/ConsoleRestaurants.tsx frontend/src/pages/console/ConsoleRestaurants.test.tsx
git commit -m "feat(console): hub status column + show-deleted toggle on the restaurant list"
```

---

## Task 9: `ConsoleRestaurantDetail` — Hub panel + delete/restore + confirm

**Files:**
- Modify: `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`
- Test: `frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx` (create)

**Interfaces:**
- Consumes: `platformRestaurantService.getById`, `.deleteRestaurant`, `.restoreRestaurant`,
  `PlatformRestaurantDetail.{hubStatus,hubActivatedAt,lastHeartbeatAt,lastHeartbeatIp,status}`.

- [ ] **Step 1: Write the failing test**

Create `ConsoleRestaurantDetail.test.tsx`:

```tsx
import type { ReactNode } from 'react'
import { describe, test, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import ConsoleRestaurantDetail from '@/pages/console/ConsoleRestaurantDetail'
import { platformRestaurantService, platformAuditLogService } from '@/lib/platformApi'

vi.mock('@/lib/platformApi', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/lib/platformApi')>()
  return {
    ...actual,
    platformRestaurantService: {
      ...actual.platformRestaurantService,
      getById: vi.fn(),
      deleteRestaurant: vi.fn().mockResolvedValue(undefined),
      restoreRestaurant: vi.fn().mockResolvedValue(undefined),
      updateStatus: vi.fn(),
      issueHubLicense: vi.fn(),
    },
    platformAuditLogService: { ...actual.platformAuditLogService, getByRestaurant: vi.fn() },
  }
})

const detail = (over: Record<string, unknown> = {}) => ({
  id: 'r-1',
  name: 'Tenant Grill',
  slug: 'tenant-grill',
  plan: 'PRO',
  status: 'SUSPENDED',
  createdAt: '2026-09-01T00:00:00Z',
  admins: [],
  hubStatus: 'ONLINE',
  hubActivatedAt: '2026-09-02T00:00:00Z',
  lastHeartbeatAt: '2026-09-06T11:59:00Z',
  lastHeartbeatIp: '203.0.113.7',
  ...over,
})

const wrap = (ui: ReactNode) =>
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={['/console/restaurants/r-1']}>
        <Routes>
          <Route path="/console/restaurants/:id" element={ui} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )

describe('ConsoleRestaurantDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(platformAuditLogService.getByRestaurant).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, size: 10, number: 0,
    } as never)
  })

  test('shows the Hub panel with last heartbeat and IP', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    wrap(<ConsoleRestaurantDetail />)
    expect(await screen.findByText('203.0.113.7')).toBeVisible()
    expect(screen.getByText('ONLINE')).toBeVisible()
  })

  test('delete requires typing the slug then calls deleteRestaurant', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail() as never)
    wrap(<ConsoleRestaurantDetail />)
    fireEvent.click(await screen.findByRole('button', { name: 'Eliminar restaurante' }))

    const confirmBtn = screen.getByRole('button', { name: 'Confirmar eliminación' })
    expect(confirmBtn).toBeDisabled()

    fireEvent.change(screen.getByLabelText('Escribe el slug para confirmar'), {
      target: { value: 'tenant-grill' },
    })
    expect(confirmBtn).toBeEnabled()
    fireEvent.click(confirmBtn)

    await waitFor(() =>
      expect(platformRestaurantService.deleteRestaurant).toHaveBeenCalledWith('r-1')
    )
  })

  test('a DELETED restaurant shows Restaurar instead of the status/license controls', async () => {
    vi.mocked(platformRestaurantService.getById).mockResolvedValue(detail({ status: 'DELETED' }) as never)
    wrap(<ConsoleRestaurantDetail />)

    fireEvent.click(await screen.findByRole('button', { name: 'Restaurar restaurante' }))
    await waitFor(() =>
      expect(platformRestaurantService.restoreRestaurant).toHaveBeenCalledWith('r-1')
    )
    expect(screen.queryByRole('button', { name: 'Emitir licencia Hub' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Eliminar restaurante' })).toBeNull()
  })
})
```

- [ ] **Step 2: Run — verify failure**

Run: `cd frontend && pnpm run test:run ConsoleRestaurantDetail`
Expected: FAIL — no Hub panel, no delete/restore buttons.

- [ ] **Step 3: Implement**

In `ConsoleRestaurantDetail.tsx`:
- Add imports: `useState` is already imported.
- Add mutations next to `toggleStatus`:

```tsx
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false)
  const [slugInput, setSlugInput] = useState('')

  const deleteRestaurant = useMutation({
    mutationFn: () => platformRestaurantService.deleteRestaurant(id!),
    onSuccess: () => {
      setShowDeleteConfirm(false)
      setSlugInput('')
      queryClient.invalidateQueries({ queryKey: ['platformRestaurant', id] })
      queryClient.invalidateQueries({ queryKey: ['platformAuditLog', id] })
      queryClient.invalidateQueries({ queryKey: ['platformRestaurants'] })
    },
  })

  const restoreRestaurant = useMutation({
    mutationFn: () => platformRestaurantService.restoreRestaurant(id!),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['platformRestaurant', id] })
      queryClient.invalidateQueries({ queryKey: ['platformAuditLog', id] })
      queryClient.invalidateQueries({ queryKey: ['platformRestaurants'] })
    },
  })
```

- In the header action `div` (currently the "Emitir licencia Hub" + suspend/reactivate buttons),
  wrap the existing two buttons so they render only when `restaurant.status !== 'DELETED'`, and
  add the delete / restore controls:

```tsx
        <div className="flex items-center gap-2">
          {restaurant.status === 'DELETED' ? (
            <Button
              type="button"
              disabled={restoreRestaurant.isPending}
              onClick={() => restoreRestaurant.mutate()}
            >
              {restoreRestaurant.isPending ? 'Restaurando...' : 'Restaurar restaurante'}
            </Button>
          ) : (
            <>
              <Button type="button" variant="outline" disabled={issueHubLicense.isPending}
                onClick={() => issueHubLicense.mutate()}>
                {issueHubLicense.isPending ? 'Emitiendo...' : 'Emitir licencia Hub'}
              </Button>
              <Button type="button" disabled={toggleStatus.isPending}
                onClick={() => toggleStatus.mutate(nextStatus(restaurant.status))}>
                {restaurant.status === 'SUSPENDED' ? 'Reactivar' : 'Suspender'}
              </Button>
              <Button
                type="button"
                variant="outline"
                className="border-red-300 text-red-700 hover:bg-red-50"
                disabled={restaurant.status !== 'SUSPENDED'}
                title={restaurant.status !== 'SUSPENDED' ? 'Suspende el restaurante primero' : undefined}
                onClick={() => setShowDeleteConfirm(true)}
              >
                Eliminar restaurante
              </Button>
            </>
          )}
        </div>
```

- After the info grid (`grid grid-cols-2 …`), add a **Hub panel**:

```tsx
      <div className="grid grid-cols-2 gap-4 rounded-lg border border-zinc-200 p-4 text-sm">
        <div>
          <div className="text-zinc-500">Estado del Hub</div>
          <div className="font-medium text-zinc-800">{restaurant.hubStatus}</div>
        </div>
        <div>
          <div className="text-zinc-500">Activado</div>
          <div className="font-medium text-zinc-800">
            {restaurant.hubActivatedAt
              ? new Date(restaurant.hubActivatedAt).toLocaleString()
              : '—'}
          </div>
        </div>
        <div>
          <div className="text-zinc-500">Último latido</div>
          <div className="font-medium text-zinc-800">
            {restaurant.lastHeartbeatAt
              ? new Date(restaurant.lastHeartbeatAt).toLocaleString()
              : '—'}
          </div>
        </div>
        <div>
          <div className="text-zinc-500">IP</div>
          <div className="font-medium text-zinc-800">{restaurant.lastHeartbeatIp ?? '—'}</div>
        </div>
      </div>
```

- At the end of the component's returned JSX (before the closing `</div>` of the root), add the
  confirm dialog (simple inline modal — the console has no dialog primitive wired):

```tsx
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="flex w-full max-w-md flex-col gap-3 rounded-lg bg-white p-5">
            <h3 className="text-lg font-semibold">Eliminar restaurante</h3>
            <p className="text-sm text-zinc-600">
              Esto marca <span className="font-medium">{restaurant.name}</span> como eliminado.
              Se puede restaurar después. Escribe <code>{restaurant.slug}</code> para confirmar.
            </p>
            <label className="text-sm text-zinc-600" htmlFor="delete-slug">
              Escribe el slug para confirmar
            </label>
            <input
              id="delete-slug"
              className="rounded-md border border-zinc-300 px-2 py-1 text-sm"
              value={slugInput}
              onChange={(e) => setSlugInput(e.target.value)}
            />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline"
                onClick={() => { setShowDeleteConfirm(false); setSlugInput('') }}>
                Cancelar
              </Button>
              <Button
                type="button"
                className="bg-red-600 hover:bg-red-700"
                disabled={slugInput !== restaurant.slug || deleteRestaurant.isPending}
                onClick={() => deleteRestaurant.mutate()}
              >
                Confirmar eliminación
              </Button>
            </div>
          </div>
        </div>
      )}
```

- [ ] **Step 4: Run — verify pass**

Run: `cd frontend && pnpm run test:run ConsoleRestaurantDetail`
Expected: PASS (3 tests).

- [ ] **Step 5: Build + lint + full frontend test run**

Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`
Expected: all clean/green.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/console/ConsoleRestaurantDetail.tsx frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx
git commit -m "feat(console): hub liveness panel + delete/restore on the restaurant detail page"
```

---

## Task 10: Report + PROGRESS + final verification

**Files:**
- Create: `reports/384-platform-console-retire-and-liveness.md`
- Modify: `PROGRESS.md`

- [ ] **Step 1: Full backend + frontend verification**

Run: `cd backend && ./mvnw test`  → BUILD SUCCESS
Run: `cd frontend && pnpm run build && pnpm run lint && pnpm run test:run`  → all clean

- [ ] **Step 2: Write `reports/384-platform-console-retire-and-liveness.md`**

Follow `CLAUDE.md` §4 structure: Identification (report 384, task = this plan, predecessor 383),
Objective, Modified Files (the full list from this plan), What Changed (per §4.1–§4.5 of the
spec), Why It Changed (spec §1–§2). Include the **Flyway `V8` deployment note** (manual
`ALTER TABLE` for baselined environments) prominently.

- [ ] **Step 3: Update `PROGRESS.md`**

- `Last Completed Task` → report 384 with a 2–3 sentence summary.
- `Current Active Task` → "platform console piece D (visual redesign) — separate spec, not started".
- `System Health` → backend + frontend green with counts.
- Keep the Flyway/`V7` caveat bullet and add a sibling note that `V8` has the same baseline risk.

- [ ] **Step 4: Commit**

```bash
git add reports/384-platform-console-retire-and-liveness.md PROGRESS.md
git commit -m "docs(platform): report 384 + progress — retire tenants + hub liveness"
```

- [ ] **Step 5: Push + PR**

```bash
git push -u origin spec/platform-console-retire-liveness
gh pr create --base main --head spec/platform-console-retire-liveness \
  --title "feat(platform): retire suspended tenants + Hub liveness in the console" \
  --body "Implements docs/superpowers/specs/2026-09-06-platform-console-retire-and-liveness-design.md (pieces B + C). ..."
```

---

## Self-Review

**Spec coverage:**
- §4.1 data model → Task 1 (migration, entity fields, `DELETED`, `findByStatusNot`) + Task 3 (`hub_activations` columns used).
- §4.2 delete/restore API → Task 4 (service) + Task 6 (controller, `?includeDeleted`, `updateStatus` guards). `GET /{id}` unchanged and still returns `DELETED` — covered (no code change needed; noted).
- §4.3 heartbeat persistence → Task 3.
- §4.4 derived Hub status → Task 2 (enum) + Task 5 (DTO fields + service population).
- §4.5 console UI → Task 7 (API client) + Task 8 (list) + Task 9 (detail).
- §5 testing → every backend task ends with its `@Test`s + full suite; Tasks 8–9 add Vitest specs; Task 10 runs `pnpm run build`/`lint`/`test:run`.
- §6 rollout → Task 10 report note; no backfill needed (null → `NEVER`).
- §7 open items → resolved in this plan: `IllegalStateException`→409 confirmed (Global Constraints); `HubStatus` lives in `platform.model.dto` (Task 2); caller-IP is a local helper in `HubHeartbeatController` (Task 3).

**Placeholder scan:** no "TBD"/"handle errors"/"similar to Task N" — every code and test step has literal content. The PR body in Task 10 Step 5 ends with "..." deliberately (the executor fills the standard summary); acceptable as it is not a code artifact.

**Type consistency:** `HubStatus` values `NEVER|ONLINE|STALE|OFFLINE` identical in Task 2 (Java), Task 7 (TS), Tasks 8–9 (tests). `heartbeat(request, callerIp)` 2-arg signature consistent across Tasks 3 (impl), 3 (service test), 3 (controller test). `getAll(Pageable, boolean)` consistent across Tasks 4, 5, 6. `deleteRestaurant`/`restoreRestaurant` names identical in Task 7 (client) and Task 9 (test). `findByStatusNot` identical in Tasks 1 and 4.
