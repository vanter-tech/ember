# Ember Postgres Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move `session` and `kitchen` off MongoDB onto PostgreSQL, so the whole Ember backend runs on a single persistence engine (unblocks Vanter Hub's "Hub JVM simple" route, which assumed one local Postgres instance with no second DB engine to run).

**Architecture:** In-place technology swap, not a data migration — there is no production data to preserve (confirmed with the user: dev-only, no live tenants). `Session`/`KitchenOrder` convert from Mongo `@Document` to JPA `@Entity`; their embedded arrays (`participants`, `items`, `activityLog`) move into `text` columns holding JSON, via Hibernate's `@JdbcTypeCode(SqlTypes.JSON)` — the exact pattern `RestaurantSettings.payload` already uses in this codebase. `SessionRepository`/`KitchenOrderRepository` keep their **exact existing public method signatures**, so `SessionService`/`KitchenService` need zero changes. The two backfill `ApplicationRunner`s that exist only because Mongo has no schema migrations (`MongoTenantBackfill`, `KitchenOrderActiveBackfill`) are deleted **first**, before anything else touches `Session`/`KitchenOrder` — their own tests create fixtures via `SessionRepository`/`KitchenOrderRepository` but read them back via raw `MongoTemplate`, so the moment those repositories become JPA, the two datastores diverge and every one of those tests breaks. Deleting the backfills has zero dependents (verified by grep), so it's safe to do before any schema work starts.

**Tech Stack:** Java 17, Spring Boot 3.5.14, Spring Data JPA, Hibernate 6, PostgreSQL (prod), H2 (tests), Flyway.

**Spec:** No separate design doc — decided inline during brainstorming for `docs/superpowers/specs/vanter_hub.md` (2026-08-24 session): the two persistence-unification options (dual backend by Spring profile vs. full migration) were discussed with the user, who chose full migration once it was confirmed there is no production data to protect.

## Global Constraints

- **No data migration.** This is a schema cutover, not a live migration — delete-and-recreate is fine, nothing to preserve.
- **Zero behavior change to `SessionService`/`KitchenService`.** Every repository method keeps its exact current name, parameter types, and return type. If a task's diff touches either service file, stop and re-check — it means a repository signature drifted.
- **JSON columns use `@JdbcTypeCode(SqlTypes.JSON)` with NO `columnDefinition` override.** A hardcoded `columnDefinition = "jsonb"` makes the entity uncreatable on the H2 test schema (`RestaurantSettings.payload` already hit this and documented the fix — see its comment). Flyway declares the physical column as plain `text` in production; Hibernate's generic JSON type resolves correctly on both PostgreSQL and H2 without forcing the DDL.
- **`Session` does NOT get `@TenantId`.** `SessionRepository.findByJoinCodeAndStatus` is deliberately untenanted (a customer types a table code before any restaurant is bound to their JWT) — this mirrors why `User` is excluded from `@TenantId` today. Adding the Hibernate tenant filter to `Session` would silently make that query always return empty (it would filter to the `NO_TENANT` sentinel). Keep explicit `tenantId` parameters on every finder, exactly as today.
- **`KitchenOrder` DOES get `@TenantId`.** It has no untenanted lookup — matches the `MenuItem`/`Category`/`ModifierGroup` convention. Explicit `tenantId` parameters stay on every finder too (redundant with the filter, kept deliberately — same convention `MenuItemRepository` documents for itself).
- **Nested value types (`Participant`, `OrderItem`, `SelectedModifier`, `SessionActivity`, `KitchenItem`) are NOT touched.** They stay plain Lombok POJOs — under JPA they're just the shape of the JSON blob, not separate mapped entities.
- Run `cd backend && ./mvnw test` after every task — do not move to the next task on a red suite.

---

### Task 1: Delete `MongoTenantBackfill`

**Files:**
- Delete: `backend/src/main/java/com/vanter/ember/config/MongoTenantBackfill.java`
- Delete: `backend/src/test/java/com/vanter/ember/config/MongoTenantBackfillTest.java`

**Interfaces:**
- None — this `ApplicationRunner` exists only because Mongo has no Flyway equivalent. Task 3's migration defines `sessions`/`kitchen_orders` with `tenant_id NOT NULL` from creation, so there is nothing left to backfill once this branch lands. Deleted first, before Task 4 touches `Session`, because `MongoTenantBackfillTest` creates its fixtures via `SessionRepository`/`KitchenOrderRepository` but reads them back via raw `MongoTemplate` — the moment those repositories become JPA (Tasks 5/9), that test starts reading from the wrong datastore and breaks for reasons unrelated to the backfill logic itself.

- [ ] **Step 1: Delete both files**

```bash
git rm backend/src/main/java/com/vanter/ember/config/MongoTenantBackfill.java
git rm backend/src/test/java/com/vanter/ember/config/MongoTenantBackfillTest.java
```

- [ ] **Step 2: Confirm nothing else references it**

Run: `cd backend && grep -rn "MongoTenantBackfill" src`
Expected: no output.

- [ ] **Step 3: Run the full test suite to confirm the app still boots without it**

