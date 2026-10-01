# Report 668 — RELEASE-V0.3.5

## 1. Identification
- Report: 668
- Task ID: RELEASE-V0.3.5
- Predecessor: 667 (LINK-PANEL-ORDER-AND-UNLINK-SIZE)

## 2. Objective
Squash the `feat/table-merge` work into `main`, push `main`, rebuild the Ember Hub installer, tag `v0.3.5` and hand over the deploy commands.

## 3. Modified Files
- `backend/pom.xml` (0.3.4 -> 0.3.5)
- The feature work (reports 661-667) squashed into one commit on `main` (`be8f7cd9`, 95 files): merge tables into an open session (`V24`), kitchen/report labels, bulk delete of order items, waiter floor (one wide merged card with animation, link panel, drag-and-drop linking).
- Excluded on purpose (still local/untracked): `.idea/compiler.xml`, `backend/mvnw.cmd`, `landing/CLAUDE.md`, `docs/legal/`, the older `docs/superpowers/` plans/specs.
- Built (git-ignored): `ember-hub/dist/EmberHubSetup-0.3.5.exe`.

## 4. What Changed?
`feat/table-merge` (7 commits) was squash-merged into `main` as `be8f7cd9`; `38bd7a7a` bumps the pom. `main` was equal to `origin/main` before the merge, so the push is a fast-forward. `ember-hub/build-installer.ps1` (all stages) produced `EmberHubSetup-0.3.5.exe` — 181.2 MB (189,979,766 bytes), SHA-256 `195700971584304d25722eb1efd24e9f0261b72ed3a954bd15f26d390ec717bb`. Tag `v0.3.5` triggers `backend-image.yml`, which builds `ember-backend:0.3.5`.

## 5. Why It Changed?
Ship the table-merge feature set. The pom version names the Hub installer and must differ per release.

### Verification (on `main`, with the pom at 0.3.5)
- Backend `./mvnw test`: 1756/1756.
- Frontend `pnpm run test:run`: 320/321 — the one failure is `MenuJoin.test.tsx` "authenticated: submitting the name joins via the QR token", already failing on `main` before this work and not touched. `pnpm run build` exit 0; `pnpm run lint` 0 errors (15 pre-existing warnings).
- Hub installer build exit 0.

### NOT verified
- The Hub installer was built but not installed or run. `V24` (idempotent `ADD COLUMN IF NOT EXISTS`) has only run on H2 in tests and on the user's local Postgres at boot, not against production. What is actually running in production is unconfirmed: check `flyway_schema_history` and the running image tag before the deploy (commands in the hand-over).
- Reduced-motion, the two-browser race for linking and the kitchen/ticket/analytics flow end to end were not exercised on a live system.
