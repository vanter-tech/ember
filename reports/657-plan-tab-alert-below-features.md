# Report 657 — PLAN-TAB-ALERT-BELOW-FEATURES

## 1. Identification
- Report: 657
- Task ID: PLAN-TAB-ALERT-BELOW-FEATURES
- Predecessor: 656 (PLAN-TAB-VISUAL-HIERARCHY); builds on uncommitted r647

## 2. Objective
Move the "plan expires soon" alert below the "what's included" list, right above the card footer.

## 3. Modified Files
- `frontend/src/pages/admin/components/settings/PlanSettings.tsx`

## 4. What Changed?
The amber/red expiry alert block moved from under the header to the end of `CardContent` (after the feature list, before `CardFooter`). Logic and texts unchanged.

## 5. Why It Changed?
Requested layout: the notice sits next to the contact/renewal action in the footer.

Verification: `pnpm run build` clean, lint 0 errors, `PlanSettings.test.tsx` 5/5. NOT committed (depends on uncommitted r647).
