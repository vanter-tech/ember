# Report 460

## 1. Identification
- **Report Number:** 460
- **Task ID:** EMBER-HUB-V2 UX change (ad-hoc, post-plan) — move the "no license.key found" message out of the PostgreSQL card
- **Predecessor Task:** report 459 (log sanitization + logo fix)

## 2. Objective
User request: the "license not found" notice was showing up inside the PostgreSQL card (as its `ERROR` message) — move it below the License card instead, styled like the app's destructive/red buttons, and stop showing it on the Postgres card.

## 3. Modified Files
- Modify: `ember-hub/ui/src/components/Dashboard.tsx`
- Modify: `ember-hub/ui/src/components/ServiceCard.tsx`
- Modify: `ember-hub/ui/src/components/ServiceCard.test.tsx`

## 4. What Changed?
The backend reports a missing/invalid license as `postgres: ERROR` (per `DefaultHubOrchestrator.runStart`'s own comment: `InvalidLicenseException` blocks everything before Postgres itself ever attempts to start, but gets attributed to Postgres's phase because that's the first real step in the boot sequence — not because it's conceptually a Postgres problem). `Dashboard.tsx` now derives `licenseBlockedStartup = status.postgres === 'ERROR' && status.license.status === 'NONE'` and, when true: passes `error={null}` to the PostgreSQL `ServiceCard` instead of the real message, and renders that message itself as a new banner directly below `LicenseCard` — solid `bg-primary`/`text-primary-foreground` (the same red used by the header's buttons, which is this app's closest thing to a "destructive" look; there is no separate `destructive` variant in `ember-hub/ui`'s `Button`/`Badge`).

`ServiceCard.tsx`'s `hasContent` used to be `isError || lines.length > 0` — with `error` now intentionally `null` while `isError` is still `true`, that would have rendered an empty red chip. Changed to `(isError && !!error) || lines.length > 0` so passing a null error on `ERROR` renders nothing instead of a blank box — this is a general-purpose fix in the shared component, not license-specific.

The `license.status === 'NONE'` check (rather than string-matching the error text) also naturally covers the *other* license-blocking case from earlier testing (a license file that exists but was already activated elsewhere) — any license failure leaves `license.status` at `NONE` (nothing was ever successfully persisted to the state file), so it moves to the same banner rather than needing separate handling.

## 5. Why It Changed?
Direct user request, and a real UX improvement: a Postgres-specific card is the wrong place to explain a licensing problem, and conflating them (as flagged in the code's own pre-existing comment) makes the dashboard read as if Postgres itself is broken when the real, single blocker is upstream. Keying off `license.status` rather than the error string keeps the logic robust to the exact wording of whatever license exception fired, rather than brittle string matching.

## 6. Verification
- `cd ember-hub/ui && npm run test` → 13/13 (12 previous + 1 new: `ERROR` phase with a null `error` renders no `.mt-3` log container at all).
- `npm run build` → clean.
- `cargo build --release` (`ember-hub/src-tauri`, UI-only change but still needs the resource copy) → clean; `build-installer.ps1 -Stage installer` (no backend change this time, so `-Stage installer` alone was correct — reuses the already-current app-image from report 458/459's `-Stage all`).
- **Real end-to-end**: fresh install, deleted `license.key` + `hub-state.json`, relaunched. Screenshotted: PostgreSQL card shows only the "Error" badge (collapsed, no message); the red banner with the real "No se encontró license.key…" text renders directly below the Licencia card, styled to match the red header buttons.
