# Report 196 — Surface open-tables detail in the close-shift error toast

## 1. Identification
- **Report number:** 196
- **Task ID:** task — close-shift-toast-open-tables-detail
- **Predecessor Task:** bugfix — cash-shift-close-validation (report 195)

## 2. Objective
Show the specific reason a cash-shift close was rejected (N tables still have an open session) in the waiter's error toast, instead of the generic "Could not close the shift." message, without leaking the raw English backend string into the Spanish UI.

## 3. Modified Files
- Modify: `frontend/src/pages/waiter/cashRegister/components/CloseShiftDialog.tsx`
- Modify: `frontend/src/locales/es/waiter.ts`
- Modify: `frontend/src/locales/en/waiter.ts`

## 4. What Changed?
Added `shiftCloseTablesOpenToast` to both locale dictionaries (`{{count}}` interpolation, same convention as `billPointsEarned`/`tableHeading`/etc.). `CloseShiftDialog.tsx`'s mutation `onError` now checks (via `axios.isAxiosError`) whether the 409 response's `ProblemDetail.detail` matches the backend's `"Cannot close cash shift: N table(s) still have an open session"` string (report 195); a small `extractOpenTablesCount` regex pulls the count out. When it matches, the toast shows the localized, count-aware message; any other error (shift already closed, network failure, etc.) still falls back to the existing generic `shiftCloseErrorToast`.

## 5. Why It Changed?
Follow-up to report 195: the backend now blocks the close with a specific reason, but the dialog was swallowing it behind a generic toast, leaving the waiter without an actionable explanation. Matching on the `detail` string (rather than displaying it directly, and rather than only checking the 409 status code) mirrors the existing `isTenantSuspendedDetail` pattern in `lib/api.ts` — it distinguishes this specific failure from any other `IllegalStateException`-driven 409 the close endpoint could throw (e.g. "shift is not open"), and keeps the surfaced text fully localized instead of exposing backend English to Spanish-locale users.
