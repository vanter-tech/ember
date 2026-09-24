# Report 564 — HIDE-EDGE-NATIVE-REVEAL

## 1. Identification
- **Report number:** 564
- **Task ID:** HIDE-EDGE-NATIVE-REVEAL
- **Predecessor:** report 563 (QUICK-LOGIN-PIN-MASKED)

## 2. Objective
Stop two reveal icons appearing on password fields once the first character is typed (Edge's native one plus ours).

## 3. Modified Files
- `frontend/src/components/PasswordInput.tsx`

## 4. What Changed?
- The inner `Input` gets `[&::-ms-clear]:hidden [&::-ms-reveal]:hidden`, hiding Edge's built-in reveal/clear buttons. Verified the built CSS contains the `::-ms-reveal{display:none}` rule.

## 5. Why It Changed?
Edge renders its own reveal button on any `type="password"` input that has text, duplicating our eye toggle in every `PasswordInput`.

Verification: `pnpm run build` clean, `lint` 0 errors, `src/pages/auth` + `src/components` 51/51. The pseudo-element can't be exercised in jsdom, so this is not covered by a test and not yet confirmed in Edge itself.
