# Report 463

## 1. Identification
- **Report Number:** 463
- **Task ID:** Fix `build-print-agent` CI failure; convert `ember-hub/ui` and `printing-agent/ui` from npm to pnpm (ad-hoc)
- **Predecessor Task:** report 462 (installer icon rebrand)

## 2. Objective
User noticed `build-print-agent` failing in CI (had already been red on both the PR #121 merge commit and the follow-up icon-rebrand push, unnoticed until now). While investigating, the user also reminded that pnpm is used exclusively in this repo — `ember-hub/ui` and `printing-agent/ui` had been using npm since they were first scaffolded, which is a real gap this task also closes.

## 3. Modified Files
- Modify: `.github/workflows/lint.yml` (`build-print-agent` job)
- Modify: `ember-hub/src-tauri/tauri.conf.json`, `printing-agent/src-tauri/tauri.conf.json` (`beforeBuildCommand`)
- Delete: `ember-hub/ui/package-lock.json`, `printing-agent/ui/package-lock.json`
- Create: `ember-hub/ui/pnpm-lock.yaml`, `printing-agent/ui/pnpm-lock.yaml`

## 4. What Changed?
**Root cause of the CI failure**, confirmed from the actual failed step's log (not guessed): `cargo tauri build`'s `beforeBuildCommand` — `npm --prefix ui install && npm --prefix ui run build` — failed with `ENOENT` looking for `printing-agent/package.json` (not `printing-agent/ui/package.json`). CI's `actions/setup-node@v4` pins Node 22, which bundles a different npm than this dev machine's (npm 11.6.2 locally) — the two versions resolve `npm --prefix <dir> install`'s package.json lookup differently: locally it correctly treats `--prefix ui` as "run from `ui/`," but CI's npm looks for `package.json` in the actual CWD (the Tauri app root, `printing-agent/`) and ignores `--prefix` for that purpose, so it never finds `ui/package.json`. This exact command was already a deliberate fix (report 441) for an *earlier* problem with a hardcoded `cd ../ui` — so this is the second time this one line's approach to "run this command inside `ui/`" has broken in a way that only shows up on CI, not locally.

Rather than patch `--prefix` again, switched to an unambiguous, shell-level `cd`: `cd ui && pnpm install && pnpm run build`. This also folds in the second half of the task — `ember-hub/ui` and `printing-agent/ui` were npm-managed (own `package-lock.json`, no tie to the root pnpm setup) since they were first scaffolded as standalone Astro packages; converted both to pnpm (`pnpm install` regenerating `pnpm-lock.yaml`, `package-lock.json` removed). `build-print-agent`'s own CI step that separately builds+tests `printing-agent/ui` (redundant with, but distinct from, the `beforeBuildCommand` invocation) was also converted from raw `npm install`/`npm run test`/`npm run build` to `pnpm install --frozen-lockfile`/`pnpm run test`/`pnpm run build`, with a `pnpm/action-setup` step added (that job never had one — `lint-frontend`/`build-hub` already did, for the main `frontend/` package).

## 5. Why It Changed?
The CI failure needed a fix regardless; using `cd` sidesteps the npm-version-dependent `--prefix` ambiguity entirely rather than trading one edge case for another. The npm→pnpm conversion is a direct, explicit user instruction ("recuerda que yo uso únicamente y exclusivamente PNPM") applying repo-wide, not just to the packages already documented as pnpm-only in CLAUDE.md — saved to memory (`feedback_pnpm_only.md`) so it isn't missed again for these or any other standalone sub-package.

## 6. Verification
- `pnpm install` + `pnpm run test` + `pnpm run build` ran clean for both `ember-hub/ui` (13/13 tests) and `printing-agent/ui` (5/5 tests) after the lockfile conversion.
- Full local pipeline re-verified for both apps: `build-installer.ps1 -Stage installer` (which invokes `cargo tauri build`, exercising the new `beforeBuildCommand` exactly as CI will) succeeded end-to-end for both, producing fresh `EmberHubSetup-0.2.4.exe` and `EmberAgentSetup-0.1.1.exe`.
- Not yet verified on the actual CI runner (Windows, Node 22) — that's the next step after pushing this commit; will monitor the `build-print-agent` run.
