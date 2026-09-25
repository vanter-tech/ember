# Report 557 — AUTH-CARD-PADDING

## 1. Identification
- **Report number:** 557
- **Task ID:** AUTH-CARD-PADDING
- **Predecessor:** report 556 (AUTH-POWERED-BY-AND-REGISTER-BACKGROUND)

## 2. Objective
Fix the cramped login (and register) card: content touching the card edges.

## 3. Modified Files
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/Register.tsx`

## 4. What Changed?
- Card: `gap-6 py-10`; `CardHeader` and `CardContent`: `px-8`.
- `components/ui/card.tsx` left untouched.

## 5. Why It Changed?
The project's `Card` has no vertical padding (only `gap-4` and `px-4` on header/content), so title and button sat against the card border. Overriding per page avoids changing every other card in the app.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser.
