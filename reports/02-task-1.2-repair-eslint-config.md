# Report 02 — task-1.2

## Identification
- **Report:** 02
- **Task ID:** task-1.2
- **Predecessor Task:** task-1.1

## Objective
Repair `frontend/eslint.config.js` so `pnpm run lint` can execute instead of crashing on module resolution.

## Modified Files
- `frontend/eslint.config.js`

## What Changed?
Removed the import of `eslint-plugin-prettier/recommended` (line 6) and its usage in the `tseslint.config(...)` array (former line 29). The rest of the flat config (JS/TS recommended rules, react-hooks, react-refresh) is unchanged.

## Why It Changed?
`eslint-plugin-prettier` was never added to `package.json` or `pnpm-lock.yaml`, so `eslint.config.js` failed to resolve the module and `pnpm run lint` crashed before linting anything. Installing the plugin was rejected as the fix: `eslint-plugin-prettier/recommended` also needs `eslint-config-prettier` and `prettier` as peers to behave correctly, which would introduce three unrequested dependencies for a config-repair task. Removing the broken import is the surgical fix; `.prettierrc` is left in place for standalone/editor-driven formatting.

## Verification
- `pnpm run lint`: now runs to completion (previously crashed on unresolved import). It reports 19 pre-existing errors / 6 warnings across unrelated files (`FloatingNav.tsx`, `ComandaView.tsx`, `Menu.tsx`, `websocket.ts`, etc.) — these are pre-existing code-quality issues out of scope for this task and tracked separately in the backlog (e.g. task-1.4, task-1.5, task-1.7).
- `pnpm run build` (`tsc -b && vite build`): **PASSING**, 0 TS errors.
