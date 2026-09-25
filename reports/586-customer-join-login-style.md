# Report 586 — CUSTOMER-JOIN-LOGIN-STYLE

## 1. Identification
- **Report number:** 586
- **Task ID:** CUSTOMER-JOIN-LOGIN-STYLE
- **Predecessor:** report 585 (ADD-ITEM-MODALS-POLISH)

## 2. Objective
Give the customer QR-join screen (`/menu/join`) and the code-join screen (`/join`) the same look as the login: landing background, brand footer, language picker, login-style cards.

## 3. Modified Files
- `frontend/src/pages/customer/components/JoinShell.tsx` (new)
- `frontend/src/pages/customer/MenuJoin.tsx`
- `frontend/src/pages/customer/JoinByCode.tsx`

## 4. What Changed?
New `JoinShell` wraps `AuthBackground`, `PoweredByVanter` and `LanguageFab` in the login's `relative min-h-screen overflow-hidden bg-white p-4 pb-12` container. All 4 `MenuJoin` screens (invalid link, auto-join, sign-in/guest choice, name entry) and `JoinByCode` use it; cards are `relative z-10 gap-6 py-10 shadow-lg` with `px-8` header/content and the title in `#920703`.

## 5. Why It Changed?
Visual consistency with the login flow the customer comes from. Build clean, lint 0 errors, customer tests 16/17 (pre-existing `MenuJoin` authenticated-join failure). Not visually verified.
