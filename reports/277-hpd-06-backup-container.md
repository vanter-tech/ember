# Report 277 — HPD-06: nightly `pg_dump` → GCS backup container

## 1. Identification
- **Report number:** 277
- **Task ID:** HPD-06 (Hosted Production Deployment plan, Phase 2 — deployment artifacts)
- **Predecessor task:** HPD-05 (report 276 — production Docker Compose stack)

## 2. Objective
Author the `backup` service image referenced by `deploy/docker-compose.prod.yml`: a container that
runs a nightly `pg_dump` of the production database, uploads the compressed archive to a private
GCS bucket, keeps a Sunday weekly copy, and prunes both series to their retention counts. No app
code, no unit tests.

## 3. Modified Files
- `deploy/backup/Dockerfile` (new)
- `deploy/backup/backup.sh` (new)
- `reports/277-hpd-06-backup-container.md` (new)
- `PROGRESS.md`

## 4. What Changed?
### `deploy/backup/Dockerfile` (new)
- Base `google/cloud-sdk:slim` (ships `gcloud storage`; auth comes from the VM's attached service
  account via the metadata server — no key file, no `MINIO_*`-style secret).
- Installs `postgresql-client` (for `pg_dump`) and `cron`, then clears the apt lists.
- Copies `backup.sh` to `/usr/local/bin/`, marks it executable.
- Writes `/etc/cron.d/ember-backup` with a single `0 8 * * * root /usr/local/bin/backup.sh >>
  /var/log/backup.log 2>&1` entry (08:00 UTC daily), mode `0644`.
- `CMD` starts `cron` and `tail -F /var/log/backup.log` so the container stays up and its output is
  visible via `docker compose logs backup`.

### `deploy/backup/backup.sh` (new)
- `set -euo pipefail`.
- `STAMP` = `date -u +%F` (`YYYY-MM-DD`), `DOW` = `date -u +%u` (`7` = Sunday).
- `pg_dump -Fc "$PGDATABASE" | gzip > /tmp/<STAMP>.dump.gz` — custom-format dump, gzipped.
  Connection comes from the standard `PGHOST/PGUSER/PGPASSWORD/PGDATABASE` env the compose file
  already passes to the service.
- `gcloud storage cp` the archive to `gs://$GCS_BUCKET/postgres/<STAMP>.dump.gz`; on Sunday also
  copies it to `gs://$GCS_BUCKET/postgres-weekly/<STAMP>.dump.gz`; removes the temp file.
- `prune(prefix, keep)` — lists `gs://$GCS_BUCKET/<prefix>/` sorted ascending (oldest first) into
  an array and `gcloud storage rm`s the leading `count - keep` objects. Called for `postgres`
  (`DAILY_RETENTION`, 14 in the compose) and `postgres-weekly` (`WEEKLY_RETENTION`, 8).

### Verification
- `docker build -t ember-backup-test deploy/backup` → image built clean.
- Dry run against the running dev `postgres` container (network `ember_default`):
  `docker run --rm --network ember_default -e PGHOST=postgres -e PGUSER=ember -e PGPASSWORD=ember
  -e PGDATABASE=ember ... ember-backup-test bash -c 'pg_dump -Fc "$PGDATABASE" | gzip > /tmp/t.gz
  && ls -l /tmp/t.gz'` → **`-rw-r--r-- 1 root root 17839 ... /tmp/t.gz`** (non-empty dump; the
  `gcloud storage cp`/`prune` steps need a real bucket + SA and are exercised in Phase 3 Task 12).
- `bash -n deploy/backup/backup.sh` → syntax OK. `shellcheck` is not installed on this host, so it
  was not run (plan Step 3 lists it as optional for HPD-07's scripts, "warnings acceptable").
- No `./mvnw` / `pnpm` gates — this task adds no application code. Backend suite stays at 900/900.

## 5. Why It Changed?
The spec's durability plan is nightly logical backups off the VM. Keeping the backup job as its
own container in the same Compose project means it starts/stops with the stack, shares the private
compose network to reach `postgres` by service name, and needs no host cron on the VM. Using
`gcloud storage` with metadata-server credentials keeps GCS access keyless — the only backup
secret is the bucket name, already in `.env.prod.example`. Retention is split daily/weekly so a
silent-corruption bug caught late still has an 8-week-old known-good archive to restore from.