Run: `cd backend && ./mvnw test`
Expected: PASS, full suite — this is a clean deletion with no other dependents, so nothing else should be affected yet.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(backend): delete MongoTenantBackfill, obsolete once flyway owns the schema"
```

---

### Task 2: Delete `KitchenOrderActiveBackfill`

**Files:**
- Delete: `backend/src/main/java/com/vanter/ember/config/KitchenOrderActiveBackfill.java`
- Delete: `backend/src/test/java/com/vanter/ember/config/KitchenOrderActiveBackfillTest.java`

**Interfaces:**
- None — same reasoning as Task 1. Task 3's migration defines `kitchen_orders.active NOT NULL DEFAULT true` from creation, and `KitchenOrderActiveBackfillTest` has the identical `SessionRepository`-writes/`MongoTemplate`-reads split that would break once Tasks 5/9 land.

- [ ] **Step 1: Delete both files**

```bash
git rm backend/src/main/java/com/vanter/ember/config/KitchenOrderActiveBackfill.java
git rm backend/src/test/java/com/vanter/ember/config/KitchenOrderActiveBackfillTest.java
```

- [ ] **Step 2: Confirm nothing else references it**

Run: `cd backend && grep -rn "KitchenOrderActiveBackfill" src`
Expected: no output.

- [ ] **Step 3: Run the full test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, full suite.

- [ ] **Step 4: Commit**

```bash
git commit -m "chore(backend): delete KitchenOrderActiveBackfill, obsolete once flyway owns the schema"
```

---

### Task 3: Flyway migration for `sessions` and `kitchen_orders`

**Files:**
- Create: `backend/src/main/resources/db/migration/V15__session_kitchen_postgres.sql`

**Interfaces:**
- Produces: two tables, `sessions` and `kitchen_orders`, that later tasks' entities/repositories (Tasks 4, 5, 8, 9) map onto.

- [ ] **Step 1: Write the migration**

```sql
-- Postgres unification (ember-postgress-migration, 2026-08-24): moves session/kitchen
-- off MongoDB onto Postgres so the whole backend runs on one persistence engine.
-- No data to carry over (dev-only, no production tenants) -- this is a fresh schema,
-- not a data migration.
--
-- participants/items/activity_log stay JSON-serialized `text` columns, not native
-- `jsonb` -- see RestaurantSettings.payload for why: a hardcoded jsonb column type
-- makes the entity uncreatable on the H2 test schema, and Hibernate's generic
-- @JdbcTypeCode(SqlTypes.JSON) already round-trips correctly through `text` on both
-- engines. id is varchar(36), a UUID string generated in code (@PrePersist),
-- replacing Mongo's ObjectId hex string -- nothing else in the app parses the id
-- format, so this is a safe swap.

CREATE TABLE IF NOT EXISTS sessions (
    id                varchar(36) PRIMARY KEY,
    version           bigint NOT NULL DEFAULT 0,
    tenant_id         uuid NOT NULL,
    table_id          uuid NOT NULL,
    waiter_id         varchar(255),
    status            varchar(20) NOT NULL,
    max_participants  int NOT NULL,
    participants      text NOT NULL DEFAULT '[]',
    items             text NOT NULL DEFAULT '[]',
    activity_log      text NOT NULL DEFAULT '[]',
    join_code         varchar(10),
    created_at        timestamp
);

