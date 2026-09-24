# Report 558 — AUTH-BRAND-TO-FOOTER

## 1. Identification
- **Report number:** 558
- **Task ID:** AUTH-BRAND-TO-FOOTER
- **Predecessor:** report 557 (AUTH-CARD-PADDING; the r558 admin-border commit was reverted)

## 2. Objective
Move the "Ember" wordmark from the login title to the footer, just above "Desarrollado por Vanter".

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/PoweredByVanter.tsx`

## 4. What Changed?
- Login title (quick-start view and form card) no longer renders `brandFallback` + `<br />`; only the tagline remains. Unused `tCommon` removed.
- `PoweredByVanter` now stacks a bold `#920703` "Ember" above the credit line. Since the component is shared, the register page footer shows it too.

## 5. Why It Changed?
Requested layout change.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser.
