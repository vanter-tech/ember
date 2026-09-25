# Report 560 — LOGIN-LANGUAGE-FAB

## 1. Identification
- **Report number:** 560
- **Task ID:** LOGIN-LANGUAGE-FAB
- **Predecessor:** report 559 (QUICK-LOGIN-OPTION-ICONS-CURSOR)

## 2. Objective
Replace the login's language dropdown with a circular `#8c1717` button in the bottom-right corner.

## 3. Modified Files
- `frontend/src/pages/auth/LanguageFab.tsx` (new)
- `frontend/src/pages/auth/Login.tsx`

## 4. What Changed?
- New `LanguageFab`: `fixed bottom-6 right-6` 48px circle, `#8c1717` background, white current-locale code (ES/EN), `cursor-pointer`; opens a Popover with Español / English (selected one highlighted).
- `Login.tsx`: removed both `LanguageSwitcher` usages (quick-start view and form card) and mounts `LanguageFab` once. The original `LanguageSwitcher` is unchanged and still used by TopNav, customer menu and kitchen.

## 5. Why It Changed?
Requested layout: language control out of the card/header area into a discreet corner button.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` + `src/test` 19/19. Not checked visually in a browser. Register has no language control (unchanged).
