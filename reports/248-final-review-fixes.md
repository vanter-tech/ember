# Report 248: Final Review Fix Wave — hub-license-activation

## 1. Identification
- **Report Number:** 248
- **Task ID:** Final review fix wave
- **Predecessor:** Report 247 (task-9-hide-registrarse-hub-build)

## 2. Objective
Fix all findings from the final whole-branch review of the completed 10-task `docs/superpowers/plans/2026-08-25-hub-license-activation.md` plan (spec `docs/superpowers/specs/2026-08-25-hub-license-activation-design.md`), covering one Critical persistence bug, one unintentionally-exposed cloud-only endpoint, JSON forward-compatibility/error-safety hardening, rate limiting, a doc correction, and two docs-only corrections in `PROGRESS.md`/a stale report.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java`
- `backend/src/main/java/com/vanter/ember/hub/provisioning/HubProvisioningRunner.java`
- `backend/src/test/java/com/vanter/ember/hub/provisioning/HubProvisioningRunnerTest.java`
- `backend/src/test/java/com/vanter/ember/restaurant/repository/RestaurantRepositoryInsertWithIdTest.java` (new)
- `backend/src/main/java/com/vanter/ember/licensing/controller/HubActivationController.java`
- `backend/src/main/java/com/vanter/ember/config/RateLimitProperties.java`
- `.env.example`
- `PROGRESS.md`
- `reports/246-task-8-console-license-button.md`
- `reports/248-final-review-fixes.md` (this file)

## 4. What Changed?

### Finding 1 — Restaurant seeding was broken (CRITICAL)
`Restaurant.builder().id(state.restaurantId())...build()` followed by `restaurantRepository.save(...)` throws against real Hibernate: with a non-null `@GeneratedValue(strategy = GenerationType.UUID)` id already set, Spring Data's `save()` calls `entityManager.merge()` (not `persist()`), and merge finds no existing row to merge into.

Fix:
- `RestaurantRepository` gained `insertWithId(UUID id, String name, String slug)` — a native `@Modifying @Query` insert that bypasses Hibernate's `UuidGenerator` entirely, hardcoding the same defaults (`plan='FREE'`, `status='ACTIVE'`, `timezone='UTC'`, `currency='USD'`, `created_at=now()`) the entity's own `@Builder.Default`s would have applied. Column list verified against `V1__baseline_consolidated.sql`'s `CREATE TABLE public.restaurants`.
- `HubProvisioningRunner.run()` now calls a new `seedRestaurantAndAdmin(UUID, ActivationResponseBody)` method: `insertWithId(...)` then `findById(...)` to get back a real managed `Restaurant`, then the admin `User` save — all wrapped in one atomic unit.
- **Deviation from the finding's literal instructions, deliberately:** the finding asked for `@Transactional` on this package-private method. `run()` calls it via `this.seedRestaurantAndAdmin(...)` — a self-invocation that bypasses Spring AOP's CGLIB proxy entirely, so a plain `@Transactional` annotation there would silently run with **no transaction at all** (well-established, documented Spring AOP behavior — self-invocation never goes through the proxy regardless of the target method's visibility). Instead, `HubProvisioningRunner` now takes a `PlatformTransactionManager` constructor dependency and wraps the seeding logic in a `TransactionTemplate`, which demarcates the transaction programmatically and is immune to self-invocation. This achieves the same atomicity goal (insert+findById+admin-save roll back together on any failure) without the silent-no-op risk.
- New test `RestaurantRepositoryInsertWithIdTest` (`@DataJpaTest`, `@Import(TenantIdentifierResolver.class)` — required project-wide per the established `@DataJpaTest` gotcha even though `Restaurant` has no `@TenantId`) exercises `insertWithId` + `findById` against a real Hibernate/H2 session — this is the test shape that would have caught the original bug; `HubProvisioningRunnerTest` mocks the repository and cannot.
- `HubProvisioningRunnerTest` updated: constructor now takes a mocked `PlatformTransactionManager` (stubbed `getTransaction(any())` returning a mocked `TransactionStatus`); assertions switched from `verify(restaurantRepository).save(...)` to `verify(restaurantRepository).insertWithId(...)` + a stubbed `findById(...)` return.

### Finding 2 — `/hub-activations` unintentionally exposed on every Hub's LAN
`HubActivationController` had no profile gate, so a Hub install (running the same jar) also booted this cloud-only controller, exposing an unauthenticated `POST /hub-activations` on its own local network (previously fails closed only by accident, since a Hub has no `HUB_LICENSE_PRIVATE_KEY`). Added `@Profile("!hub")` plus a comment explaining why. Confirmed `HubActivationControllerTest` (a `@WebMvcTest`, activates no profile by default) still passes unchanged.

### Finding 3 — JSON forward-compatibility and error-message safety
- `MAPPER` now `.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)` so a future cloud-side response field addition doesn't break already-deployed Hubs.
- `Files.readString(properties.licenseFile())` is now wrapped in try/catch, rethrown as `HubProvisioningException("No se pudo leer license.key para activar.", e)`.
- `MAPPER.readValue(response.body(), ActivationResponseBody.class)` is now wrapped in try/catch; the raw parse exception (which could embed a JSON snippet containing `adminPasswordHash`) is logged via `log.error` (stack trace only, not surfaced) and rethrown as `HubProvisioningException("La respuesta de activación no es válida.", e)` — no response-body content reaches the constructed message.

### Finding 4 — Rate-limit the activation endpoint
Added `"/hub-activations"` to `RateLimitProperties.paths`, alongside `/auth/login`, `/auth/register`, `/platform/auth/login` — same risk profile (unauthenticated, does RSA verification + DB reads per request).

### Finding 5 — `.env.example` overstated `HUB_LICENSE_PRIVATE_KEY`'s fail-fast guarantee
Removed `HUB_LICENSE_PRIVATE_KEY` from the "app refuses to boot without them" sentence (verified `LicenseIssuingService` is `@Lazy`) and added a clause: it's validated lazily on first use (issuing a Hub license), not at boot — see `LicenseIssuingService`. `MINIO_SECRET_KEY` and the other vars in that sentence are unchanged (still genuinely fail-fast).

### Finding 6 — `PROGRESS.md` checkbox/staleness gaps
- Line 160's "Design gap, not started" bullet is now `[x]`, rewritten to state the gap is closed by the 10-task plan (Tasks 1–9), referencing reports 239–247 and this fix wave.
- Added a new "**Ember Hub — hub-license-activation**" checkbox block to Task Queue Status: Tasks 1–9 checked (referencing reports 239–247), Task 10 (manual e2e verification) unchecked, plus this fix wave checked (report 248).
- Added one new "Current Execution State" bullet (report 248) summarizing this fix wave, separate from the Task Queue Status edits.
- Updated the System health bullet with the genuinely-observed final counts (see §"Verification" below). File is 176 lines, under the 180-line limit.

### Finding 7 — stale/self-contradicting `reports/246-...md`
Corrected the "Test suite timed out" claim to the genuinely-observed result (36/36 PASS, 61.15s), matching `PROGRESS.md`'s own report-246 bullet. Added a "Commit" subsection citing the final SHA `09e7909` (the report contained no prior SHA reference to overwrite — none was found via `git log`/grep, so this adds the correct one rather than replacing a wrong one).

## 5. Why It Changed?

Finding 1 is the load-bearing fix: without it, every real Hub install's first boot would throw during provisioning and never seed a usable Restaurant/admin User — the entire plan's stated goal ("a Hub instance calls a cloud endpoint once on first boot to fetch its restaurant+admin data and seed its local database") would be non-functional in production despite all 831 prior tests passing, because no existing test exercised real Hibernate persistence for this path. The `TransactionTemplate` substitution for `@Transactional` exists because Spring's self-invocation limitation would have silently defeated the atomicity guarantee the finding asked for — using it as literally specified would have looked correct in code review while doing nothing at runtime.

Findings 2–5 close real, if lower-severity, gaps: an accidentally-fails-closed unauthenticated endpoint on customer LANs, a response schema that can't evolve without breaking deployed Hubs, technical (non-Spanish, potentially secret-leaking) error text reaching users, missing throttling on an endpoint that does real work per request, and documentation that no longer matched the code's actual (lazy) validation behavior.

Findings 6–7 are documentation hygiene: keeping `PROGRESS.md`'s Task Queue Status and Current Execution State accurate (per CLAUDE.md §6's mandatory schema) and correcting a report that would otherwise mislead a future reader about Task 8's actual, passing, test result and final commit.

## Verification
- `cd backend && ./mvnw test -Dtest=HubProvisioningRunnerTest,RestaurantRepositoryInsertWithIdTest,HubActivationControllerTest` — **10/10 PASS**, genuinely observed.
- `cd backend && ./mvnw test` (full suite) — **832/832 PASS**, 0 failures/errors (831 prior + 1 new `RestaurantRepositoryInsertWithIdTest`), genuinely observed, ~4m29s.
- `cd frontend && pnpm run test:run` — **36/36 PASS**, genuinely observed, 12.54s.
- `cd frontend && pnpm run build` — **PASS** (tsc -b + vite build), genuinely observed.