CREATE INDEX IF NOT EXISTS idx_sessions_tenant_status ON sessions (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_tenant_table_status ON sessions (tenant_id, table_id, status);
CREATE INDEX IF NOT EXISTS idx_sessions_join_code_status ON sessions (join_code, status);

CREATE TABLE IF NOT EXISTS kitchen_orders (
    id            varchar(36) PRIMARY KEY,
    tenant_id     uuid NOT NULL,
    session_id    varchar(36) NOT NULL,
    table_number  int NOT NULL,
    created_at    timestamp,
    items         text NOT NULL DEFAULT '[]',
    active        boolean NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant ON kitchen_orders (tenant_id);
CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant_active ON kitchen_orders (tenant_id, active);
CREATE INDEX IF NOT EXISTS idx_kitchen_orders_tenant_session ON kitchen_orders (tenant_id, session_id);
```

- [ ] **Step 2: Verify the migration is syntactically valid**

Run: `cd backend && ./mvnw flyway:info -Dflyway.url=jdbc:postgresql://localhost:5432/ember -Dflyway.user=ember -Dflyway.password=<local password>` (against your local dev Postgres from `.env`) — confirm `V15` shows as `Pending`, no parse error. If you don't have a local Postgres running, skip this check here; Task 13's full app boot will catch a bad migration anyway.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V15__session_kitchen_postgres.sql
git commit -m "feat(backend): add sessions/kitchen_orders postgres schema"
```

---

### Task 4: Convert `Session` to a JPA entity

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/model/Session.java`
- Test: `backend/src/test/java/com/vanter/ember/session/model/SessionEntityTest.java` (new)

**Interfaces:**
- Consumes: `sessions` table from Task 3.
- Produces: `Session` as a plain JPA `@Entity` with `String id` (self-assigned, not DB-generated), `List<Participant> participants`, `List<OrderItem> items`, `List<SessionActivity> activityLog` as JSON columns. Field names/types identical to today — `SessionService`/DTOs are untouched.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.session.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class SessionEntityTest {

    @Autowired EntityManager entityManager;

    @Test
    void persist_assignsIdAndRoundTripsEmbeddedLists() {
        Session session = Session.builder()
                .tenantId(UUID.randomUUID())
                .tableId(UUID.randomUUID())
                .waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(4)
                .participants(List.of(Participant.builder().userId("u1").name("Alice").build()))
                .createdAt(LocalDateTime.now())
                .build();

        entityManager.persist(session);
        entityManager.flush();
        entityManager.clear();

        Session reloaded = entityManager.find(Session.class, session.getId());

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getStatus()).isEqualTo(SessionStatus.OPEN);
        assertThat(reloaded.getParticipants()).hasSize(1);
        assertThat(reloaded.getParticipants().get(0).getUserId()).isEqualTo("u1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=SessionEntityTest`
Expected: FAIL — `Session` is still a Mongo `@Document`, `@DataJpaTest` won't even find it as a JPA entity (no table, `EntityManager.persist` throws `IllegalArgumentException: Unknown entity`).

- [ ] **Step 3: Convert the entity**

Replace the full contents of `backend/src/main/java/com/vanter/ember/session/model/Session.java`:

```java
package com.vanter.ember.session.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Deliberately has NO {@code @TenantId}: {@link com.vanter.ember.session.repository.SessionRepository#findByJoinCodeAndStatus}
 * has to see across every tenant (a customer types a table code before any restaurant is bound to
 * their JWT), which Hibernate's tenant filter would silently break by forcing every query to the
 * {@code NO_TENANT} sentinel. Same reasoning as why {@code User} stays outside {@code @TenantId}.
 * Every finder therefore keeps an explicit {@code tenantId} parameter, exactly as it did on Mongo.
 */
@Entity
@Table(
        name = "sessions",
        indexes = {
                @Index(name = "idx_sessions_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "idx_sessions_tenant_table_status", columnList = "tenant_id, table_id, status"),
                @Index(name = "idx_sessions_join_code_status", columnList = "join_code, status")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @Column(updatable = false, nullable = false, length = 36)
    private String id;

    @Version
    private Long version;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "waiter_id")
    private String waiterId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "max_participants", nullable = false)
    private int maxParticipants;

    // No columnDefinition: a hardcoded "jsonb" makes the table uncreatable on the H2 test
    // schema. SqlTypes.JSON resolves to the dialect's own JSON-compatible type on both
    // PostgreSQL and H2 -- see RestaurantSettings.payload for the same pattern.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<Participant> participants = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "activity_log", nullable = false)
    @Builder.Default
    private List<SessionActivity> activityLog = new ArrayList<>();

    @Column(name = "join_code", length = 10)
    private String joinCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=SessionEntityTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/session/model/Session.java backend/src/test/java/com/vanter/ember/session/model/SessionEntityTest.java
git commit -m "feat(backend): convert Session to a JPA entity backed by postgres"
```

---

### Task 5: Convert `SessionRepository` to `JpaRepository`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`

**Interfaces:**
- Consumes: `Session` entity from Task 4.
- Produces: `SessionRepository extends JpaRepository<Session, String>` — every method name/signature identical to the Mongo version. `SessionService` (which already only calls these method names) needs no changes.

- [ ] **Step 1: Replace the repository**

Replace the full contents of `backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java`:

```java
package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, String> {

    Optional<Session> findByIdAndTenantId(String id, UUID tenantId);

    List<Session> findByTenantIdAndTableIdAndStatus(UUID tenantId, UUID tableId, SessionStatus status);

    List<Session> findByTenantId(UUID tenantId);

    /**
     * Filters the embedded {@code participants} JSON in Java rather than in SQL: Postgres's jsonb
     * containment operators aren't portable to H2 (the test datasource), and this method has no
     * production caller today. A tenant-first fetch is cheap at real-world scale (a few thousand
     * sessions per tenant), so a portable in-memory filter beats a Postgres-only native query here.
     */
    default List<Session> findByTenantIdAndParticipants_UserId(UUID tenantId, String userId) {
        return findByTenantId(tenantId).stream()
                .filter(s -> s.getParticipants().stream().anyMatch(p -> userId.equals(p.getUserId())))
                .toList();
    }

    List<Session> findByTenantIdAndTableIdInAndStatus(
            UUID tenantId, List<UUID> tableIds, SessionStatus status);

    Optional<Session> findByTenantIdAndJoinCodeAndStatus(
            UUID tenantId, String joinCode, SessionStatus status);

    /**
     * Deliberately untenanted: a customer types a table code before any restaurant is bound to
     * their token, so this is the one lookup that has to span tenants. Returns a list because
     * join codes are only random, not globally unique — see SessionService#joinSessionCode.
     */
    List<Session> findByJoinCodeAndStatus(String joinCode, SessionStatus status);

    /** How many sessions the tenant currently has in the given status — the analytics live count. */
    long countByTenantIdAndStatus(UUID tenantId, SessionStatus status);

    /**
     * Bulk tenant-first fetch used by product analytics to pull the line items of the sessions whose
     * bills settled inside the reporting window.
     */
    List<Session> findByTenantIdAndIdIn(UUID tenantId, Collection<String> ids);
}
```

- [ ] **Step 2: Compile and run the existing service test suite (still Mongo-shaped, will fail until Task 6)**

Run: `cd backend && ./mvnw test -Dtest=SessionServiceTest`
Expected: PASS unchanged — `SessionServiceTest` mocks `SessionRepository` with Mockito and never references Mongo types directly, so swapping the interface's base type doesn't affect it.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/session/repository/SessionRepository.java
git commit -m "feat(backend): convert SessionRepository from mongo to jpa"
```

---

### Task 6: Rewrite `SessionRepositoryTest` for JPA

**Files:**
- Modify: `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java`

**Interfaces:**
- Consumes: `SessionRepository` from Task 5.

- [ ] **Step 1: Replace the test**

Replace the full contents of `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java`:

```java
package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SessionRepositoryTest {

    private static final UUID TABLE_1_ID = UUID.randomUUID();
    private static final UUID TABLE_2_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired SessionRepository sessionRepository;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
    }

    @Test
    void save_persistsSession() {
        Session session = Session.builder()
                .tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN)
                .maxParticipants(4)
                .createdAt(LocalDateTime.now())
                .build();

        Session saved = sessionRepository.save(session);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByTableIdAndStatus_returnsMatchingSessions() {
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.CLOSED).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_2_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByTenantIdAndTableIdAndStatus(
                TENANT_ID, TABLE_1_ID, SessionStatus.OPEN);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTableId()).isEqualTo(TABLE_1_ID);
        assertThat(result.get(0).getStatus()).isEqualTo(SessionStatus.OPEN);
    }

    @Test
    void findByParticipants_UserId_returnsSessionsForUser() {
        Participant alice = Participant.builder().userId("user-1").name("Alice").build();
        Participant bob = Participant.builder().userId("user-2").name("Bob").build();

        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_1_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(alice))
                .createdAt(LocalDateTime.now()).build());
        sessionRepository.save(Session.builder().tenantId(TENANT_ID).tableId(TABLE_2_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(bob))
                .createdAt(LocalDateTime.now()).build());

        List<Session> result = sessionRepository.findByTenantIdAndParticipants_UserId(TENANT_ID, "user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getParticipants().get(0).getUserId()).isEqualTo("user-1");
    }
}
```

(Only two lines changed from the Mongo version: the import and the `@DataJpaTest` annotation — everything else round-trips identically because the repository's public surface didn't change.)

- [ ] **Step 2: Run the test**

Run: `cd backend && ./mvnw test -Dtest=SessionRepositoryTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTest.java
git commit -m "test(backend): rewrite SessionRepositoryTest for jpa"
```

---

### Task 7: Rewrite `SessionRepositoryTenantIsolationTest` for JPA

**Files:**
- Modify: `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java`

**Interfaces:**
- Consumes: `SessionRepository` from Task 5.

- [ ] **Step 1: Replace the test**

Replace the full contents of `backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java`:

```java
package com.vanter.ember.session.repository;

