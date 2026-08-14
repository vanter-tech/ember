# Report 41 — backend boot fix (schema drift, not a backlog task)

**Predecessor Task:** task-5.2

## Objective
Fix the local backend failing to boot/serve requests, reported as "not running... since credentials were moved."

## Modified Files
- `.env` (gitignored, not committed — local-only edit)
- `backend/src/main/resources/db/migration/V3__restaurant_columns_backfill.sql` (new)

## What Changed?
Two independent, sequential root causes, found via `docker ps` and the actual boot log rather than assumption:

1. **`.env` pointed at Docker Compose service hostnames** (`postgres`, `mongodb`, `minio`) instead of `localhost`. Nothing in this session had edited `.env` — its mtime (Aug 12) predates every commit made here. `docker ps` showed `ember-postgres-1`/`ember-mongodb-1`/`ember-minio-1` up and healthy on host-mapped ports, but `ember-app-1` (the backend container) stopped — so the intended run mode is the host JVM talking to those containers over `localhost`, which `application.yml` already anticipates via an optional `.env.local` override; edited `.env` directly instead, per instruction, since local dev is host-based right now. Fixed `SPRING_DATASOURCE_URL`/`SPRING_DATA_MONGODB_URI`/`MINIO_URL` to `localhost`.
2. **`restaurants` table was missing 7 columns** (`name`, `slug`, `plan`, `status`, `timezone`, `currency`, `created_at`) — a genuine, pre-existing schema-migration gap. The table predates task-2.10/4.4; `ddl-auto: update` cannot add a `NOT NULL` column to a non-empty table without a default, so all seven `ALTER TABLE`s have been silently failing (`WARN`, not fatal) on every boot since, leaving `jwtAuthFilter`'s per-request `Restaurant` lookup broken. Added `V3__restaurant_columns_backfill.sql`, following `V2`'s add-nullable → backfill → set-NOT-NULL pattern, backfilling the existing row deterministically from `id` and adding the `plan`/`status` CHECK constraints and the `slug` UNIQUE constraint Hibernate had also been failing to add.

## Why It Changed?
Fix #1 alone got the app past the connection error but immediately surfaced fix #2 (a real, previously-silent bug) — confirmed by reading the full boot log rather than stopping at the first error.

## Verification
- `./mvnw spring-boot:run`: `Started EmberApplication in 6.981 seconds`, no DDL warnings on `restaurants`.
- `flyway_schema_history`: V3 recorded `success = t`.
- `GET /v1/actuator/health`: `db` and `mongo` both `UP`.
- `GET /v1/public/restaurants/restaurant-a04f4ae4/branding`: returns real data end-to-end.
