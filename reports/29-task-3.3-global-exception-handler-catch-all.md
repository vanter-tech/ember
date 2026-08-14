# Report 29 — task-3.3: `GlobalExceptionHandler` catch-all with standardized `ProblemDetail`

## 1. Identification
- **Report Number:** 29
- **Task ID:** task-3.3
- **Predecessor Task:** task-3.2 (report 28 — remove unused `spring-kafka` dependency)

## 2. Objective
Extend `GlobalExceptionHandler` with a last-resort `@ExceptionHandler(Exception.class)` so no exception ever escapes as a raw Spring Boot error page, and normalize every handler onto a single RFC 7807 `ProblemDetail` shape (`type`, `title`, `status`, `detail`, `instance`, plus `traceId` on 500s).

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/config/GlobalExceptionHandler.java` (modified)
- `backend/src/test/java/com/vanter/ember/config/GlobalExceptionHandlerTest.java` (new)

## 4. What Changed?

### `GlobalExceptionHandler`
1. **Now extends `ResponseEntityExceptionHandler`.** This registers Spring's built-in handlers for the ~20 standard MVC exceptions (`BindException`, `TypeMismatchException`, `HttpMessageNotReadableException`, `ErrorResponseException`/`ResponseStatusException`, `NoResourceFoundException`, `MissingServletRequestParameterException`, `MaxUploadSizeExceededException`, …), each of which is *more specific* than `Exception.class` and therefore wins handler resolution against the new catch-all.
2. **Catch-all `@ExceptionHandler(Exception.class)`** → `500`. Generates an opaque `traceId` (UUID), logs `traceId` + HTTP method + URI + the full stack trace at `ERROR`, and returns a fixed generic `detail` with the `traceId` as an extension property. The original exception message never reaches the client.
3. **`AccessDeniedException` → `403`** (new). Previously these propagated out of the `DispatcherServlet` to Spring Security's `ExceptionTranslationFilter`; the catch-all now shadows that path, so the handler reproduces the 403 explicitly — and adds a JSON body where there was none.
4. **`AuthenticationException` → `401`** (new). Same reasoning: preserves the 401 for authentication failures raised *inside* a controller. The pre-existing `BadCredentialsException` handler is more specific and still wins for that subtype.
5. **`MethodArgumentNotValidException`**: the standalone `@ExceptionHandler` method was replaced by an override of `handleMethodArgumentNotValid(...)`. Declaring both would have been an ambiguous mapping (`ResponseEntityExceptionHandler` already claims that type) and failed at context startup. Behaviour is unchanged — `detail` is still the first binding error's default message — with an `orElse("Validation failed")` guard for the empty-error case.
6. **`problem(status, detail, path)` helper**: every handler now routes through it, setting `title` from the status reason phrase and `instance` from the request URI. All existing status codes (404 / 409 / 409 / 409 / 401 / 400) are unchanged.

### `GlobalExceptionHandlerTest` (12 tests)
Standalone `MockMvc` (`standaloneSetup(...).setControllerAdvice(new GlobalExceptionHandler())`) driving a nested `ThrowingController`. Covers: the 500 body shape (`status`/`title`/`detail`/`instance`/`traceId`) and `application/problem+json` content type; that the internal message (a fake JDBC URL) is *not* echoed; the 404/403/401/409 mappings; and four regression tests asserting the catch-all does **not** shadow `ResponseStatusException` (404), path-variable type mismatch (400), bean validation (400 with the first message), or unreadable JSON (400).

## 5. Why It Changed?
- **Information disclosure:** any unhandled exception previously surfaced through Spring Boot's default `/error` handling, which can leak exception class names, messages and — depending on configuration — stack traces. The catch-all replaces that with a constant, non-revealing body while keeping the diagnostic detail server-side, correlated by `traceId`.
- **Contract consistency:** the frontend received a mix of RFC 7807 documents (mapped exceptions), Boot's default error JSON (unmapped exceptions), and empty bodies (403 from the security filter). Every error response is now the same document shape.
- **Extending `ResponseEntityExceptionHandler` was mandatory, not cosmetic.** A bare `@ExceptionHandler(Exception.class)` in a `@RestControllerAdvice` is resolved by `ExceptionHandlerExceptionResolver`, which runs *before* `ResponseStatusExceptionResolver` and `DefaultHandlerExceptionResolver`. Without the superclass's specific handlers, `BindException` (e.g. `CategoryControllerTest.create_returns400ForBlankName`), `ResponseStatusException` (thrown by `SessionService.findByJoinCode`) and malformed-JSON 400s would all have silently degraded to 500.
- **Guarding `AccessDeniedException` explicitly** was likewise required: ~25 existing controller tests assert 403 from `@PreAuthorize` denials, which the catch-all would otherwise have converted to 500 — masking a security control as a server fault.

## 6. Verification
`cd backend && ./mvnw test` → **BUILD SUCCESS**, `Tests run: 379, Failures: 0, Errors: 0, Skipped: 0` (367 pre-existing + 12 new).
