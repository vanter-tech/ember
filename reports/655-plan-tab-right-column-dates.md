# Report 655 — PLAN-TAB-RIGHT-COLUMN-DATES

## 1. Identification
- Report: 655
- Task ID: PLAN-TAB-RIGHT-COLUMN-DATES
- Predecessor: 654 (SUBSCRIPTION-MONTHLY-RENEWAL-CONSOLE); builds on uncommitted r647

## 2. Objective
Show the plan start and next payment on the right side of the Plan card.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`

## 4. What Changed?
The summary grid is now two fixed columns: left (current plan, status badge) and right (plan start, period, next payment with days left), right-aligned from `sm` up; stacked on mobile. The yellow "plan expires soon" alert already existed (amber at <= 30 days left, red once overdue) and is unchanged.

## 5. Why It Changed?
The previous grid interleaved the fields; the dates now read as their own block. Note: with the monthly cycle (default end = start + 30d) the amber alert is shown for the whole month.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 5/5. Not checked in a browser. NOT committed (depends on uncommitted r647).
