# Report 451

## 1. Identification
- **Report:** 451
- **Task ID:** EMBER-HUB-V2 Task 3
- **Predecessor Task:** report 450 (EMBER-HUB-V2 Task 2 — headless sidecar collapse)

## 2. Objective
Build `ember-hub/ui/`, the Astro+React+Tailwind window content for the Hub's Tauri shell: `ServiceCard` (auto-expanding simulated per-service boot/shutdown log, honest about real errors) for PostgreSQL/MinIO/Servidor, `LicenseCard` (3-state badge + last-heartbeat text), and a `Dashboard` composing them with the header controls (Iniciar servicios/Detener/Abrir en navegador/Salir).

## 3. Modified Files
- Create: `ember-hub/ui/package.json`, `astro.config.mjs`, `tsconfig.json`, `vitest.config.ts`, `.gitignore`
- Create: `ember-hub/ui/src/styles/global.css`, `src/layouts/Layout.astro`, `src/pages/index.astro`
- Create: `ember-hub/ui/src/lib/{types,api,logScripts}.ts`
- Create: `ember-hub/ui/src/components/{Button,Card,Badge,ServiceCard,LicenseCard,Dashboard}.tsx`
- Test: `ember-hub/ui/src/components/{ServiceCard,LicenseCard}.test.tsx`

## 4. What Changed?
- Standalone Astro 7 + `@astrojs/react` + React 19 + Tailwind 4 package, design tokens copied verbatim from `printing-agent/ui` (brand red `oklch(0.395 0.175 28.5)`, `--radius: 0.625rem`).
- `lib/types.ts`/`api.ts` mirror `HubControlServer`'s JSON contract from Task 1 (`getStatus`/`startServices`/`stopServices`/`installLicense`, all resolving the port via `invoke('get_port')`).
- `lib/logScripts.ts`: hardcoded, per-service-flavored canned log lines for the start and stop direction (Postgres/MinIO/Spring-Boot-flavored text).
- `Button`/`Card`/`Badge`: local copies of printer-agent's already-shipped components (not imported cross-package, per spec §2.4).
- `ServiceCard`: compact `icon+title+Badge` row; auto-expands into a monospace terminal panel on `STARTING`/`STOPPING` (staggered reveal via a `useTypedLog` hook, one line every 350ms) or on `ERROR` (shows the **real** error message, stays expanded); collapses back to compact on `RUNNING`/`STOPPED`.
- `LicenseCard`: `Badge` (OK=green/Suspendida=red/Sin licencia=neutral) + last-contact text + "Seleccionar license.key…" button.
- `Dashboard`: polls `/api/status` every 1.5s; header has Iniciar servicios (only shown when fully stopped) / Detener / Abrir en navegador (gated on server RUNNING) / Salir; dynamically imports `@tauri-apps/plugin-{dialog,shell,process}` inside their respective handlers.
- Tests: `ServiceCard` (4) + `LicenseCard` (4), 8/8 passing. `npm run build` produces `ember-hub/ui/dist/`.

## 5. Why It Changed?
Implements Task 3 of `docs/superpowers/plans/2026-09-13-ember-hub-v2-tauri-shell.md` — the window content Task 4's Tauri shell will load, following the exact same pattern as printer-agent's already-shipped `printing-agent/ui`.

**3 real deviations from the plan's literal text, found by actually running the steps rather than assuming they'd work:**

1. **`lucide-react` version**: the plan pinned `^0.469.0`; the actual, currently-installed `printing-agent/ui/package.json` uses `^1.16.0`. Used the real version for consistency between the two sibling UIs.
2. **`global.css` missing a rule**: the plan's Step 5 text omitted `html, body { height: 100%; }`, present in the real `printing-agent/ui/src/styles/global.css` it claims to copy verbatim. Without it, `Dashboard.tsx`'s `h-full` on `<main>` has nothing to resolve its percentage height against. Added the rule to match the actual reference file.
3. **`ServiceCard.test.tsx`'s STARTING test was written against `useTypedLog`'s real `setInterval(…, 350)` timing but asserted synchronously right after render** — confirmed failing exactly as predicted before writing `ServiceCard.tsx` (Step 15), and still failing after (1 of 4 tests) once `ServiceCard.tsx` existed, because no lines had been revealed yet at assertion time. Fixed by wrapping that one test in `vi.useFakeTimers()`/`act(() => vi.advanceTimersByTime(3 * 350))` — the production component's staggered-reveal behavior is unchanged, only the test now controls time deterministically instead of hoping the assertion runs after 1050ms of real wall-clock time.
4. **`Dashboard.tsx`'s dynamic `import('@tauri-apps/plugin-dialog'|'-shell'|'-process')` calls broke `npm run build`** (`astro build`), not just Vitest: the plan's own text says these are "Task 4 installs them as real dependencies," but Vite/Rolldown's bundler tries to resolve every literal-string dynamic import at build time regardless of whether it's ever executed — `/* @vite-ignore */` did not suppress this in this Vite version (confirmed by reproducing the exact `Rolldown failed to resolve import` build failure first). Fixed by adding `@tauri-apps/plugin-dialog@^2.7.3`, `@tauri-apps/plugin-process@^2.3.1`, `@tauri-apps/plugin-shell@^2.3.6` as real npm dependencies of **this** task's `package.json` (mirroring how `@tauri-apps/api` was already listed here, not deferred) — Task 4 still owns the *Rust-side* Cargo/`tauri.conf.json` capability wiring for these plugins, only the JS binding packages needed to exist now for the build to resolve them.

## 6. Verification
- `cd ember-hub/ui && npm install && npm run test` — **8/8** pass (`ServiceCard` 4/4, `LicenseCard` 4/4).
- `cd ember-hub/ui && npm run build` — clean, `dist/index.html` + `_astro/*` produced (confirmed on disk after fixing deviation #4 above; failed with a real `Rolldown failed to resolve import` error before the fix, not hypothetically).
