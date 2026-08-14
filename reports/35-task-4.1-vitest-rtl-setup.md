# Report 35 — task-4.1

**Task ID:** task-4.1
**Predecessor Task:** task-3.8

## Objective
Set up Vitest and React Testing Library for frontend unit tests.

## Modified Files
- `frontend/package.json`
- `frontend/vite.config.ts`
- `frontend/tsconfig.app.json`
- `frontend/src/test/setup.ts` (new)
- `frontend/src/test/Button.smoke.test.tsx` (new)

## What Changed?
- Added devDependencies: `vitest`, `jsdom`, `@testing-library/react`, `@testing-library/jest-dom`, `@testing-library/user-event`.
- Added a `test` block to `vite.config.ts` (`environment: 'jsdom'`, `globals: true`, `setupFiles: ['./src/test/setup.ts']`), reusing the existing Vite config (plugins, `@` alias) instead of a separate `vitest.config.ts`.
- Added `src/test/setup.ts` importing `@testing-library/jest-dom` matchers.
- Added `vitest/globals` and `@testing-library/jest-dom` to `tsconfig.app.json`'s `types` array so `test`/`expect`/`toBeInTheDocument` type-check.
- Added `test` (watch) and `test:run` (CI) scripts to `package.json`.
- Added `src/test/Button.smoke.test.tsx`, a minimal render test against the existing `Button` component, to prove the harness renders and asserts correctly end-to-end.

## Why It Changed?
task-4.1 in the backlog calls for a frontend unit-test harness; none existed. Vitest was chosen over Jest because it shares Vite's config/transform pipeline (no separate babel/ts-jest setup, same `@` alias, same plugins), which keeps the harness minimal per the project's surgical-edit policy. The smoke test exists only to verify the wiring (jsdom environment, RTL queries, jest-dom matchers, path alias) works, not as feature coverage.

## Verification
- `pnpm run build` — PASS (0 TS errors, Vite build succeeded).
- `pnpm run test:run` — PASS (1 test file, 1 test).
