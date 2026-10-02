# Report 683 — RELEASE-V0.3.6

## 1. Identification
- Report: 683
- Task ID: RELEASE-V0.3.6
- Predecessor: 682 (PRINTING-SKELETONS)

## 2. Objective
Ship the skeleton-loading rework: bump the version, rebuild the Ember Hub installer (it bundles the frontend), tag `v0.3.6` and hand over the deploy commands.

## 3. Modified Files
- `backend/pom.xml` (0.3.5 -> 0.3.6), commit `92299f72`.
- Already on `main` before this release: `f9336f7c` (animated Settings tab switch) and `c2b6ea2f` (squash of reports 661-682, 86 files, frontend only).
- Built (git-ignored): `ember-hub/dist/EmberHubSetup-0.3.6.exe`.

## 4. What Changed?
Frontend-only release. Nothing changed in `backend/`, `deploy/`, `ember-hub/` or `.github/` since `v0.3.5` apart from the pom version, and `printing-agent/` is untouched, so the agent installer is not republished and there are no new Flyway migrations (last is `V24`). `ember-hub/build-installer.ps1` (all stages) produced `EmberHubSetup-0.3.6.exe` — 181.2 MB (189,982,068 bytes), SHA-256 `1d1bb765198f1264bf16b67ff9db505c944fb0617541ef3fbff1f459d3119c1d`. Tag `v0.3.6` triggers `backend-image.yml` (`ember-backend:0.3.6`, same code as 0.3.5).

## 5. Why It Changed?
Ship the skeleton rework. The pom version names the Hub installer and must differ per release. The production SPA is served by the Cloudflare Worker `ember-app` (built from `frontend/`, per `deploy/RUNBOOK.md`), so the push to `main` is what ships the frontend; the backend image only needs a redeploy for version parity.

### Verification
- Frontend on `main`: `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings); `pnpm run test:run` 377/378 — the one failure is the known `MenuJoin.test.tsx` "authenticated: submitting the name joins via the QR token", failing before this work.
- Backend: no code change since `v0.3.5` (1756/1756 there); `./mvnw test` was not re-run.
- Hub installer build exit 0.

### NOT verified
- The installer was built but not installed or run. The Cloudflare build of `ember-app` after the push was not checked from here. The skeletons were never measured in the running app (only in tests), and reduced-motion, the two-browser link race and the kitchen/ticket/analytics flow remain unexercised on a live system.
