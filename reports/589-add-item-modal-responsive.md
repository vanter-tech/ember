# Report 589 — ADD-ITEM-MODAL-RESPONSIVE

## 1. Identification
- **Report number:** 589
- **Task ID:** ADD-ITEM-MODAL-RESPONSIVE
- **Predecessor:** report 588 (MENU-JOIN-REMOVE-HEADER-ICON)

## 2. Objective
Stop the per-client cart panel of the waiter add-dish modal from leaving the viewport on iPad / mid-width screens.

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx`

## 4. What Changed?
The cart panel was anchored outside the dialog (`right-[calc(100%+1rem)]`) and the dialog shifted 216px; after r585 enlarged both (dialog up to 88rem + 26rem panel) the pair no longer fit. Now the side-by-side layout and the dialog shift apply only from 1920px (`min-[1920px]:`); below that the panel overlays the right side of the dialog (`right-0 z-10 w-[min(26rem,100%)]`). List heights use `dvh` (`100dvh-16rem` / `100dvh-26rem`) so short landscape screens don't overflow vertically. Inline margin styles replaced by classes.

## 5. Why It Changed?
User reported the order panel spilling off-screen on iPad/wide devices. Build clean, lint 0 errors, waiter tests 39/39. Not verified at real device sizes.
