# Report 486 — Frontend: wire up apple-touch-icon.png

**Predecessor:** report 485 (PILOT-READINESS Task 1, hourly Postgres backups)

## Objective
User added `apple-touch-icon.png` to `frontend/public/` but nothing referenced it — wire it up so
iOS "Add to Home Screen" actually uses it, mirroring the pattern `landing` already has.

## Modified Files
- `frontend/index.html`

## What Changed?
Added `<link rel="apple-touch-icon" href="/apple-touch-icon.png" />` next to the existing favicon
`<link>`, matching `landing/src/layouts/Layout.astro:52`'s exact pattern (no `<link>` existed in
`frontend/index.html` for it before this — the file being present in `public/` alone wasn't
sufficient).

## Why It Changed?
`public/apple-touch-icon.png` was already being copied into `dist/` by Vite, but with no `<link>`
tag referencing it, iOS Safari falls back to guessing a root-relative `/apple-touch-icon.png` by
convention — explicit is more reliable and consistent with how `landing` already declares it.

## Verification
`pnpm run build` clean; confirmed `dist/apple-touch-icon.png` present in the build output.
