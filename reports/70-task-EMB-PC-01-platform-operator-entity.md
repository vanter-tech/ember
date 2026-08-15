# Report 70 — EMB-PC-01: PlatformOperator entity, repository and seed migration

## 1. Identification
- **Report:** 70
- **Task ID:** EMB-PC-01
- **Predecessor Task:** feature-pagination-floating-style (report 69)

## 2. Objective
Lay the first brick of the Platform/Super-Admin Console backlog: a `PlatformOperator` JPA entity + repository + Flyway migration for a `platform_operators` table, deliberately isolated from the tenant data model (no FK to `Restaurant`/`User`), seeded with an initial operator row.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/PlatformOperator.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/repository/PlatformOperatorRepository.java` (new)
- `backend/src/main/resources/db/migration/V4__platform_operators.sql` (new)
- `backend/src/test/java/com/vanter/ember/platform/repository/PlatformOperatorRepositoryTest.java` (new)

## 4. What Changed?
- `PlatformOperator`: `id` (UUID, Hibernate-generated), `name`, `email` (unique), `passwordHash` (`@JsonIgnore`), `createdAt` (`@PrePersist`) — same Lombok (`@Data`/`@Builder`) shape as `User`/`Restaurant`, but with no `@ManyToOne` to either and no `@TenantId`.
- `PlatformOperatorRepository`: bare `JpaRepository<PlatformOperator, UUID>` plus `findByEmail`.
- `V4__platform_operators.sql`: creates the table, a unique constraint on `email`, and seeds one row (`platform-admin@ember.local`, name "Platform Admin") with a bcrypt hash for password `ChangeMe123!` generated locally via a throwaway JUnit test that called the same `BCryptPasswordEncoder` `SecurityConfig` wires up (temp test file deleted after capturing the hash — not part of the final diff). The insert is `ON CONFLICT (email) DO NOTHING` so re-running the migration on a database that already has the row is a no-op rather than a constraint-violation failure.
- `PlatformOperatorRepositoryTest`: a `@DataJpaTest` needing `@Import(TenantIdentifierResolver.class)` to boot at all — the Hibernate `SessionFactory` is configured for multi-tenancy globally, so every repository bean in the test slice (not just this one) fails to initialize without a resolver present, even though `PlatformOperator` itself never touches tenant context. Tests insert their own rows rather than asserting on the migration's seed row, because `spring.flyway.enabled=false` in the H2 test profile (`application.properties`) — migrations are Postgres-only and never run under `./mvnw test`.

## 5. Why It Changed?
EMB-PC-01 is the first backlog item for the Platform/Super-Admin Console (brainstormed 2026-08-13, PROGRESS.md's EMB-PC-01–14): everything downstream (EMB-PC-03's `PlatformJwtService`/`PlatformOperatorDetailsService`, EMB-PC-05's login/password-change endpoints) needs this table and repository to exist first. Isolating it from `Restaurant`/`User` (no FK, no `@TenantId`) is deliberate per the brainstormed design: mutual exclusion between platform and tenant auth comes from a separate signing key (`platform.jwt.secret`, EMB-PC-03/04), not a claim or relational check.

## Verification
- `./mvnw test`: PASSING, 527/527 (523 baseline + 4 new `PlatformOperatorRepositoryTest` cases).
- Since Flyway is disabled in the test profile, the migration itself was verified separately: ran `./mvnw spring-boot:run` against the real Postgres instance (`ember-postgres-1`, already running via `docker-compose`) — boot log confirms `Migrating schema "public" to version "4 - platform operators"` / `Successfully applied 1 migration ... now at version v4`; `docker exec ember-postgres-1 psql` confirms the table shape (unique constraint on `email`) and the seeded row (`platform-admin@ember.local`, hash starting `$2a$10$`). The app itself failed to fully start only because port 8080 was already occupied by an unrelated pre-existing process — irrelevant to migration correctness, and that process was left untouched since this session didn't start it.
