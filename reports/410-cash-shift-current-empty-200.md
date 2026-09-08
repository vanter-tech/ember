# Report 410 — `/cash-shifts/current` returns an empty 200, not a 404, when no shift is open

## 1. Identification
- **Report number:** 410
- **Current Task ID:** ad-hoc backend/frontend fix (no milestone ID)
- **Predecessor Task:** report 409 — frontend admin landing / tours / caja UX

## 2. Objective
On `/waiter/cash-register` with no cash shift open, the browser console logged
`GET https://api.ember.vanter.net/cash-shifts/current 404 (Not Found)`. The frontend already
handled it (returns `null`), but a browser logs every HTTP ≥ 400 response and JS cannot
suppress that — it just looked like a broken system. Make "no open shift" a normal `200`.

## 3. Modified Files
- `backend/src/main/java/com/vanter/ember/cashregister/service/CashShiftService.java`
- `backend/src/main/java/com/vanter/ember/cashregister/controller/CashShiftController.java`
- `backend/src/test/java/com/vanter/ember/cashregister/service/CashShiftServiceTest.java`
- `backend/src/test/java/com/vanter/ember/cashregister/controller/CashShiftControllerTest.java`
- `frontend/src/lib/api.ts`

## 4. What Changed?
- **`CashShiftService`:** added `findCurrentOpenShift(UUID): Optional<CashShift>` — a plain repo
  passthrough. `getCurrentOpenShift` (still throws `ResourceNotFoundException`) is untouched; it
  had no remaining callers in `main` but its unit test stays valid.
- **`CashShiftController.current()`:** now
  `findCurrentOpenShift(tenantId).map(cashShiftService::toResponse).orElse(null)`. A `null`
  return from a `@RestController` handler serialises as `200 OK` with an empty body — no 404.
- **`frontend/src/lib/api.ts` `cashShiftService.current`:** happy path is now
  `return data || null` (axios yields `''` for an empty 200 body → normalised to `null`). The
  `catch` that maps a `404` to `null` is kept, commented, for the rolling-deploy window when the
  old backend is still live behind a newer frontend.
- **Tests:** `CashShiftControllerTest` gains `current_returnsEmpty200WhenNoShiftOpen` (200 +
  empty body) and `current_returnsTheOpenShiftWhenOneExists` (200 + JSON). `CashShiftServiceTest`
  gains `findCurrentOpenShift_returnsEmptyWhenNoneOpen`.

## 5. Why It Changed?
"Nobody has opened the register yet" is an expected state on every screen the cash-shift
sentinel polls from, not a client error. A 404 on a normal poll every 60 s is console noise
that reads as a defect. Returning `200` with no body is the correct REST semantics for
"the resource is a singleton that currently has no value" and keeps the console clean.

## 6. Verification
- `cd backend && ./mvnw test` — **1199/1199** passing (+3: 2 controller, 1 service).
- `cd frontend && pnpm run build` — clean; `pnpm run lint` — 0 errors, 16 pre-existing warnings.
- `cd frontend && pnpm run test:run` — 118/118.
