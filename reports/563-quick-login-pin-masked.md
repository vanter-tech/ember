# Report 563 — QUICK-LOGIN-PIN-MASKED

## 1. Identification
- **Report number:** 563
- **Task ID:** QUICK-LOGIN-PIN-MASKED
- **Predecessor:** report 562 (PASSWORD-REVEAL-EYE-ICON)

## 2. Objective
The quick-login PIN must not be shown in clear while typing, and should have the same eye reveal toggle as the password.

## 3. Modified Files
- `frontend/src/pages/auth/QuickLoginModal.tsx`

## 4. What Changed?
- The PIN and password modes now both render `PasswordInput` (`key={mode}` so the visibility state resets when switching). `inputMode="numeric"`, `maxLength={6}` and the digits-only filter are kept. The plain `Input` branch and its import were removed.

## 5. Why It Changed?
The PIN was a `type="text"` field, so it was visible over the shoulder on a shared device; masking by default with an opt-in reveal matches the password field.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` + `src/components` 51/51. Not checked visually in a browser.
