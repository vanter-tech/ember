# Report 659 — PLAN-EXPIRY-ALERT-10-DAYS

## 1. Identification
- Report: 659
- Task ID: PLAN-EXPIRY-ALERT-10-DAYS
- Predecessor: 658 (PLAN-TAB-STATUS-UNDER-PLAN); builds on uncommitted r647

## 2. Objective
Show the "plan expires soon" alert only when 10 days or fewer remain.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`
- `frontend/src/pages/admin/components/settings/PlanSettings.test.tsx`

## 4. What Changed?
`RENEW_SOON_DAYS` 30 -> 10 (single threshold for every billing period). Tests: the warning test now reads "within 10 days"; a new test asserts no alert with 11 days left.

## 5. Why It Changed?
With the monthly cycle the next payment is always <= 30 days away, so the alert never went away (a just-renewed month showed exactly 30 days left). Ten days leaves the operator margin to collect and renew manually while keeping the alert quiet for most of the month.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 6/6. NOT committed (depends on uncommitted r647).
