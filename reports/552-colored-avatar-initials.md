# Report 552 — COLORED-AVATAR-INITIALS

## 1. Identification
- **Report number:** 552
- **Task ID:** COLORED-AVATAR-INITIALS
- **Predecessor:** report 551 (STAFF-ROLE-LABEL-REMOVE-JOB-TITLE)

## 2. Objective
Give each person's initials circle its own color instead of a single/gray one: `admin/staff` cards and the customer participant avatars.

## 3. Modified Files
- `frontend/src/components/AvatarInitials.ts`
- `frontend/src/components/AvatarInitials.test.ts` (new)
- `frontend/src/pages/admin/staff/components/StaffCard.tsx`
- `frontend/src/pages/customer/ComandaView.tsx`
- `frontend/src/pages/customer/components/{ParticipantsList,ParticipantsPopUp}.tsx`

## 4. What Changed?
- New `getAvatarColor(seed)`: deterministic hash → one of 10 pastel palette entries (`bg-*-200 text-*-800 border-white`). Empty seed falls back to the first entry.
- `StaffCard` colors the `AvatarFallback` with `member.id ?? name`.
- The three customer avatar views use `getAvatarColor(name)` instead of the index-based gray `AvatarColors`, which is removed (no remaining users). Name is the seed everywhere on the customer side because `ComandaView`'s person objects have no `userId`, and one shared seed keeps a person's color identical across the three views.

## 5. Why It Changed?
Grays made every person look the same. A hash of a stable seed (not the list index) keeps a person's color constant when the list reorders or someone joins/leaves.

Verification: `pnpm run build` clean, `lint` 0 errors, `test:run` 216/217 (+3 new); the 1 failure is the pre-existing `MenuJoin.test.tsx` authenticated QR join (see r550).
