# Report 18 — task-2.10

## Identification
- **Report #:** 18
- **Task ID:** task-2.10
- **Predecessor Task:** task-2.9 (report 17)

## Objective
Flesh out the `Restaurant` entity and add `RestaurantRepository`/`RestaurantService`, then update registration (`RegisterRequest`/`AuthService`) to create-or-join a `Restaurant` and bind every `User.restaurantId` explicitly, adding the tenant id as a `rid` JWT claim. This replaces the interim regression accepted in task-2.9, where no registration path ever set `User.restaurantId`.

## Modified Files
- `backend/src/main/java/com/vanter/ember/restaurant/model/Restaurant.java`
- `backend/src/main/java/com/vanter/ember/restaurant/model/RestaurantPlan.java` (new)
- `backend/src/main/java/com/vanter/ember/restaurant/model/RestaurantStatus.java` (new)
- `backend/src/main/java/com/vanter/ember/restaurant/repository/RestaurantRepository.java` (new)
- `backend/src/main/java/com/vanter/ember/restaurant/service/RestaurantService.java` (new)
- `backend/src/main/java/com/vanter/ember/identity/model/dto/RegisterRequest.java`
- `backend/src/main/java/com/vanter/ember/identity/service/AuthService.java`
- `backend/src/main/java/com/vanter/ember/session/service/SessionService.java`
- `backend/src/main/java/com/vanter/ember/session/controller/SessionController.java`
- `backend/src/test/java/com/vanter/ember/identity/service/AuthServiceTest.java`
- `backend/src/test/java/com/vanter/ember/session/controller/SessionControllerTest.java`
- `backend/src/test/java/com/vanter/ember/session/service/SessionServiceTest.java`
- `backend/src/test/java/com/vanter/ember/E2EOrderFlowTest.java`
- `PROGRESS.md`

## What Changed?
- `Restaurant` gained `name`, unique `slug`, `plan` (`RestaurantPlan`: FREE/STARTER/PRO/ENTERPRISE), `status` (`RestaurantStatus`: ACTIVE/SUSPENDED/INACTIVE), `timezone`, `currency`, and `createdAt`.
- Added `RestaurantRepository` (`findBySlug`, `existsBySlug`) and `RestaurantService.createOrJoin(name, slug, fallbackNameSeed)`: joins an existing restaurant by slug if one is given (404-equivalent `IllegalArgumentException` if not found), otherwise auto-creates one with a unique, slugified name.
- `RegisterRequest` gained optional `restaurantName`/`restaurantSlug` fields.
- `AuthService.register` now resolves/creates the `Restaurant` via `RestaurantService`, sets `User.restaurantId`, and adds a `rid` claim (restaurant UUID) to the JWT. `AuthService.login` now also adds the `rid` claim, sourced from the persisted user's restaurant.
- Fixed two bugs surfaced while verifying against `E2EOrderFlowTest` (previously masked entirely because that test's `@BeforeEach` failed before the test body ever ran, due to the `restaurant_id NOT NULL` constraint this task fixes):
  - `SessionService.joinSession` (QR-code join) was storing the participant's **email** as `Participant.userId`, while every other path (`joinSessionCode`, `addItem`, `confirmDraftsForUser`) compares against the resolved `User.getId()`. It now resolves the user by email first and stores the real id, matching the rest of the codebase.
  - `SessionController.getSession`'s customer-participant check compared `participant.userId()` against the raw JWT subject (email) instead of the resolved user id — now resolves the id via `UserRepository` first, consistent with the fix above.
- Test-only fixes in `E2EOrderFlowTest`: corrected `sessionId` vs `id` JSON key mismatch in the create-session assertion, set `itemReq.setAvailable(true)` (previously defaulted to `false`), and added the missing `POST /sessions/{id}/participants/{userId}/confirm` call the DRAFT→PENDING cart flow (task-2.8/2.9) requires before an item reaches the kitchen queue. Also persists a `Restaurant` in `setUp()` and assigns it to all seeded users and the dining table.
- `AuthServiceTest`/`SessionControllerTest`/`SessionServiceTest` updated to mock the new `RestaurantService`/`UserRepository` dependencies these changes introduced.

## Why It Changed?
`User.restaurantId` is `nullable = false`, but no registration path ever populated it, so every self-registered customer denied `confirmMyOrder`/`confirmDraftsForUser` (task-2.9's fail-closed tenant check) and any real insert failed the DB constraint (`E2EOrderFlowTest`). This task wires actual tenant provisioning at registration so the constraint is satisfiable and the tenant checks added in task-2.8/2.9 are meaningful. The `joinSession`/`getSession` participant-identity fixes were pre-existing defects unrelated to tenant scope, but blocked verifying this task's own change against `E2EOrderFlowTest`; both were confirmed by user decision before being fixed (see conversation) rather than left silently patched.
