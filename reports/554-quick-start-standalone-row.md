# Report 554 — QUICK-START-STANDALONE-ROW

## 1. Identification
- **Report number:** 554
- **Task ID:** QUICK-START-STANDALONE-ROW
- **Predecessor:** report 553 (QUICK-START-SQUARE-CARDS)

## 2. Objective
Take the quick-start profile tiles out of the login `Card`, make them larger, and lay them out horizontally.

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/Login.quickaccess.test.tsx`

## 4. What Changed?
- With stored profiles and the form not requested, the page renders a standalone block directly on the landing-style background: brand title, "Inicio rápido" + edit toggle, one horizontal row of 176px square tiles (80px colored initials circle, name + role below; `overflow-x-auto`, centered on md+), and "Usar otra cuenta".
- Otherwise the login `Card` renders with the form only (no profiles inside it any more). `QuickLoginModal` moved out of the card so it opens from either state. Language switcher is repositioned in the standalone block.
- Test: the login form is now unmounted (not just hidden) while tiles show, so the assertion is `not.toBeInTheDocument()`.

## 5. Why It Changed?
Requested: tiles outside the card, bigger, in a horizontal row.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser.
