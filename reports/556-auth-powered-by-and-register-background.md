# Report 556 — AUTH-POWERED-BY-AND-REGISTER-BACKGROUND

## 1. Identification
- **Report number:** 556
- **Task ID:** AUTH-POWERED-BY-AND-REGISTER-BACKGROUND
- **Predecessor:** report 555 (QUICK-START-CAROUSEL)

## 2. Objective
Add a "Desarrollado por Vanter" credit at the bottom edge of the auth screens, and give the register page the same landing-style background as login.

## 3. Modified Files
- `frontend/src/pages/auth/AuthBackground.tsx` (new)
- `frontend/src/pages/auth/PoweredByVanter.tsx` (new)
- `frontend/src/pages/auth/Login.tsx`
- `frontend/src/pages/auth/Register.tsx`
- `frontend/src/locales/{es,en}/auth.ts`

## 4. What Changed?
- The inline backdrop (dot grid + two blurred red glows) from `Login.tsx` was extracted to `AuthBackground` and reused by `Register` (white base, card raised with `z-10`).
- `PoweredByVanter`: absolutely positioned, centered footer text with "Vanter" linking to `https://vanter.net` (new i18n key `poweredBy`). Used on Login (quick-start and form views) and Register; containers got `pb-12` so it doesn't overlap content on short screens.

## 5. Why It Changed?
Requested credit line and visual consistency across the auth flow. Extracting the background avoids duplicating the gradient markup in two pages.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` 16/16. Not checked visually in a browser (register page has no tests).
