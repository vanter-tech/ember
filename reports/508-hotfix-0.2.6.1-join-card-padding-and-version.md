# Report 508

## 1. Identification
- **Report number:** 508
- **Task ID:** HOTFIX 0.2.6.1 — join-card padding + version bump
- **Predecessor task:** report 507 (Hub installer duplicate Flyway migration)

## 2. Objective
Prepare the `0.2.6.1` hotfix release that bundles the guest-join fix (r506) and the Hub installer
fix (r507), plus a small live UI bug: on the "join table" screens the buttons sat flush against the
card's bottom edge.

## 3. Modified Files
- `backend/pom.xml`
- `frontend/src/pages/customer/MenuJoin.tsx`
- `frontend/src/pages/customer/JoinByCode.tsx`

## 4. What Changed?
- `pom.xml` version `0.2.6` -> `0.2.6.1` (`build-installer.ps1` reads it, so the installer becomes
  `EmberHubSetup-0.2.6.1.exe`).
- Added `py-6` to the three join cards that have a `CardHeader` + `CardContent` (`MenuJoin`: guest/
  choice card and no-name fallback card; `JoinByCode`). The two `py-10` status cards were already fine.

## 5. Why It Changed?
This project's `Card` primitive (`components/ui/card.tsx`) has `gap-4` but no vertical padding, so
header/content touch the card's top and bottom edges. Padding was added at the three call sites
instead of on the shared `Card` to avoid shifting every other card in the app.

Verification: backend `./mvnw test` 1314/1314; frontend `pnpm run test:run` 164/164, `pnpm run
build` clean, `pnpm run lint` 0 errors (16 pre-existing warnings). Installer build/test and the
prod deploy are separate steps (see PROGRESS.md).