import com.vanter.ember.session.model.Participant;
import com.vanter.ember.session.model.Session;
import com.vanter.ember.session.model.SessionStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-tenant isolation regression tests for {@link SessionRepository}.
 *
 * <p>{@link Session} deliberately has no {@code @TenantId} filter (see its class Javadoc), so
 * isolation here is only as good as the finder signatures: every query must carry the tenant
 * itself. Each fixture below is duplicated across two tenants with otherwise identical data — same
 * table id, same participant, same join code — so a query that forgets the tenant returns both
 * sessions and fails.
 */
@DataJpaTest
class SessionRepositoryTenantIsolationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID TABLE_ID = UUID.randomUUID();

    @Autowired SessionRepository sessionRepository;

    private Session sessionA;
    private Session sessionB;

    @BeforeEach
    void setUp() {
        sessionRepository.deleteAll();
        sessionA = save(TENANT_A);
        sessionB = save(TENANT_B);
    }

    private Session save(UUID tenantId) {
        return sessionRepository.save(Session.builder()
                .tenantId(tenantId).tableId(TABLE_ID).waiterId("waiter@test.com")
                .status(SessionStatus.OPEN).maxParticipants(4)
                .participants(List.of(Participant.builder().userId("user-1").name("Alice").build()))
                .joinCode("AB3CD")
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Test
    void findByIdAndTenantId_doesNotResolveAnotherTenantsSession() {
        assertThat(sessionRepository.findByIdAndTenantId(sessionA.getId(), TENANT_B)).isEmpty();
        assertThat(sessionRepository.findByIdAndTenantId(sessionA.getId(), TENANT_A)).isPresent();
    }

    @Test
    void findByTenantIdAndTableIdAndStatus_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndTableIdAndStatus(
                TENANT_A, TABLE_ID, SessionStatus.OPEN);

        assertThat(result).extracting(Session::getId).containsExactly(sessionA.getId());
    }

    @Test
    void findByTenantIdAndTableIdInAndStatus_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndTableIdInAndStatus(
                TENANT_B, List.of(TABLE_ID), SessionStatus.OPEN);

        assertThat(result).extracting(Session::getId).containsExactly(sessionB.getId());
    }

    @Test
    void findByTenantIdAndParticipants_UserId_returnsOnlyTheOwningTenantsSession() {
        List<Session> result = sessionRepository.findByTenantIdAndParticipants_UserId(
                TENANT_A, "user-1");

        assertThat(result).extracting(Session::getId).containsExactly(sessionA.getId());
    }

    @Test
    void countByTenantIdAndStatus_countsOnlyTheOwningTenantsOpenSessions() {
        save(TENANT_B);

        assertThat(sessionRepository.countByTenantIdAndStatus(TENANT_A, SessionStatus.OPEN))
                .isEqualTo(1L);
        assertThat(sessionRepository.countByTenantIdAndStatus(TENANT_B, SessionStatus.OPEN))
                .isEqualTo(2L);
        assertThat(sessionRepository.countByTenantIdAndStatus(TENANT_A, SessionStatus.CLOSED))
                .isZero();
    }

    @Test
    void findByTenantIdAndIdIn_doesNotResolveAnotherTenantsSessions() {
        List<String> bothIds = List.of(sessionA.getId(), sessionB.getId());

        assertThat(sessionRepository.findByTenantIdAndIdIn(TENANT_A, bothIds))
                .extracting(Session::getId)
                .containsExactly(sessionA.getId());
        assertThat(sessionRepository.findByTenantIdAndIdIn(UUID.randomUUID(), bothIds)).isEmpty();
    }

    @Test
    void findByTenantIdAndJoinCodeAndStatus_doesNotResolveAnotherTenantsJoinCode() {
        assertThat(sessionRepository.findByTenantIdAndJoinCodeAndStatus(
                TENANT_B, "AB3CD", SessionStatus.OPEN))
                .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(sessionB.getId()));
        assertThat(sessionRepository.findByTenantIdAndJoinCodeAndStatus(
                UUID.randomUUID(), "AB3CD", SessionStatus.OPEN)).isEmpty();
    }
}
```

(Same pattern as Task 6 — annotation swap and an updated class Javadoc explaining why `Session` still has no tenant filter; every test body is untouched because the repository's behavior is unchanged from the caller's point of view.)

- [ ] **Step 2: Run the test**

Run: `cd backend && ./mvnw test -Dtest=SessionRepositoryTenantIsolationTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/vanter/ember/session/repository/SessionRepositoryTenantIsolationTest.java
git commit -m "test(backend): rewrite SessionRepositoryTenantIsolationTest for jpa"
```

---

### Task 8: Convert `KitchenOrder` to a JPA entity with `@TenantId`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java`
- Test: `backend/src/test/java/com/vanter/ember/kitchen/model/KitchenOrderEntityTest.java` (new)

