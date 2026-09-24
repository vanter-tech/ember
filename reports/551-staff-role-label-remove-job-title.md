# Report 551 — STAFF-ROLE-LABEL-REMOVE-JOB-TITLE

## 1. Identification
- **Report number:** 551
- **Task ID:** STAFF-ROLE-LABEL-REMOVE-JOB-TITLE
- **Predecessor:** report 550 (FIX-ONBOARDING-TABLES-STEP-SKIPPED)

## 2. Objective
In `admin/staff`, the WAITER role badge/filter must read "Mesero" (not "Comedor"), and the free-text "Puesto" (`jobTitle`) field, redundant with the role, is removed end to end.

## 3. Modified Files
- `frontend/src/pages/admin/staff/types.ts`
- `frontend/src/pages/admin/staff/components/{CreateStaffModal,EditStaffModal,StaffCard}.tsx`
- `frontend/src/locales/{es,en}/admin.ts`
- `frontend/src/lib/backend-types.ts`
- `backend/src/main/java/com/vanter/ember/identity/{model/User.java,dto/CreateStaffRequest.java,dto/StaffMemberResponse.java,dto/UpdateStaffProfileRequest.java,service/UserAdminService.java}`
- `backend/src/test/java/com/vanter/ember/identity/{controller/UserAdminControllerTest.java,service/UserAdminServiceTest.java,repository/UserRepositoryStaffQueryTest.java}`

## 4. What Changed?
- `ROLE_LABELS.WAITER` and the `STAFF_FILTERS` WAITER entry: "Comedor" → "Mesero".
- `jobTitle` removed from the create/edit forms, i18n keys, `StaffCard` (subtitle is now just the email), generated types, the `User` entity, the three staff DTOs and `UserAdminService`.
- Tests updated for the shorter record constructors; `createStaff_returns400ForBlankJobTitle` deleted (rule no longer exists).

## 5. Why It Changed?
"Comedor" was not intuitive for the waiter role, and "Puesto" duplicated what the role already conveys. The backend had `@NotBlank` on `jobTitle`, so dropping only the form field would have made creation fail with 400.

The `users.job_title` DB column is deliberately left in place (nullable, now unmapped): dropping it would make a rollback to the previous image break on Hibernate mapping, and prod migrations are never hand-run. Dropping it can be a later migration once the release is stable.

Verification: backend `./mvnw test` 1539/1539 (−1 deleted test); frontend `build` clean, `lint` 0 errors, staff tests pass. Full `test:run` not re-run (pre-existing `MenuJoin.test.tsx` failure noted in r550).
