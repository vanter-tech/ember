# Report 346 — EMB-FEAT-11: Plan B wrap-up (report + PROGRESS + full verification)

## 1. Identification
- **Report number:** 346
- **Task ID:** EMB-FEAT-11 (Plan B — `docs/superpowers/plans/2026-09-03-waiter-quick-login.md`, final task)
- **Predecessor:** report 345 (EMB-FEAT-10 — `SetPinPrompt` post-login nudge + layout header entry)

## 2. Objective
Close out Plan B (waiter quick-login: device-cached chips + PIN). No source changes — run the
full backend and frontend verification suites for the finished feature, document the end-to-end
result, and update `PROGRESS.md`.

## 3. Modified Files
- `reports/346-emb-feat-11-plan-b-verification.md` (new — this file)
- `PROGRESS.md`

No application code was touched by this task. EMB-FEAT-01..10 shipped in commits
`58d28c7`, `7f0b968`, `66b66a6`, `b880364`, `c9bfeb8`, `0b39c09`, `3f58fcf`, `d53d21b`,
`aa57918`, `15aea1f` (EMB-FEAT-00 console-logout fix in `6af6e4b`).

### Plan B code footprint (cumulative, `main..HEAD`, application files only)
**Backend (create)**
- `db/migration/V6__user_pin.sql`
- `identity/model/dto/PinLoginRequest.java`, `identity/model/dto/SetPinRequest.java`
- `identity/exception/PinNotSetException.java`, `identity/exception/PinLockedException.java`
- `identity/service/PinAttemptGuard.java`
- `identity/config/IdentityClockConfig.java`
- `identity/controller/AccountController.java`

**Backend (modify)**
- `identity/model/User.java` — `pinHash` (`@JsonIgnore`), `pinUpdatedAt`
- `identity/service/AuthService.java` — `loginWithPin`, `setPin`, `clearPin`
- `identity/controller/AuthController.java` — `POST /auth/login/pin`
- `config/GlobalExceptionHandler.java` — `PIN_NOT_SET` (409) / `PIN_LOCKED` (423) handlers
- `config/RateLimitProperties.java` — `/auth/login/pin` added to guarded `paths`

**Backend (tests)**
- `identity/model/UserPinColumnsTest.java`, `identity/service/PinAttemptGuardTest.java`,
  `identity/controller/AuthControllerTest.java`, `identity/controller/AccountControllerTest.java`,
  `identity/service/AuthServiceTest.java`

**Frontend (create)**
- `src/store/quickAccessStore.ts` (+ `.test.ts`)
- `src/pages/auth/navigateForRole.ts` (+ `.test.tsx`)
- `src/pages/auth/QuickLoginModal.tsx` (+ `.test.tsx`)
- `src/pages/auth/SetPinPrompt.tsx` (+ `.test.tsx`)
- `src/pages/auth/Login.quickaccess.test.tsx`

**Frontend (modify)**
- `src/lib/api.ts` — `authService.loginPin` / `setPin` / `clearPin` + inline request types
- `src/pages/auth/Login.tsx` — quick-access chip grid + `remember()` after password login
- `src/layouts/WaiterLayout.tsx`, `src/layouts/AdminLayout.tsx` — header "set PIN" entry
- `src/locales/es/auth.ts`, `src/locales/en/auth.ts` — 21 new keys each (parity)

## 4. What Changed?
Only `PROGRESS.md` and this report.

- `PROGRESS.md` — EMB-FEAT-11 checkbox flipped to `[x] (report 346)`; a new
  "Last Completed Task (report 346, EMB-FEAT-11 ...)" bullet added at the top of
  Current Execution State summarizing the three PIN endpoints, the lockout guard, the
  device-local `quickAccessStore`, and the `QuickLoginModal` / `SetPinPrompt` UI; an
  obsolete older Plan-B task bullet was condensed to stay within the 180-line budget.

## 5. Why It Changed?
Plan B's Task 11 is a pure wrap-up gate: prove the whole feature (DB migration → PIN login
endpoint → self-service PIN management → device chip store → quick-login modal → set-PIN
nudge) builds and passes green as an integrated whole after ten incremental commits, and
leave the execution state accurate for whoever picks up Plan C (EMB-FEAT-12..22, the
table-detail action buttons).

## 6. Verification
- **Backend** — `cd backend && ./mvnw test` → **BUILD SUCCESS**, `Tests run: 962, Failures: 0, Errors: 0, Skipped: 0`.
- **Frontend build** — `cd frontend && pnpm run build` (`tsc -b && vite build`) → **PASS**, 0 TypeScript errors, built in ~2s.
- **Frontend lint** — `pnpm run lint` → **0 errors**, 17 pre-existing warnings (`AddPrinterModal.tsx`, `Menu.tsx`, `SelectModifiersModal.tsx`, `TableInformation.tsx` — none in Plan B files).
- **Frontend tests** — `pnpm run test:run` → **17 files, 57/57 passed**.
