# Report 71

## Identification
- **Report Number:** 71
- **Current Task ID:** EMB-PC-02
- **Predecessor Task:** EMB-PC-01 (report 70)

## Objective
Add the `PlatformAuditLog` JPA entity, its repository, and the Flyway migration for the `platform_audit_log` table, so future platform-console mutation endpoints (EMB-PC-07/08) have somewhere to write audit entries and EMB-PC-09 has something to list.

## Modified Files
- `backend/src/main/java/com/vanter/ember/platform/model/PlatformAuditLog.java` (new)
- `backend/src/main/java/com/vanter/ember/platform/repository/PlatformAuditLogRepository.java` (new)
- `backend/src/main/resources/db/migration/V5__platform_audit_log.sql` (new)
- `backend/src/test/java/com/vanter/ember/platform/repository/PlatformAuditLogRepositoryTest.java` (new)

## What Changed?
- `PlatformAuditLog`: `id` (UUID, generated), `operatorId` (UUID, not null, no FK), `operatorEmail` (snapshot string, not null), `restaurantId` (UUID, nullable, no FK), `action` (string, not null), `oldValue`/`newValue` (nullable `text` columns), `createdAt` (`Instant`, set via `@PrePersist`).
- `PlatformAuditLogRepository`: bare `JpaRepository<PlatformAuditLog, UUID>`, no custom finders yet.
- `V5__platform_audit_log.sql`: creates `platform_audit_log` with the above columns plus a `btree` index on `restaurant_id` (for EMB-PC-09's future `restaurantId` filter), no foreign keys.
- Test mirrors `PlatformOperatorRepositoryTest`'s pattern: `@DataJpaTest` + `@Import(TenantIdentifierResolver.class)` (required for the multi-tenant `SessionFactory` to let any repository bean boot in the slice), 3 cases covering persist/generated-fields, nullable `restaurantId`, and `findById`.

## Why It Changed?
`PlatformOperator`/`PlatformAuditLog` are deliberately outside the tenant data model (per the EMB-PC backlog decision recorded in `PROGRESS.md`): no FK to `Restaurant`/`User`, no `@TenantId`, mutual exclusion from tenant auth comes from a separate signing key (EMB-PC-03/04), not a claim check — so this table must never be joined against tenant-scoped tables. `operatorId`/`operatorEmail` are stored as a snapshot rather than a live FK so audit entries stay legible even if the operator row changes or is removed later. `restaurantId` is nullable since not every future platform action targets a specific tenant.

## Verification
- `./mvnw test`: **530/530 passing** (up from 527; +3 new `PlatformAuditLogRepositoryTest` cases), `BUILD SUCCESS`.
- Migration verified against the real `ember-postgres-1` Postgres container (H2 test profile has `spring.flyway.enabled=false`, so `./mvnw test` never runs migrations): started `./mvnw spring-boot:run`, confirmed `Migrating schema "public" to version "5 - platform audit log"` / `Successfully applied 1 migration`, then confirmed via `psql \d platform_audit_log` and `flyway_schema_history` that the table, its index, and column types/nullability match the entity.
- `pnpm run build`/`pnpm run lint`: not run — no frontend files touched this task; frontend state unchanged from report 70 (`pnpm run build` last verified passing; lint has 18 pre-existing unrelated errors).
