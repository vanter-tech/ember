# Report 644

## 1. Identification
- **Report Number:** 644
- **Task ID:** FIX-ADD-ITEM-CONFIRM-BUTTON — the "Confirmar pedido" button of the add-dish modal looked squashed
- **Predecessor Task:** report 643 (ACCOUNTANT-PROLONG-SHIFT). Everything since report 634 is still uncommitted at the time of writing.

## 2. Objective
The user, reviewing the waiter's "Agregar platillo" modal, reported that the confirm button was "too thin, something is suppressing it".

## 3. Modified Files
- `frontend/src/pages/waiter/components/AddItemModal.tsx` (one class)
- `frontend/src/pages/waiter/components/AddItemModal.test.tsx` (+1 regression test)
- `PROGRESS.md`

## 4. What Changed?
- **Root cause:** the shared `Button` (`components/ui/button.tsx`, size `default`) has a fixed height (`h-10`) and **only horizontal padding** (`px-2.5`). The modal's confirm button was `className="h-11 w-full sm:h-auto"`: at phone widths the `h-11` kept it fine, but from 640 px up `sm:h-auto` removed the height, so nothing but the text line gave it size. It is the only `h-auto` responsive override left in the frontend (`grep`), so no other button has this problem. It came in with the "waiter mobile polish" commit (#142).
- **Fix:** `sm:h-auto` -> `sm:h-12`, so the button keeps an explicit height at every width (44 px on phones, 48 px from `sm`).

## 5. Why It Changed?
Direct user feedback: the primary action of the modal looked collapsed on desktop.

## 6. Verification
- A regression test asserts the button has no `h-auto` class and does have an explicit `h-11`..`h-14`; it failed (RED) with the old class and passes now. jsdom does not compute Tailwind, so this guards the cause, not the rendered pixels.
- Frontend `tsc -b` clean, `build` OK, `lint` 0 errors (15 baseline warnings), tests **267/267** excluding `MenuJoin.test.tsx` (load-dependent flake, report 640).
- **Not verified visually:** I did not open the modal in a browser (the dev server was stopped), so the final look at each width is inferred from the class change, not seen. The user should confirm.
