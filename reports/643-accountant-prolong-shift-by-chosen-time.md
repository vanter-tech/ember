# Report 643

## 1. Identification
- **Report Number:** 643
- **Task ID:** ACCOUNTANT-PROLONG-SHIFT — button in the accountant view to extend the cash shift by a chosen time
- **Predecessor Task:** report 642 (ADMIN-FORCE-CLOSE-TABLE + FIX-STAFF-ROLE-SAVE). Everything since report 634 is still uncommitted at the time of writing.

## 2. Objective
Until now the only way to extend an open cash shift was the reminder modal (`CashShiftSentinel`) that appears every so often and offers a fixed hour. The accountant asked for a permanent control in their view, and to **pick how long** to extend instead of always adding one hour.

## 3. Decisions (agreed with the user)
- Durations selectable from a fixed list: **30 min, 1 h, 2 h, 3 h, 4 h**; the server rejects anything else (so a manual call cannot push the deadline out by days).
- The periodic reminder keeps extending by one hour (backend default), so nothing changes for it or for older clients.
- A shift from a previous business day cannot be extended, only closed (same rule the reminder already applies); the control is disabled with an explanation. The user did not object to this when it was proposed.

## 4. Modified Files
- Backend: `cashregister/service/CashShiftDeadlineService.java` (`ALLOWED_PROLONG_MINUTES`, `prolong(shift, now, step)`), `cashregister/service/CashShiftService.java` (`prolongShift(id, user, minutes)`, old 2-arg overload = 60), `cashregister/controller/CashShiftController.java` (optional body), `cashregister/dto/ProlongShiftRequest.java` (new)
- Backend tests: `CashShiftDeadlineServiceTest` (+3), `CashShiftServiceTest` (+2, 1 adapted), `CashShiftControllerProlongTest` (+2, 1 adapted)
- Frontend: `pages/accountant/cashRegister/components/ProlongShiftControl.tsx` (new), `pages/accountant/cashRegister/CashRegister.tsx` (+`.test.tsx`, 4 new tests), `lib/api.ts` (`prolong(id, minutes?)`), `locales/es|en/waiter.ts` (4 keys)
- `PROGRESS.md`

## 5. What Changed?
- `POST /cash-shifts/{id}/prolong` accepts an optional `{ "minutes": n }`. Bean validation (`@AssertTrue`) rejects values outside the list with 400; `CashShiftService.prolongShift` re-checks (`IllegalArgumentException`) as defence in depth. No body, or `minutes` null, means 60. The deadline still extends from the current deadline, or from now when already overdue. Still `ACCOUNTANT` only.
- New `ProlongShiftControl` in the shift card's action row: a native `<select>` (label "Tiempo a prolongar", options 30 min / 1 h / 2 h / 3 h / 4 h, default 1 h) and a "Prolongar" button. It calls `cashShiftService.prolong(shiftId, minutes)`, refreshes `cashShiftCurrent` (so the deadline shown updates) and toasts "Caja prolongada {tiempo} más."; errors reuse the existing error toast. Disabled for a previous-day shift using the existing `deriveCashShiftAlert(...) === 'STALE'`, with the hint "Este turno es de un día anterior: debe cerrarse, no prolongarse."
- A native select was chosen over the shadcn `Select` because it is accessible out of the box and testable in jsdom.

## 6. Verification
- Backend `./mvnw test` excluding `PortableMinioBootstrapCredentialsIntegrationTest` (the known load-dependent flake): **1663/1663**. RED first (compile errors for the missing members).
- Frontend: `tsc -b` clean, `build` OK, `lint` 0 errors (15 warnings, the baseline; one new react-refresh warning I introduced was fixed), tests **266/266** excluding `MenuJoin.test.tsx` (load-dependent flake, see report 640). Four new tests: default hour, chosen duration, exact list of options, previous-day shift disabled.
- **Not verified:** no live run against a real backend or a browser; the `@AssertTrue` 400 is covered by a controller test with the service mocked, not through a full HTTP stack. The developer's running backend must be restarted to pick up the new body handling.
- Known limits: nothing caps the total number of extensions (each one is now up to 4 h), same as before with one hour at a time; the reminder modal still only offers one hour.
