# Report 658 — PLAN-TAB-STATUS-UNDER-PLAN

## 1. Identification
- Report: 658
- Task ID: PLAN-TAB-STATUS-UNDER-PLAN
- Predecessor: 657 (PLAN-TAB-ALERT-BELOW-FEATURES); builds on uncommitted r647

## 2. Objective
Stack the plan name, the "Estado" label and the status badge vertically, with more breathing room around the badge.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`

## 4. What Changed?
Left header block is now a column: plan label + name, then the "Estado" text, then the badge below it. Badge padding `px-4 py-1.5` -> `px-6 py-2`; `gap-4` between the blocks and `gap-2` between "Estado" and the badge.

## 5. Why It Changed?
Requested layout; the badge was too tight to the surrounding text.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 5/5. NOT committed (depends on uncommitted r647).
