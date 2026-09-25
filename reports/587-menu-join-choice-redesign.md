# Report 587 — MENU-JOIN-CHOICE-REDESIGN

## 1. Identification
- **Report number:** 587
- **Task ID:** MENU-JOIN-CHOICE-REDESIGN
- **Predecessor:** report 586 (CUSTOMER-JOIN-LOGIN-STYLE)

## 2. Objective
Make the `/menu/join` sign-in / guest card less plain.

## 3. Modified Files
- `frontend/src/pages/customer/MenuJoin.tsx`
- `frontend/src/locales/es/customer.ts`, `frontend/src/locales/en/customer.ts`

## 4. What Changed?
Centered header with a haloed `UtensilsCrossed` icon. The two flat buttons became option cards with icon and one-line hint (sign in: filled brand red; guest: outlined), separated by an "o"/"or" divider; `aria-label` keeps the original accessible names. New keys `qrJoinSignInHint`, `qrJoinGuestHint`, `qrJoinOr`. The restaurant-name chip from the plan was skipped: no restaurant name is available on this screen (the QR token carries only ids).

## 5. Why It Changed?
User found the modal too simple. Build clean, lint 0 errors, customer tests 16/17 (pre-existing `MenuJoin` failure). Not visually verified.
