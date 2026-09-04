# Report 340 — EMB-FEAT-05: Frontend `authService` PIN methods

## 1. Identification
- **Report number:** 340
- **Current Task ID:** EMB-FEAT-05 (Task 5 of `docs/superpowers/plans/2026-09-03-waiter-quick-login.md`)
- **Predecessor Task:** EMB-FEAT-04 (report 339 — `POST`/`DELETE /account/pin` backend endpoints)

## 2. Objective
Expose the three PIN-related backend endpoints to the React app as thin `authService`
wrappers with inline request body types, so later quick-login UI tasks (chip list,
`QuickLoginModal`, `SetPinPrompt`) can call them.

## 3. Modified Files
- `frontend/src/lib/api.ts`

## 4. What Changed?
Extended the exported `authService` object (immediately after `register`) with three
methods:

- `loginPin(body: { email: string; pin: string }): Promise<LoginResponse>` — `POST /auth/login/pin`,
  returns the same `AuthResponse` shape as `login`/`register` (reuses the existing
  `LoginResponse` type alias).
- `setPin(body: { currentPassword: string; pin: string }): Promise<void>` — `POST /account/pin`,
  no response body (backend returns `204`).
- `clearPin(): Promise<void>` — `DELETE /account/pin`, no body, no response body (`204`).

Request bodies are typed inline per the plan (no shared DTO type added). No other
part of `api.ts` touched. No tests added — these are thin passthroughs exercised later
by the `QuickLoginModal` test with a mocked `api`.

## 5. Why It Changed?
The quick-login feature needs a client entry point for PIN authentication and
self-service PIN management. Placing the wrappers alongside `login`/`register` keeps
all identity calls in one object and matches the codebase convention (`await api.post`
/ `api.delete` with a destructured `{ data }` only when a body is returned).

## Verification
- `cd frontend && pnpm run build` → PASS, 0 TypeScript errors (2847 modules, `tsc -b` clean).
- `pnpm run lint` → 0 errors (17 pre-existing warnings, none in `api.ts`).
