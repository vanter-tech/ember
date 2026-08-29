# Report 280 — HPD-08: CI build & push backend image to GHCR

## 1. Identification
- **Report number:** 280
- **Current Task ID:** HPD-08 (Hosted Production Deployment plan, `docs/superpowers/plans/2026-08-28-hosted-production-deployment.md`, Phase 2 / Task 8)
- **Predecessor Task:** HPD-07 (report 279 — `deploy/deploy.sh` + `deploy/RUNBOOK.md` skeleton)
- **Branch:** `feat/hosted-production-deployment`

## 2. Objective
Add a GitHub Actions workflow that builds the Spring Boot backend container image and
pushes it to `ghcr.io/vanter-tech/ember-backend` on every `v*` tag push, so the prod
Docker Compose stack (`deploy/docker-compose.prod.yml`, HPD-05) has a versioned image to
pull.

## 3. Modified Files
- **Created:** `.github/workflows/backend-image.yml`

## 4. What Changed?
New workflow `backend-image`:

- **Triggers:** `push` on tags matching `v*`, plus `workflow_dispatch` for manual runs.
- **Permissions:** `contents: read`, `packages: write` (the minimum for GHCR push via the
  built-in `GITHUB_TOKEN`).
- **Single `build` job** on `ubuntu-latest`:
  1. `actions/checkout@v4`.
  2. `docker/login-action@v3` → `ghcr.io`, authenticated as `github.actor` with
     `secrets.GITHUB_TOKEN`.
  3. **Derive tags** step: on a tag event, emits two tags —
     `ghcr.io/vanter-tech/ember-backend:<version>` (the tag name with a leading `v`
     stripped) and `:latest`; on a manual `workflow_dispatch` run it emits only
     `:<short-sha>` (no `:latest` move).
  4. `docker/build-push-action@v6` with `context: backend`, `push: true`, `tags` from the
     previous step.

No `setup-java` / `mvnw package` step: `backend/Dockerfile` is already a multi-stage build
(`maven:3.9-eclipse-temurin-17` stage runs `mvn package -DskipTests`, then copies
`target/*.jar` into a `17-jre-alpine` runtime stage), so the image build compiles the jar
itself. Adding a pre-build step would compile the backend twice per run.

**Verification:** `actionlint` (via `rhysd/actionlint:latest` container, which also runs
shellcheck over the `run:` block) — exit 0, no findings.

## 5. Why It Changed?
- HPD-05's `app` service pulls `ghcr.io/vanter-tech/ember-backend:${EMBER_IMAGE_TAG:-latest}`
  and `deploy/deploy.sh` (HPD-07) sets `EMBER_IMAGE_TAG` from its `$1` argument — nothing
  in the repo produced that image until now.
- Tag-driven (`v*`) rather than branch-push-driven: releases are explicit, and `:latest`
  only advances on a real version tag, so a routine `deploy.sh v1.2.3` always maps to an
  immutable published tag.

### Plan drift (vs. the plan's Task 8 sketch)
1. **Dropped the `actions/setup-java@v4` + "Package" step.** The plan's own step-1 note
   said to keep whichever avoids building twice and to check `backend/Dockerfile`; it is
   multi-stage and self-compiles, so the Docker build is the only build.
2. **Hardened the tag-derive step for `workflow_dispatch`.** The sketch's
   `echo "tag=${GITHUB_REF_NAME#v}"` would produce an invalid image tag containing `/`
   when run manually from a branch ref (e.g. `feat/hosted-production-deployment`). The
   step now branches on `GITHUB_REF_TYPE`: tag events keep the `v`-stripped version +
   `:latest`; manual runs publish a `:<short-sha>` tag only.

No backend/frontend code touched — test suite unchanged at **900/900** (report 275).
