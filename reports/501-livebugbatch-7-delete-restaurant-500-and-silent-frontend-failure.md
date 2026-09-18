# Report 501

## 1. Identification
- **Report number:** 501
- **Task ID:** LIVE-BUG-BATCH 7/7 — delete-restaurant button does nothing (final task, **LIVE-BUG-BATCH complete**)
- **Predecessor task:** report 500 (bug 6 — ADMIN sees "no open shift" in `/waiter/tables`)

## 2. Objective
Live user bug report: clicking "Confirmar eliminación" with the slug typed correctly on a
`SUSPENDED` restaurant does nothing. User confirmed a live `500 Internal Server Error` with a
`traceId` when this was tried, on `DELETE /v1/platform/restaurants/{id}`.

## 3. Modified Files
- `backend/src/main/resources/db/migration/V13__widen_restaurants_status_check.sql` (new)
- `frontend/src/pages/console/ConsoleRestaurantDetail.tsx`
- `frontend/src/pages/console/ConsoleRestaurantDetail.test.tsx`

## 4. What Changed?
**Root cause (confirmed by inspection, same class of bug as V11's `users_role_check` fix).**
`RestaurantStatus.DELETED` was added to the Java enum back in V8 (alongside `deleted_at`/
`deleted_by`), but the baseline migration's `restaurants_status_check` CHECK constraint
(`ACTIVE`/`SUSPENDED`/`INACTIVE` only) was never widened to allow it. Every `delete()` call reaches
`restaurant.setStatus(DELETED); restaurantRepository.save(restaurant);` and hits a live Postgres
constraint violation — a `DataIntegrityViolationException` that falls through to the generic 500
handler, matching exactly the error the user saw. `updateStatus`/`toggleStatus` (ACTIVE/SUSPENDED)
never hit this because those two values were always allowed.

New `V13__widen_restaurants_status_check.sql`, identical idempotent `DROP CONSTRAINT IF EXISTS` +
`ADD CONSTRAINT` pattern as `V11__add_accountant_role.sql`, adding `DELETED` to the allowed list.
(Originally written as `V12` in this session, before the `CASH-SHIFT-DENOMINATION-COUNT` branch's
own `V12__cash_shift_denomination_breakdown.sql` merged to `main` first and claimed that version
number — renumbered to `V13` when reconciling both branches.)

**Why no new backend test could catch this:** confirmed `backend/src/test/resources/application.properties`
runs the entire test suite against H2 with `spring.jpa.hibernate.ddl-auto=create-drop` and
`spring.flyway.enabled=false` — its own comment says "Migrations are PostgreSQL-specific; the H2
test schema is generated from the entities instead." Hand-written SQL `CHECK` constraints are
therefore invisible to the whole test suite by design; `RestaurantRepositorySoftDeleteTest.java`
already persists a `DELETED` restaurant successfully today (against H2), which is exactly why this
real-Postgres-only bug was never caught by CI. This migration's correctness can only be confirmed
against a real Postgres instance (same as V11's own verification, done live via `psql`), not by an
automated test in this repo.

**Frontend: silent-failure fix.** `ConsoleRestaurantDetail.tsx`'s `deleteRestaurant` mutation had no
`onError` handler at all — any failure (this 500, or the pre-existing "must be SUSPENDED" 4xx guard)
produced zero visible feedback, indistinguishable from "did nothing." Added an `onError` reading
`error.response?.data?.detail` (the RFC 7807 `ProblemDetail` message `GlobalExceptionHandler`
already returns for both `IllegalStateException` and the generic 500 handler) with a toast, falling
back to a generic Spanish message, matching the existing `ConsoleRestaurantCreate.tsx` error-toast
pattern in this same console app. New test asserts the toast fires with the backend's exact message
on a mocked 500.

## 5. Why It Changed?
The migration is the actual fix — without it, delete will keep failing in prod even after
redeploying, since a CHECK constraint isn't a code path that gets "skipped," it's an unconditional
DB-level rule. The frontend fix is a genuine, independent defect (silent failures on any mutation
error) that made this specific bug look like nothing was happening at all rather than a diagnosable
error — it will also help surface any *future* delete failures immediately instead of requiring a
network-tab investigation.

## 6. LIVE-BUG-BATCH — Plan Result
All 7 live bug reports from this session are fixed (reports 495-501, commits on
`fix/live-bug-batch-2026-09-18`):
1. QR join skips the name form for authenticated accounts.
2. Activity log shows "X left the table".
3. Accurate closed-table banner ("no order" vs "paid") + leave-confirmation warns before freeing a table.
4. Settings tab cards use a two-column grid, matching `BrandingSettings`.
5. Cash-shift payments show the table, not the paying participant's name.
6. ADMIN can read cash-shift status in `/waiter/tables` again.
7. Restaurant deletion actually works (`V13` migration) and failures are no longer silent.

## Verification
- `cd frontend && pnpm vitest run src/pages/console/ConsoleRestaurantDetail.test.tsx` → 4/4 pass.
- `cd frontend && pnpm run build` → clean.
- `cd frontend && pnpm run lint` → 0 errors, 16 pre-existing warnings (unchanged).
- `cd frontend && pnpm run test:run` → **150/150**.
- `cd backend && ./mvnw test` → **1302/1302**, BUILD SUCCESS (unchanged — migration not exercised by
  the H2-based suite, by the codebase's own documented design; requires a real Postgres to verify).
- **Not yet verified live** — the migration needs a real deploy (or a manual `psql` run against a
  Postgres instance) to confirm the actual delete now succeeds. Recommend the user re-test on the
  same restaurant/environment where the original 500 was captured.
