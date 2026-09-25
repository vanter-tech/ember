# Report 559 — QUICK-LOGIN-OPTION-ICONS-CURSOR

## 1. Identification
- **Report number:** 559
- **Task ID:** QUICK-LOGIN-OPTION-ICONS-CURSOR
- **Predecessor:** report 558 (AUTH-BRAND-TO-FOOTER; an earlier enlarged-modal attempt for r559 was removed at the user's request)

## 2. Objective
Add icons to the quick-login modal's Password/PIN options and pointer cursors to the clickable elements of the quick-start flow.

## 3. Modified Files
- `frontend/src/pages/auth/QuickLoginModal.tsx`
- `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?
- Modal options keep their size but now show a lucide icon (`Lock` for password, `Hash` for PIN) beside the label, with `cursor-pointer`; submit button gets `disabled:cursor-not-allowed`.
- `Login.tsx`: `cursor-pointer` on profile tiles, the edit toggle, "Usar otra cuenta" and the tile remove "×".

## 5. Why It Changed?
Requested: icons on the options and pointer cursors. Tailwind 4's preflight no longer sets `cursor: pointer` on buttons, so it must be explicit.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser.
