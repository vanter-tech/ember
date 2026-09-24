# Report 561 — PASSWORD-REVEAL-EMBER-FLAME

## 1. Identification
- **Report number:** 561
- **Task ID:** PASSWORD-REVEAL-EMBER-FLAME
- **Predecessor:** report 560 (LOGIN-LANGUAGE-FAB)

## 2. Objective
Add a show/hide toggle to password fields, using the Ember flame (the landing navbar logo) as the icon: gray when hidden, `#8c1717` when visible.

## 3. Modified Files
- `frontend/src/components/PasswordInput.tsx` (new), `PasswordInput.test.tsx` (new)
- `frontend/public/ember_flame.svg` (new, copy of `landing/src/assets/Ember_logo.svg`)
- `frontend/src/pages/auth/{Login,Register,QuickLoginModal,ForcePasswordChangeModal}.tsx`
- `frontend/src/pages/admin/staff/components/CreateStaffModal.tsx`
- `frontend/src/locales/{es,en}/common.ts`

## 4. What Changed?
- `PasswordInput`: wraps `Input`, toggles `type` password/text via a right-aligned button (`cursor-pointer`, `aria-pressed`, translated `aria-label`). The flame is painted with a CSS `mask-image` so its color switches by class (gray `zinc-400` / red `#8c1717`) without duplicating the SVG.
- Used in Login, Register, QuickLoginModal (password mode only; PIN keeps the plain input), ForcePasswordChangeModal (3 fields) and CreateStaffModal. Operator-console password fields untouched.
- New i18n keys `showPasswordAria` / `hidePasswordAria`.

## 5. Why It Changed?
Requested reveal control with brand-consistent icon. My first attempt used the wrong asset (`ember_logo_info.svg`, a rounded-square mark); corrected to the navbar flame after the user's clarification.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 217/218 (+1 new); the 1 failure is the pre-existing `MenuJoin.test.tsx` authenticated QR join (see r550). Not checked visually in a browser.
