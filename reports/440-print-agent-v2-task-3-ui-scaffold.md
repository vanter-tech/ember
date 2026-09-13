# Report 440 — PRINT-AGENT-V2 Task 3: `printing-agent/ui/` Astro+React+Tailwind window UI

## 1. Identification
- **Report:** 440
- **Task ID:** PRINT-AGENT-V2 Task 3
- **Predecessor Task:** report 439 (PRINT-AGENT-V2 Task 2 — collapse `Main` to sidecar mode, remove Swing UI)

## 2. Objective
Build the static Astro+React+Tailwind UI that will become the Tauri window's content — 4 sections (status, pairing, printers, jobs) plus footer actions, polling `LocalControlServer`'s JSON contract from Task 1, independently buildable/testable ahead of the Task 4 Tauri shell.

## 3. Modified Files
- Create: `printing-agent/ui/package.json`, `astro.config.mjs`, `tsconfig.json`, `vitest.config.ts`, `.gitignore`
- Create: `printing-agent/ui/src/styles/global.css`
- Create: `printing-agent/ui/src/layouts/Layout.astro`
- Create: `printing-agent/ui/src/pages/index.astro`
- Create: `printing-agent/ui/src/lib/{types.ts, api.ts}`
- Create: `printing-agent/ui/src/components/{Dashboard,StatusSection,PairingSection,PrintersSection,JobsTable,FooterActions}.tsx`
- Create: `printing-agent/ui/src/components/{PairingSection,JobsTable}.test.tsx`
- (generated, not committed) `printing-agent/ui/package-lock.json` is committed; `node_modules/` and `dist/` are gitignored

## 4. What Changed?
New standalone package `printing-agent/ui/` (Astro 7 + `@astrojs/react` + React 19 + Tailwind 4 via `@tailwindcss/vite`), not shared with `frontend/`/`landing/` per the plan's constraint — only the Tailwind design tokens were copied from `frontend/src/index.css` (`--primary: oklch(0.395 0.175 28.5)`, `--radius: 0.625rem`, Inter font) into `src/styles/global.css`.

`src/lib/types.ts` mirrors `LocalControlServer`'s DTOs (`Phase`, `JobRecord`, `Status`, `DiscoveredPrinter`). `src/lib/api.ts` wraps `fetch` calls to `/api/status`, `/api/printers`, `/api/pair`, `/api/test-print`, `/api/diagnostics`, `/api/paths`, resolving the port via a `@tauri-apps/api/core` `invoke('get_port')` call (stubbed/unimplemented until Task 4 supplies the real Tauri runtime — this task only needs the import to resolve for the build, not to run in a browser).

Five components: `StatusSection` (connection dot + phase/last-seen/printer-count), `PairingSection` (code vs. API-key toggle, posts to `/api/pair`, surfaces server error text), `PrintersSection` (queue `<select>` refreshed every 5s + "Imprimir página de prueba"), `JobsTable` (recent-jobs table with an empty-state row and em-dash fallbacks), `FooterActions` ("Abrir carpeta de logs" / "Copiar diagnóstico", the latter via `navigator.clipboard`). `Dashboard` composes all five, polling `/api/status` every 1.5s and swallowing transient poll failures (retried next tick).

Added `printing-agent/ui/.gitignore` (not explicitly listed in the plan's file list, but needed to keep `node_modules/`, `dist/`, `.astro/` out of git) — copied from `landing/.gitignore`'s shape, the closest sibling Astro project.

## 5. Why It Changed?
Implements Task 3 of `docs/superpowers/plans/2026-09-12-printer-agent-v2-tauri-shell.md`. This is the window content Task 4's Tauri shell will load as `frontendDist`; building it now, independent of the not-yet-existing Rust shell, lets the component logic and its tests be verified before the cross-process wiring is added.

**Verification:**
- `npm install` — 363 packages, no errors (5 pre-existing moderate/high/critical `npm audit` advisories in transitive deps, not new to this task, not investigated — out of scope for a UI-scaffold task).
- `npm run test` (vitest) → **4/4 pass** (`JobsTable.test.tsx` 2/2, `PairingSection.test.tsx` 2/2).
- `npm run build` (astro build) → clean, `dist/index.html` + `_astro/` assets produced, no errors.
