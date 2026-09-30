# Report 656 — PLAN-TAB-VISUAL-HIERARCHY

## 1. Identification
- Report: 656
- Task ID: PLAN-TAB-VISUAL-HIERARCHY
- Predecessor: 655 (PLAN-TAB-RIGHT-COLUMN-DATES); builds on uncommitted r647

## 2. Objective
Fix the disordered Plan card: give it a clear hierarchy with two focal points (plan and next payment).

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`

## 4. What Changed?
Card content reordered: (1) header row with the plan name (`text-4xl`, #8c1717) plus the larger Active badge on the left, and a highlighted "Próximo pago" block (date `text-2xl font-bold` + days left) on the right; (2) amber/red expiry alert right below; (3) one compact row with plan start and period under a divider; (4) "Qué incluye" under another divider as a 2-column checklist; footer unchanged (note + contact button right). Stacks in the same order on mobile. The separate "Estado" label was dropped: the badge now sits next to the plan name.

## 5. Why It Changed?
All fields had equal weight in an interleaved grid, so nothing stood out. The plan and the next payment date are what the admin looks for, so they lead; secondary data is compact and the feature list is scannable.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 5/5. Not checked visually in a browser. NOT committed (depends on uncommitted r647).
