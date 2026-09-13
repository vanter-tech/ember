# Report 459

## 1. Identification
- **Report Number:** 459
- **Task ID:** EMBER-HUB-V2 polish (ad-hoc, post-plan) — remove internal implementation details from simulated logs; fix missing Vanter/Ember logos under the Hub build's `/app/` base path
- **Predecessor Task:** report 458 (restart + license lifecycle)

## 2. Objective
Two unrelated but small fixes requested/found in the same testing round: (1) the Servidor card's simulated boot-log line literally read `"Starting EmberApplication using Java 17 (hub profile)"` — naming the internal Java class and JVM version, which the user flagged as inappropriate to expose in a polished product UI; (2) after successfully logging into the web app through "Abrir en navegador," the Settings → Información page was missing both the Vanter and Ember logos.

## 3. Modified Files
- Modify: `ember-hub/ui/src/lib/logScripts.ts`
- Modify: `frontend/src/pages/admin/components/settings/InfoSettings.tsx`

## 4. What Changed?
`logScripts.ts`'s `server` entries in both `START_SCRIPTS` and `STOP_SCRIPTS` (these are 100% cosmetic, canned strings — never real backend output, per the plan's original design) were rewritten to drop any internal naming: `"Starting EmberApplication using Java 17 (hub profile)"` → `"Iniciando el servidor…"`, and `"Cerrando el contexto de Spring…"` → `"Cerrando el servidor…"` (also dropped "Spring" for the same reason, even though only the first line was explicitly flagged, for consistency).

`InfoSettings.tsx` hardcoded `src="/vanter-tech_logo.svg"` and `src="/ember_logo_info.svg"` — absolute, domain-root paths. The Hub build serves the SPA under `/app/` (`vite build --base=/app/`, per `ember-hub/build-frontend.ps1`), so those two `<img>` tags were the *only* two image references anywhere in `frontend/src` using an absolute root path instead of the codebase's own established `import.meta.env.BASE_URL` pattern (already used in `App.tsx` and `isHubBuild.ts` for exactly this Hub-vs-cloud path difference). Fixed to `` src={`${import.meta.env.BASE_URL}vanter-tech_logo.svg`} `` (and the Ember logo the same way) — a no-op change for the normal cloud build, where `BASE_URL` is `"/"`.

## 5. Why It Changed?
The log-content fix is a direct, explicit user request (a real product-polish concern: revealing "this runs on Java 17" to restaurant staff is an unnecessary and unprofessional implementation leak). The logo fix root cause was confirmed, not guessed: `curl`ing `http://localhost:8080/vanter-tech_logo.svg` returned **401 Unauthorized** (Spring Security's allowlist doesn't cover bare root-level static paths — only `/app/**`), while the same file at `http://localhost:8080/app/vanter-tech_logo.svg` returned 200 `image/svg+xml`. This bug could only ever manifest under the Hub build specifically (the cloud frontend's `BASE_URL` is already `/`, so the old absolute path happened to already be correct there) — which is why it went unnoticed until this session's live Hub testing.

## 6. Verification
- `cd ember-hub/ui && npm run test` → 12/12 unchanged (no test asserts on the old canned log strings; confirmed via `grep` before editing).
- `cd frontend && pnpm run build:hub && pnpm run build` → both clean (hub build, and the normal cloud build to confirm the `BASE_URL`-prefixed path is backward compatible there).
- `cd frontend && pnpm run test:run` → 124/124 pass, no regressions (no test file exists for `InfoSettings.tsx` specifically, confirmed via search).
- Full pipeline rebuild + real install: log lines confirmed live via screenshot ("Iniciando el servidor…" / "Servicio web escuchando en el puerto 8080" / "Servidor listo.", no class/version names). Logo fix confirmed directly over HTTP against the real running instance: `/app/vanter-tech_logo.svg` → 200 `image/svg+xml`; bare `/vanter-tech_logo.svg` → 401 (proving both the original bug's exact mechanism and the fix).
