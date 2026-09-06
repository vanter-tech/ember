# Report 386 — fix(hub): EmberHub.iss compile error (Inno Setup has no Randomize)

## Identification
- **Report:** 386
- **Task:** hotfix — the installer script committed in report 381 / PR #78 does not compile.
- **Predecessor:** report 385 (platform console redesign).
- **Branch:** `fix/hub-iss-random-seed` off `main`.

## Objective
Report 381 added a `[Code]` block to `ember-hub/installer/EmberHub.iss` that generates a
per-install `JWT_SECRET` / `PLATFORM_JWT_SECRET`. It calls `Randomize` before `Random(16)`.
**Inno Setup 6's Pascal Script exposes neither `Randomize` nor `GetTickCount`**, so
`iscc EmberHub.iss` aborts with `Unknown identifier 'Randomize'`. Since report 381 no installer
can be built from `main` at all — the installer stage of `build-installer.ps1` fails.

## Modified Files
- `ember-hub/installer/EmberHub.iss`

## What Changed?
Replaced `Randomize` + `Random(16)` with a tiny self-seeded LCG using only functions Inno
Setup 6 actually provides:

- `SeedRng` seeds a module-level `GSeed` from `GetComputerNameString` + a
  `GetDateTimeString('yyyymmddhhnnss', …)` install timestamp, folded in with a `*131 + Ord(c)`
  rolling hash (masked to 31 bits), plus one `Random(65536)` call (harmless whether or not
  Inno's `Random` is seeded).
- `NextNibble` advances `GSeed` with the glibc constants (`*1103515245 + 12345`, mask 31 bits)
  and returns `(GSeed shr 16) and 15`.
- `RandomHex(ByteCount)` builds the hex string from `NextNibble`. `CurStepChanged` now calls
  `SeedRng` (once) instead of `Randomize`; the two `RandomHex(32)` calls that follow continue
  the same sequence, so `JWT_SECRET` and `PLATFORM_JWT_SECRET` differ even within one install
  second.

Not cryptographic — the design goal (report 381 / spec) was only that each install gets a
*distinct, non-empty* 256-bit token, never one hardcoded value shared across customers. The
HMAC strength is in the length.

## Why It Changed?
`iscc` is invoked by `build-installer.ps1`'s `installer` stage; a compile error there means the
`.exe` cannot be produced. This blocks every Hub install build.

## Verification
- `iscc /DAppVersion=0.0.1 /DServerPort=8080 /DEmberHubActivationUrl=… /DEmberHubHeartbeatUrl=…
  EmberHub.iss` → exit 0 (was: aborted on line 129).
- Full `build-installer.ps1` (all stages) → `dist/EmberHubSetup-0.0.1.exe`, ~172 MB, with the
  prod `hub-public-key.der` (SHA256 `6ce631e5…`) bundled.
- No backend/frontend code touched; test suites unaffected.
