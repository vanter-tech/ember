# Report 592 — WAITER-TABLE-AVATAR-BADGE

## 1. Identification
- Report: 592
- Task ID: WAITER-TABLE-AVATAR-BADGE
- Predecessor: ACCOUNTANT-REMOVE-DIGITAL-SALES (report 591)

## 2. Objective
Show the assigned waiter's avatar on the bottom-left corner of each occupied table card in `waiter/Tables`.

## 3. Modified Files
- `frontend/src/pages/waiter/Tables.tsx`

## 4. What Changed?
- Occupied table cards with `currentSession.waiterName` render a circular badge (`absolute bottom-4 left-4`, `size-7`) with the waiter's initials (`AvatarInitials`) (per-waiter color via `getAvatarColor`), no background ring, no animation; `title` holds the full name.
- Free tables and sessions without a waiter render nothing.

## 5. Why It Changed?
Lets the floor see at a glance which waiter owns each table. The badge sits inside the bottom-left corner with the same 1rem inset as the `M#` label (a negative offset got clipped by the card). Data already came from `ActiveSessionSummary.waiterName`; no backend change.

Verification: `pnpm run build` clean, `pnpm run lint` 0 errors, `Tables.test.tsx` 2/2. Not visually verified.
