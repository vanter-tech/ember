# Report 562 — PASSWORD-REVEAL-EYE-ICON

## 1. Identification
- **Report number:** 562
- **Task ID:** PASSWORD-REVEAL-EYE-ICON
- **Predecessor:** report 561 (PASSWORD-REVEAL-EMBER-FLAME)

## 2. Objective
Replace the Ember-flame reveal icon with an eye icon like the one Edge shows on password fields.

## 3. Modified Files
- `frontend/src/components/PasswordInput.tsx`
- `frontend/public/ember_flame.svg` (deleted)

## 4. What Changed?
- The toggle now renders lucide `Eye` (hidden) / `EyeOff` (visible), gray by default and `#8c1717` while the password is shown. The CSS mask and the flame asset were removed; labels, `aria-pressed`, `cursor-pointer` and usages are unchanged.

## 5. Why It Changed?
The flame wasn't what the user wanted; the eye is the familiar affordance.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 217/218 (the failure is the pre-existing `MenuJoin.test.tsx`, see r550). Not checked visually in a browser.
