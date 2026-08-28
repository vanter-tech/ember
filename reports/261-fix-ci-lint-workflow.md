# Report 261 — fix CI lint workflow (unblock PR merges to `main`)

## 1. Identification
- **Report number:** 261
- **Task ID:** fix-ci-lint-workflow
- **Predecessor Task:** report 260 (fix-frontend-lint-errors)

## 2. Objective
After BRANCH EVENT 6 (report 260 / PR #55), the `Monorepo Linting CI` workflow was found to be
structurally incapable of satisfying `main`'s `EMBER - PROTECTION` ruleset, so no `fix/*` / `feat/*`
PR could merge normally. Repair it.

## 3. Modified Files
- `.github/workflows/lint.yml`

## 4. What Changed?
Three defects, all in `lint.yml`:

1. **`lint-frontend` job ran on npm in a pnpm repo.** It used `npm ci` +
   `cache-dependency-path: ./frontend/package-lock.json`; the repo has `frontend/pnpm-lock.yaml`
   (lockfileVersion 9.0) and no `package-lock.json`. `actions/setup-node` with `cache: npm` +
   an unresolvable cache path **hard-fails the step at ~5s**, before `npm ci` / eslint ever run —
   red on every historical run. Replaced with `pnpm/action-setup@v4` (v10) → `setup-node`
   (`cache: pnpm`, `cache-dependency-path: ./frontend/pnpm-lock.yaml`) → `pnpm install
   --frozen-lockfile` → `pnpm run lint`.

2. **Job names never matched the required status-check contexts.** The ruleset requires contexts
   `lint-backend` / `lint-frontend` / `lint-gateway`; the jobs were named `Lint Java Backend` /
   `Lint React Frontend`. GitHub matches required contexts by exact check-run name, so none were
   ever satisfied → every PR to `main` sat `BLOCKED` regardless of pass/fail. Renamed the two
   job `name:` values to `lint-backend` / `lint-frontend`.

3. **No job produced `lint-gateway`.** There is no `gateway/` module (EMB-GATEWAY unstarted).
   Added a trivial always-passing `lint-gateway` job (`echo`) purely to satisfy the third
   required context. Its body should be replaced with a real lint when a `gateway/` module
   exists. (User-approved: chosen over editing the ruleset, which needs repo-admin access.)

## 5. Why It Changed?
`main`'s ruleset is `enforcement: active` with 3 required status checks. Two of the three could
never go green (name mismatch + broken frontend job) and the third had no producer, so the
PR-per-task workflow the team is moving to (BRANCH EVENT 6) was completely blocked. This is
infrastructure repair, no application code touched.

## Verification
- PR #56: `lint-backend` **SUCCESS**, `lint-frontend` **SUCCESS**, `lint-gateway` **SUCCESS**;
  `mergeStateStatus: CLEAN`. Merged to `main` as `b7fc1b7`.
- Frontend lint content itself was already green as of report 260 (`pnpm run lint` exit 0).
