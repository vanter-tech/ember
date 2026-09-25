# Report 588 — MENU-JOIN-REMOVE-HEADER-ICON

## 1. Identification
- **Report number:** 588
- **Task ID:** MENU-JOIN-REMOVE-HEADER-ICON
- **Predecessor:** report 587 (MENU-JOIN-CHOICE-REDESIGN)

## 2. Objective
Remove the haloed header icon added to the `/menu/join` choice card.

## 3. Modified Files
- `frontend/src/pages/customer/MenuJoin.tsx`

## 4. What Changed?
Deleted the `UtensilsCrossed` icon block and its import; the header keeps the centered title and subtitle. Option-card icons (`LogIn`, `User`) stay.

## 5. Why It Changed?
User feedback: the card looks better but the header icon was unwanted. Build clean, lint 0 errors, customer tests 16/17 (pre-existing `MenuJoin` failure).