**Interfaces:**
- Consumes: `kitchen_orders` table from Task 3, `TenantIdentifierResolver` (existing, `com.vanter.ember.config`).
- Produces: `KitchenOrder` as a JPA `@Entity` with `@TenantId` on `tenantId` — auto-stamped/filtered by Hibernate from `TenantContextHolder`, same convention as `MenuItem`/`Category`/`ModifierGroup`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.kitchen.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TenantIdentifierResolver.class)
class KitchenOrderEntityTest {

    @Autowired EntityManager entityManager;

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void persist_stampsBoundTenantAndAssignsId() {
        UUID tenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);

        KitchenOrder order = KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();

        entityManager.persist(order);
        entityManager.flush();
        entityManager.clear();

        KitchenOrder reloaded = entityManager.find(KitchenOrder.class, order.getId());

        assertThat(reloaded.getId()).isNotNull();
        assertThat(reloaded.getTenantId()).isEqualTo(tenantId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=KitchenOrderEntityTest`
Expected: FAIL — `KitchenOrder` is still a Mongo `@Document`.

- [ ] **Step 3: Convert the entity**

Replace the full contents of `backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java`:

```java
package com.vanter.ember.kitchen.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.TenantId;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
        name = "kitchen_orders",
        indexes = {
                @Index(name = "idx_kitchen_orders_tenant", columnList = "tenant_id"),
                @Index(name = "idx_kitchen_orders_tenant_active", columnList = "tenant_id, active"),
                @Index(name = "idx_kitchen_orders_tenant_session", columnList = "tenant_id, session_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenOrder {

    @Id
    @Column(updatable = false, nullable = false, length = 36)
    private String id;

    @TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "table_number", nullable = false)
    private int tableNumber;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // No columnDefinition -- see Session.java's comment on the same pattern.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    @Builder.Default
    private List<KitchenItem> items = new ArrayList<>();

    /** Whether this order still belongs to a live session; false once its session closes. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @PrePersist
    void ensureId() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=KitchenOrderEntityTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/kitchen/model/KitchenOrder.java backend/src/test/java/com/vanter/ember/kitchen/model/KitchenOrderEntityTest.java
git commit -m "feat(backend): convert KitchenOrder to a jpa entity with @TenantId"
```

---

### Task 9: Convert `KitchenOrderRepository` to `JpaRepository`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/kitchen/repository/KitchenOrderRepository.java`

**Interfaces:**
- Consumes: `KitchenOrder` entity from Task 8.
- Produces: `KitchenOrderRepository extends JpaRepository<KitchenOrder, String>` — method names/signatures identical to the Mongo version. `KitchenService` needs no changes.

- [ ] **Step 1: Replace the repository**

Replace the full contents of `backend/src/main/java/com/vanter/ember/kitchen/repository/KitchenOrderRepository.java`:

```java
package com.vanter.ember.kitchen.repository;

import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, String> {

    List<KitchenOrder> findByTenantId(UUID tenantId);

    List<KitchenOrder> findByTenantIdAndActiveTrue(UUID tenantId);

    Page<KitchenOrder> findByTenantId(UUID tenantId, Pageable pageable);

    Optional<KitchenOrder> findByIdAndTenantId(String id, UUID tenantId);

    Optional<KitchenOrder> findByTenantIdAndSessionId(UUID tenantId, String sessionId);

    /**
     * Filters the embedded {@code items} JSON in Java rather than in SQL — same portability
     * reasoning as {@link com.vanter.ember.session.repository.SessionRepository#findByTenantIdAndParticipants_UserId}.
     */
    default List<KitchenOrder> findByTenantIdAndItems_Status(UUID tenantId, OrderItemStatus status) {
        return findByTenantId(tenantId).stream()
                .filter(o -> o.getItems().stream().anyMatch(i -> status == i.getStatus()))
                .toList();
    }
}
```

- [ ] **Step 2: Run the (still-Mongo-shaped) service test to confirm no regression**

Run: `cd backend && ./mvnw test -Dtest=KitchenServiceTest`
Expected: PASS unchanged — `KitchenServiceTest` mocks `KitchenOrderRepository` with Mockito and never references Mongo types.

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/vanter/ember/kitchen/repository/KitchenOrderRepository.java
git commit -m "feat(backend): convert KitchenOrderRepository from mongo to jpa"
```

---

### Task 10: Rewrite `KitchenOrderRepositoryTest` for JPA

**Files:**
- Modify: `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTest.java`

**Interfaces:**
- Consumes: `KitchenOrderRepository` from Task 9, `TenantIdentifierResolver` (existing).

- [ ] **Step 1: Replace the test**

Replace the full contents of `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTest.java`:

```java
package com.vanter.ember.kitchen.repository;

import com.vanter.ember.config.TenantContextHolder;
import com.vanter.ember.config.TenantIdentifierResolver;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link KitchenOrder} carries {@code @TenantId}, so every save/read below runs with
 * {@link TenantContextHolder} bound — otherwise Hibernate stamps/filters against the
 * {@code NO_TENANT} sentinel instead of {@link #TENANT_ID}.
 */
@DataJpaTest
@Import(TenantIdentifierResolver.class)
class KitchenOrderRepositoryTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(TENANT_ID);
        kitchenOrderRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void save_persistsKitchenOrder() {
        KitchenOrder order = KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>())
                .build();

        KitchenOrder saved = kitchenOrderRepository.save(order);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getSessionId()).isEqualTo("sess-1");
        assertThat(saved.getTableNumber()).isEqualTo(5);
    }

    @Test
    void findByTenantIdAndSessionId_returnsMatchingOrder() {
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-2").tableNumber(3).items(new ArrayList<>()).build());

        Optional<KitchenOrder> result = kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_ID, "sess-1");

        assertThat(result).isPresent();
        assertThat(result.get().getSessionId()).isEqualTo("sess-1");
        assertThat(result.get().getTableNumber()).isEqualTo(5);
    }

    @Test
    void findByTenantId_paginated_returnsOnePageAtATime() {
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(1).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-2").tableNumber(2).items(new ArrayList<>()).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-3").tableNumber(3).items(new ArrayList<>()).build());

        Page<KitchenOrder> firstPage = kitchenOrderRepository.findByTenantId(TENANT_ID, PageRequest.of(0, 2));

        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByTenantIdAndItems_Status_returnsOrdersContainingItemWithGivenStatus() {
        KitchenItem pendingItem = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        KitchenItem preparingItem = KitchenItem.builder()
                .itemId("order-item-2").name("Burger").participantName("Bob")
                .status(OrderItemStatus.PREPARING).updatedAt(LocalDateTime.now()).build();

        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .items(new ArrayList<>(List.of(pendingItem))).build());
        kitchenOrderRepository.save(KitchenOrder.builder()
                .sessionId("sess-2").tableNumber(3)
                .items(new ArrayList<>(List.of(preparingItem))).build());

        List<KitchenOrder> result = kitchenOrderRepository.findByTenantIdAndItems_Status(
                TENANT_ID, OrderItemStatus.PENDING);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getSessionId()).isEqualTo("sess-1");
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && ./mvnw test -Dtest=KitchenOrderRepositoryTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTest.java
git commit -m "test(backend): rewrite KitchenOrderRepositoryTest for jpa"
```

---

### Task 11: Rewrite `KitchenOrderRepositoryTenantIsolationTest` on `AbstractTenantIsolationTest`

**Files:**
- Modify: `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTenantIsolationTest.java`

**Interfaces:**
- Consumes: `KitchenOrderRepository` from Task 9, `AbstractTenantIsolationTest` (existing, `com.vanter.ember.config`, already used by `ModifierGroupRepositoryTenantIsolationTest` and others).

- [ ] **Step 1: Replace the test**

Replace the full contents of `backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTenantIsolationTest.java`:

```java
package com.vanter.ember.kitchen.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.vanter.ember.config.AbstractTenantIsolationTest;
import com.vanter.ember.kitchen.model.KitchenItem;
import com.vanter.ember.kitchen.model.KitchenOrder;
import com.vanter.ember.session.model.OrderItemStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Cross-tenant isolation regression tests for {@link KitchenOrderRepository}.
 *
 * <p>Both tenants get an order for the same session id, holding an item in the same status, so any
 * finder that drops the tenant from its query returns the other restaurant's kitchen queue — which
 * is exactly the leak {@code GET /kitchen/display} had before task-2.17. {@link KitchenOrder}
 * carries {@code @TenantId} (unlike {@code Session}, which has no untenanted lookup need), so
 * stamping/filtering is automatic — see {@link AbstractTenantIsolationTest} for why each save/read
 * below still runs wrapped in {@code asTenant}/{@code readAs}.
 */
class KitchenOrderRepositoryTenantIsolationTest extends AbstractTenantIsolationTest {

    @Autowired KitchenOrderRepository kitchenOrderRepository;

    private String orderAId;
    private String orderBId;

    @Override
    protected void deleteAll() {
        kitchenOrderRepository.deleteAll();
    }

    private KitchenOrder newOrder() {
        KitchenItem item = KitchenItem.builder()
                .itemId("order-item-1").name("Tacos").participantName("Alice")
                .status(OrderItemStatus.PENDING).updatedAt(LocalDateTime.now()).build();
        return KitchenOrder.builder()
                .sessionId("sess-1").tableNumber(5)
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>(List.of(item)))
                .build();
    }

    private void seed() {
        orderAId = readAs(TENANT_A, () -> kitchenOrderRepository.save(newOrder())).getId();
        orderBId = readAs(TENANT_B, () -> kitchenOrderRepository.save(newOrder())).getId();
    }

    @Test
    void findByTenantId_returnsOnlyTheOwningTenantsOrders() {
        seed();

        assertThat(readAs(TENANT_A, () -> kitchenOrderRepository.findByTenantId(TENANT_A)))
                .extracting(KitchenOrder::getId).containsExactly(orderAId);
        // Bound context TENANT_B, but explicit param asks for TENANT_A's data -- the @TenantId
        // filter and the explicit param must both agree, so a mismatch returns nothing rather
        // than leaking tenant A's order to a request authenticated as tenant B.
        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantId(TENANT_A))).isEmpty();
    }

    @Test
    void findByTenantId_paginated_returnsOnlyTheOwningTenantsOrders() {
        seed();

        assertThat(readAs(TENANT_A,
                () -> kitchenOrderRepository.findByTenantId(TENANT_A, PageRequest.of(0, 10)).getContent()))
                .extracting(KitchenOrder::getId).containsExactly(orderAId);
        assertThat(readAs(TENANT_B,
                () -> kitchenOrderRepository.findByTenantId(TENANT_A, PageRequest.of(0, 10)).getContent()))
                .isEmpty();
    }

    @Test
    void findByIdAndTenantId_doesNotResolveAnotherTenantsOrder() {
        seed();

        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByIdAndTenantId(orderAId, TENANT_B)))
                .isEmpty();
        assertThat(readAs(TENANT_A, () -> kitchenOrderRepository.findByIdAndTenantId(orderAId, TENANT_A)))
                .isPresent();
    }

    @Test
    void findByTenantIdAndSessionId_doesNotResolveAnotherTenantsSession() {
        seed();

        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_B, "sess-1")))
                .hasValueSatisfying(o -> assertThat(o.getId()).isEqualTo(orderBId));
        assertThat(readAs(TENANT_B, () -> kitchenOrderRepository.findByTenantIdAndSessionId(TENANT_A, "sess-1")))
                .isEmpty();
    }

    @Test
    void findByTenantIdAndItems_Status_returnsOnlyTheOwningTenantsOrders() {
        seed();

        List<KitchenOrder> result = readAs(TENANT_A,
                () -> kitchenOrderRepository.findByTenantIdAndItems_Status(TENANT_A, OrderItemStatus.PENDING));
        assertThat(result).extracting(KitchenOrder::getId).containsExactly(orderAId);

        assertThat(readAs(TENANT_B,
                () -> kitchenOrderRepository.findByTenantIdAndItems_Status(TENANT_A, OrderItemStatus.PENDING)))
                .isEmpty();
    }
}
```

- [ ] **Step 2: Run the test**

Run: `cd backend && ./mvnw test -Dtest=KitchenOrderRepositoryTenantIsolationTest`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/com/vanter/ember/kitchen/repository/KitchenOrderRepositoryTenantIsolationTest.java
git commit -m "test(backend): rewrite KitchenOrderRepositoryTenantIsolationTest on AbstractTenantIsolationTest"
```

---

### Task 12: Remove the MongoDB dependency and all its config

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/resources/application-prod.properties`
- Modify: `backend/src/main/resources/application-dev.properties`
- Modify: `backend/src/test/resources/application.properties`
- Modify: `.env.example`
- Modify: `CLAUDE.md`

**Interfaces:**
- None — pure removal, no code depends on Mongo anymore after Tasks 3–11.

- [ ] **Step 1: Remove the two Mongo dependencies from `pom.xml`**

Delete these two `<dependency>` blocks (found via the `mongodb`/`flapdoodle` matches from earlier — exact line numbers will have shifted, match on `artifactId`):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

```xml
<dependency>
    <groupId>de.flapdoodle.embed</groupId>
    <artifactId>de.flapdoodle.embed.mongo.spring30x</artifactId>
    <version>4.11.0</version>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Remove Mongo config from `application.yml`**

In the `spring.data` block, delete the `mongodb` sub-block (keep `web.pageable` — it's unrelated):

```yaml
  data:
    mongodb:
      # No fallback: the URI embeds the Mongo credentials.
      uri: ${SPRING_DATA_MONGODB_URI}
    web:
```

becomes:

```yaml
  data:
    web:
```

- [ ] **Step 3: Remove Mongo config from `application-prod.properties` and `application-dev.properties`**

Delete the line `spring.data.mongodb.uri=${SPRING_DATA_MONGODB_URI}` from both files.

- [ ] **Step 4: Remove Mongo config from the test properties**

In `backend/src/test/resources/application.properties`, delete these two lines:

```properties
# Spring Data MongoDB URI for embedded instance (flapdoodle overrides host/port at runtime)
spring.data.mongodb.uri=mongodb://localhost:27017/ember-test
```

```properties
# Embedded MongoDB version for @DataMongoTest (flapdoodle)
de.flapdoodle.mongodb.embedded.version=7.0.2
```

- [ ] **Step 5: Remove `SPRING_DATA_MONGODB_URI` from `.env.example`**

Delete the line `SPRING_DATA_MONGODB_URI=mongodb://ember:ember@localhost:27017/ember?authSource=admin`, and update the comment line above it (currently lists it among the required secrets) to drop the reference.

- [ ] **Step 6: Update `CLAUDE.md`'s stale "Hybrid Persistence" section**

In the `### Tech Stack & Persistence` section, replace:

```markdown
- **Hybrid Persistence:**
  - **PostgreSQL (JPA):** `identity`, `catalog`, `billing`, `settings`, `restaurant`.
  - **MongoDB:** `session` (documents with embedded participants), `kitchen` (embedded orders and items).
```

with:

```markdown
- **Persistence:** PostgreSQL (JPA) for every module — `identity`, `catalog`, `billing`, `settings`, `restaurant`, `session`, `kitchen`. `session`/`kitchen` moved off MongoDB in the `ember-postgress-migration` branch; their embedded arrays (participants, order items) now live in JSON columns via Hibernate's `@JdbcTypeCode(SqlTypes.JSON)`.
```

Also update:

```markdown
- **Event Handling:** 100% internal synchronous communication via Spring `ApplicationEventPublisher` and `@EventListener`. **DO NOT use or configure Kafka** (the dependency in `pom.xml` should be ignored or removed).
```

leave as-is (unrelated to this migration — Kafka was already flagged as unused elsewhere; don't conflate the two cleanups in one task).

- [ ] **Step 7: Confirm no remaining Mongo references anywhere in the backend**

Run: `cd backend && grep -rln "mongo\|Mongo\|MONGO" src pom.xml`
Expected: no output.

- [ ] **Step 8: Run the full backend test suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, full suite (the app now boots with zero Mongo auto-configuration).

- [ ] **Step 9: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/application-prod.properties backend/src/main/resources/application-dev.properties backend/src/test/resources/application.properties .env.example CLAUDE.md
git commit -m "chore(backend): remove mongodb dependency and config, fully on postgres now"
```

---

### Task 13: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full backend test suite one more time from a clean build**

Run: `cd backend && ./mvnw clean test`
Expected: PASS, same test count as before this branch started (check `PROGRESS.md`'s last recorded count — 797/797 as of `emb-i18n-08` — new tests added in Tasks 4 and 8 should bring it to 799, minus whatever `MongoTenantBackfillTest`/`KitchenOrderActiveBackfillTest` contributed before Tasks 1/2 deleted them).

- [ ] **Step 2: Run the E2E order flow test specifically**

Run: `cd backend && ./mvnw test -Dtest=E2EOrderFlowTest`
Expected: PASS — this is a `@SpringBootTest` (full context) exercising the real session→kitchen→billing flow through `MockMvc`; it never referenced Mongo types directly, so it should pass unmodified once the full context boots on Postgres/H2 alone.

- [ ] **Step 3: Confirm the frontend build is unaffected**

Run: `cd frontend && pnpm run build`
Expected: PASS — this migration is backend-only, no API request/response shapes changed (DTOs, `OrderItemStatus`/`SessionStatus` JSON serialization, and every endpoint contract are untouched), so this is a sanity check, not an expected source of new errors.

- [ ] **Step 4: Manual boot check against a real local Postgres**

Run: `cd backend && ./mvnw spring-boot:run` (with your local `.env` pointed at a real Postgres, `SPRING_DATA_MONGODB_URI` removed) and confirm the app boots cleanly, `V15` shows applied in the `flyway_schema_history` table, and hitting a session/kitchen endpoint (e.g. via the frontend dev server) round-trips correctly. This is the one step no automated test covers — a live boot against real Postgres, not H2.

- [ ] **Step 5: Update `PROGRESS.md`**

Add a bullet to **Active Context & Recent Decisions** documenting: `session`/`kitchen` are now Postgres/JPA, not MongoDB; `MongoTenantBackfill`/`KitchenOrderActiveBackfill` are gone; `Session` deliberately has no `@TenantId` (untenanted `findByJoinCodeAndStatus`) while `KitchenOrder` does. Update **Current Execution State** to reflect the branch and that Vanter Hub planning is paused pending this migration's completion.

- [ ] **Step 6: Final commit**

```bash
git add PROGRESS.md
git commit -m "docs: record postgres migration completion in PROGRESS.md"
```
