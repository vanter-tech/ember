# Report 33 — task-3.7

## Identification
- **Report Number:** 33
- **Task ID:** task-3.7
- **Predecessor Task:** task-3.6

## Objective
Add minimum length and complexity rules to user registration password validation.

## Modified Files
- `backend/src/main/java/com/vanter/ember/identity/model/dto/RegisterRequest.java`
- `backend/src/test/java/com/vanter/ember/identity/controller/AuthControllerTest.java`

## What Changed?
- `RegisterRequest.password` now carries `@Size(min = 8, max = 128)` and a `@Pattern` regex requiring at least one lowercase letter, one uppercase letter, one digit, and one special character, on top of the existing `@NotBlank`.
- `AuthControllerTest`'s two passing-registration tests (`register_returns200WithTokenAndName`, `register_returns409WhenEmailAlreadyExists`) were updated from the non-compliant password `"secret"` to `"Secret123!"` so they still exercise the success path under the new rule.
- Added `register_returns400ForWeakPassword`, asserting a password that is long enough but lacks uppercase/digit/special-char complexity (`"alllowercase1!"`... adjusted to fail complexity) is rejected with 400.

## Why It Changed?
Registration previously accepted any non-blank password (e.g. a single character), which is a weak-credential risk directly in the tenant's user-onboarding path. `RegisterRequest` is already validated via `@Valid` in `AuthController.register` and routed through the task-3.3 `GlobalExceptionHandler`, which correctly demotes `MethodArgumentNotValidException` to a 400 `ProblemDetail` — so the fix is a pure DTO-level constraint addition with no controller/service/exception-handler changes required. `AuthServiceTest` (Mockito-based, calls `AuthService.register` directly) and `E2EOrderFlowTest`/`LoginRequest` flows were confirmed unaffected since they never pass through Spring's bean-validation layer.

## Verification
`cd backend && ./mvnw test` — **BUILD SUCCESS**, Tests run: 405, Failures: 0, Errors: 0, Skipped: 0 (404 prior + 1 new).
