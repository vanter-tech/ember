# Report 485 — PILOT-READINESS Task 1: hourly Postgres backups

**Predecessor:** report 484 (Admin Corte Z shifts table)

## Objective
Lower the prod RPO from up to 24h (nightly `pg_dump`) to up to 1h (hourly), per
PILOT-READINESS item 1. Cloud VM only — the separate question of on-premise
Ember Hub tenants having no backup mechanism at all is deferred as its own
future task.

## Modified Files
- `deploy/backup/Dockerfile`
- `deploy/backup/backup.sh`
- `deploy/docker-compose.prod.yml`
- `deploy/RUNBOOK.md`

## What Changed?
- Cron schedule `0 8 * * *` (once daily, 08:00 UTC) → `0 * * * *` (every hour).
- `backup.sh`'s `STAMP` went from `%F` (`YYYY-MM-DD`) to `%Y-%m-%dT%H`, so each
  hourly run writes a distinct object instead of overwriting the same day's file.
- The Sunday "weekly" copy (`postgres-weekly/`) is now gated to a single hour
  (`08`, the old daily run time) so it still produces one weekly snapshot
  instead of 24.
- `docker-compose.prod.yml`'s `DAILY_RETENTION: "14"` env renamed to
  `HOURLY_RETENTION: "72"` (72 hourly objects ≈ 3 days of hourly coverage);
  `backup.sh`'s prune call updated to match. `WEEKLY_RETENTION` unchanged (8).
- `RUNBOOK.md`: topology diagram + filename-pattern references updated from
  "nightly"/`YYYY-MM-DD` to "hourly"/`YYYY-MM-DDTHH`; added a new "Updating the
  `backup` service" subsection under "Routine deploy" with the scp+ssh+rebuild
  commands, since `deploy.sh` only pulls/restarts `app` and never touches the
  `backup` container.

## Why It Changed?
PILOT-READINESS item 1: nightly dumps mean up to 24h of lost orders/sales/
cash-shift data on a VM failure, which is not acceptable before onboarding a
paying restaurant's real money/data. Hourly dumps to
`gs://ember-backups-ember-prod-vanter` is the documented minimum bar (WAL
archiving/PITR is the "ideal" but out of scope here). Pairs with HPD-21
(restore test, still pending).

**Not deployed to prod as part of this task** — `deploy.sh` doesn't cover the
`backup` container, so applying this requires the manual scp/rebuild sequence
now documented in `RUNBOOK.md`, run by the user from Cloud Shell.

## Verification
- `bash -n deploy/backup/backup.sh` — syntax OK.
- `docker compose -f deploy/docker-compose.prod.yml config` — parses cleanly;
  the only errors raised are the pre-existing required env vars
  (`BACKUP_GCS_BUCKET`, `SPRING_DATASOURCE_PASSWORD`) that only exist in
  `/opt/ember/.env` on the VM, unrelated to this change.
- No `mvn test`/`pnpm run build` — no application code touched.
