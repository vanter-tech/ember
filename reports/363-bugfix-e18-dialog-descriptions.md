# Report 363 — bugfix: E-18 accessibility warning on 3 remaining dialogs

## 1. Identification
- **Report:** 363
- **Task:** bugfix-e18-dialog-descriptions (user-requested, closes the gap
  `QA_SIMULATION_REPORT_v2.md` §4.1 found: FIX-QA only fixed `CloseShiftDialog`)
- **Predecessor:** report 362 (bugfix-qr-token-bearer-500)

## 2. Objective
`QA_SIMULATION_REPORT.md` E-18 ("Missing `Description` or `aria-describedby={undefined}` for
`{DialogContent}`") was only fixed on `CloseShiftDialog` by `FIX-QA`. A live browser pass this
session confirmed the warning still fires on 3 other dialogs: `QuickLoginModal` (PIN entry),
`EditStaffModal` ("Edit employee"), and `GlobalDeleteModal`'s "Are you sure?" deactivate/delete
confirm. Fix all 3.

## 3. Modified Files
- `frontend/src/pages/auth/QuickLoginModal.tsx`
- `frontend/src/pages/admin/staff/components/EditStaffModal.tsx`
- `frontend/src/components/GlobalDeleteModal.tsx`
- `frontend/src/locales/{en,es}/auth.ts` (+`quickLoginDialogDescription`)
- `frontend/src/locales/{en,es}/admin.ts` (+`editEmployeeDescription`, +`confirmDeleteWarning`)

## 4. What Changed?
Each dialog now renders a real `<DialogDescription>` (from `@/components/ui/dialog`, the shared
Radix wrapper) instead of either nothing or a plain unlinked `<p>`:
- `QuickLoginModal.tsx` — added `<DialogDescription>{tAuth('quickLoginDialogDescription')}</DialogDescription>`
  inside `DialogHeader`, after the title.
- `EditStaffModal.tsx` — same pattern, `t('editEmployeeDescription')`.
- `GlobalDeleteModal.tsx` — the existing plain `<p>{t('deactivateStaffWarning')}</p>` (rendered
  only for the `DELETE_STAFF` case) was replaced with a `<DialogDescription>` that always renders:
  `deactivateStaffWarning` for `DELETE_STAFF`, the new generic `confirmDeleteWarning` ("This action
  cannot be undone.") for the other two variants this same component handles
  (`DELETE_CATEGORY`/`DELETE_ITEMS`), which previously had no description text at all and would
  still have triggered the warning even after a staff-only fix.

`DialogDescription` auto-wires Radix's `aria-describedby` to the dialog root, which is what
silences the warning (and gives screen readers real content, not just a fix for a console log).

## 5. Why It Changed?
Radix logs this as a real accessibility defect, not a cosmetic one — a screen reader user gets no
description of what a destructive confirmation dialog ("Are you sure?") is actually about, or what
they're being asked to do in the PIN/edit-employee forms. `FIX-QA` closed the one dialog the
original finding happened to name a repro for; the other two named dialogs (and the deactivate
confirm, found via this session's live click-through) were never touched.

## 6. Verification
- Frontend: `pnpm run build` — PASS, 0 TS errors.
- `pnpm run lint` — 0 errors, 16 pre-existing warnings (none in touched files).
- `pnpm run test:run` — **73/73** (23 files), unchanged count — no test asserted on the missing
  description, so nothing to update.
- **Live browser re-check** (`claude-in-chrome`, backend+frontend booted for this session only):
  opened `QuickLoginModal` (login page, "admin" chip), the staff "Deactivate" confirm dialog, and
  `EditStaffModal` ("Edit employee") — all 3 now show the description text on screen and produce
  **zero** `Missing Description` console warnings, versus 2 warnings each before the fix.
