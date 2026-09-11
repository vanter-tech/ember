# Report 437

**Task ID:** Ember Hub installer rebuild (ad-hoc, to carry the EMB-EXPORT `.xlsx` feature into the on-premise build)
**Predecessor Task:** report 436 — EMB-EXPORT `.xlsx` structure (borders, title bar, banding)

## Objective
Ember Hub bundles `backend/` + `frontend/` as-is (no code of its own), so the export feature and the styling fix from reports 435/436 need a new `EmberHubSetup-<version>.exe` for on-premise customers to get them. Running `ember-hub/build-installer.ps1` failed before producing anything.

## Modified Files
- `ember-hub/build-installer.ps1`

## What Changed?
`Build-AppImage`'s frontend step (`& powershell -ExecutionPolicy Bypass -File $frontendPs`, which runs `pnpm run build:hub`) aborted with:
```
<script src="env-config.js"> in "/index.html" can't be bundled without type="module" attribute
NativeCommandError
```
`pnpm run build:hub` (`vite build --base=/app/ --mode hub`) actually **succeeds** (exit 0) — this is a non-fatal Vite diagnostic about `frontend/index.html`'s intentionally-plain (non-`type="module"`) `env-config.js` script tag, written to the process's stderr stream. `build-installer.ps1` sets `$ErrorActionPreference = "Stop"` at the top; in Windows PowerShell 5.1, any native command writing to stderr is wrapped into a terminating `NativeCommandError` under that setting, regardless of the process's real exit code. So a harmless build-time warning aborted the whole installer build before the script's own `$LASTEXITCODE` check (which was already correct) ever ran.

Fix: scoped the frontend call so it's not evaluated under `Stop`, and left the existing `$LASTEXITCODE` check as the actual pass/fail signal (which is what it already was — the bug was purely `Stop` pre-empting it):
```powershell
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
& powershell -ExecutionPolicy Bypass -File $frontendPs
$ErrorActionPreference = $prevEap
if ($LASTEXITCODE -ne 0) { throw "build-frontend.ps1 failed" }
```
Scoped to just this one call site (the only one observed to emit stderr today) rather than flipping the script's global `$ErrorActionPreference`, which would have silently weakened failure detection for the script's `Copy-Item`/`Remove-Item` cmdlet calls.

## Why It Changed?
Without this, `ember-hub/build-installer.ps1` cannot produce an installer at all on this machine, blocking the whole point of the rebuild — getting the `.xlsx` export feature (and its styling fix) into the Hub distributable. Not caused by the EMB-EXPORT changes themselves; it's a latent PowerShell 5.1 quirk in the Hub build tooling, surfaced now because this is the first Hub rebuild since the frontend started emitting this particular Vite diagnostic.

## Verification
`powershell -File ember-hub\build-installer.ps1` (all stages: runtime → app-image → installer) completed clean end-to-end:
`ember-hub\dist\EmberHubSetup-0.2.4.exe` produced (~181 MB). Version `0.2.4` comes from `backend/pom.xml` (already bumped off the old placeholder before this session, in commit `d81db55a`/#116) — distinct from the previously-published `EmberHubSetup-0.0.1.exe`, so publishing it will not collide with or silently overwrite that stale object in `gs://ember-downloads-prod`.
Not yet published — the user runs `deploy/publish-installer.sh hub 0.2.4 EmberHubSetup-0.2.4.exe` from Cloud Shell after transferring the `.exe` there.
