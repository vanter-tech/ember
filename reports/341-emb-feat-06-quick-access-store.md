# Report 341 — EMB-FEAT-06: device-local quick-access profile store

## Identification
- **Report number:** 341
- **Task ID:** EMB-FEAT-06
- **Predecessor Task:** EMB-FEAT-05 (report 340 — frontend `authService.loginPin`/`setPin`/`clearPin`)

## Objective
Add a device-local (localStorage) store of recent login profiles ("chips") that the
quick-login UI (EMB-FEAT-08/09) will render, plus a per-email `pinDismissed` list for the
post-login PIN nudge (EMB-FEAT-10). No UI wiring in this task.

## Modified Files
- `frontend/src/store/quickAccessStore.ts` (new)
- `frontend/src/store/quickAccessStore.test.ts` (new)

## What Changed?
- **`quickAccessStore.ts`** — a `zustand` store wrapped in `persist` (localStorage key
  `ember-quick-access`).
  - `QuickAccessProfile` type: `{ email, name, role, initials, colorSeed, lastUsedAt }`.
  - State: `profiles: QuickAccessProfile[]`, `pinDismissed: string[]`.
  - `remember({ email, name, role })` — derives `initials` (first letter of up to the first
    two whitespace-separated name words, uppercased; `'?'` fallback) and `colorSeed`
    (deterministic 0–359 hash of the lowercased email), stamps `lastUsedAt = Date.now()`,
    upserts by lowercased/trimmed email (removing any prior entry for that key), then sorts
    by `lastUsedAt` desc and slices to `MAX_PROFILES = 6` (LRU eviction of the oldest).
  - `forget(email)` — removes the matching profile (case-insensitive, trimmed).
  - `clear()` — empties `profiles`.
  - `dismissPinPrompt(email)` — appends the email to `pinDismissed` once (no-op if already
    present).
- **`quickAccessStore.test.ts`** — 5 vitest cases (mirrors `authStore.test.ts` style, a
  `beforeEach` state reset): derived initials + positive `lastUsedAt`; case-insensitive
  upsert (no duplicate, latest name/role win); cap-6 with oldest `lastUsedAt` evicted;
  `forget` by email + `clear`; `dismissPinPrompt` dedupe.

## Why It Changed?
The quick-login feature needs the browser to remember who has signed in on this shared
front-of-house device so the login screen can show tap-to-select chips instead of forcing a
full email+password entry every shift change. The list is intentionally device-local (never
sent to the server), capped and LRU-evicted to stay small, and keyed case-insensitively by
email so re-logins update rather than pile up. `pinDismissed` lets EMB-FEAT-10 stop nagging
a user who has declined to set a PIN, without a server round-trip.

## Verification
- `pnpm run test:run quickAccessStore` — 5/5 PASS (failed first with "module not found" before
  the implementation existed).
- `pnpm run build` — PASS (0 TS errors).
- `pnpm run lint` — 0 errors (17 pre-existing warnings, none in the new files).
