# Report 239: Task 1 — HubActivation Entity, Repository, and Migration

## 1. Identification
- **Report Number:** 239
- **Task ID:** Task 1: HubActivation entity, repository, and migration
- **Predecessor Task:** None (first task of Hub license activation feature)
- **Branch:** `emb-i18n-08`

## 2. Objective
Implement the foundational JPA entity, Spring Data repository, and Flyway migration for tracking Hub license activations on the server side. This enables Task 4's `HubActivationService` to distinguish legitimate retries (same hardware) from license copies (different hardware), layering server-side validation on top of the client-side hardware lock already implemented by `LicenseService`.

## 3. Modified Files
- **Created:** `backend/src/main/java/com/vanter/ember/licensing/model/HubActivation.java`
- **Created:** `backend/src/main/java/com/vanter/ember/licensing/repository/HubActivationRepository.java`
- **Created:** `backend/src/main/resources/db/migration/V2__hub_activations.sql`
- **Created:** `backend/src/test/java/com/vanter/ember/licensing/repository/HubActivationRepositoryTest.java`

## 4. What Changed?
Three new files implement the Hub activation persistence layer:

1. **HubActivation.java:** JPA entity with Lombok `@Data`/`@Builder` annotations, mapping fields:
   - `UUID id` (UUID-generated primary key)
   - `UUID restaurantId` (unique, non-null, server's marker for a single Hub installation)
   - `String hardwareFingerprint` (non-null, paired with restaurantId to detect hardware changes)
   - `Instant activatedAt` (timestamp of the initial activation)

2. **HubActivationRepository.java:** Spring Data `JpaRepository<HubActivation, UUID>` with one query method:
   - `findByRestaurantId(UUID restaurantId): Optional<HubActivation>` — used by Task 4's `HubActivationService` to check if this restaurant/hardware pair has already activated

3. **V2__hub_activations.sql:** Flyway migration creating the `hub_activations` table with:
   - Primary key on `id`
   - Unique constraint on `restaurant_id` (enforces one activation per restaurant at the DB level)
   - Non-null columns for all fields
   - Standard PostgreSQL `uuid` and `timestamp(6) with time zone` types

4. **HubActivationRepositoryTest.java:** Two unit tests under `@DataJpaTest`:
   - `findByRestaurantId_returnsEmptyWhenNoneExists()` — verifies no false positives
   - `findByRestaurantId_returnsSavedActivation()` — verifies persistence and retrieval
   - Includes `@Import(TenantIdentifierResolver.class)` — required when `@DataJpaTest` scans project-wide entities that use `@TenantId` (PROGRESS.md note: per report 56, this import is mandatory even when the entity under test has no `@TenantId` of its own)

## 5. Why It Changed?
This task lays the groundwork for the Ember Hub license activation feature (spec `docs/superpowers/specs/2026-08-25-hub-license-activation-design.md` §4.3). A Hub installation activates its license against the cloud backend once (either online or deferred via periodic sync), and the server records the activation pairing `(restaurantId, hardwareFingerprint)`. On future heartbeat or license-check requests, Task 4's `HubActivationService` will:
1. Check if the restaurant has an activation record
2. Verify the hardware fingerprint matches
3. Allow re-activation only if the fingerprint is identical (same PC after a local DB wipe)
4. Reject mismatched fingerprints (license copied to a different machine)

This persistence layer is a clean, reusable foundation — no existing files were modified, no dependencies added, follows established conventions (Lombok builders, Spring Data repositories, Flyway migrations, `@DataJpaTest` patterns from other modules).

Test results: 2/2 new tests PASS. Full backend suite: 809/809 PASS (807 pre-existing + 2 new).
