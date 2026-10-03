# Report 701 — Hourly backup cron failed on unset environment (BACKUP-CRON-ENV)

## 1. Identification
- **Report number:** 701
- **Current Task:** BACKUP-CRON-ENV
- **Predecessor Task:** Report 700 — GCS-VM-DEDICATED-SERVICE-ACCOUNT

## 2. Objective
Make the hourly `pg_dump` backup actually run from cron. Found in report 700: every cron run died with `PGDATABASE: unbound variable`.

## 3. Modified Files
- `deploy/backup/Dockerfile`
- `deploy/RUNBOOK.md`
- `PROGRESS.md`
- `reports/701-backup-cron-env.md` — new

## 4. What Changed?
- `deploy/backup/Dockerfile`: the `CMD` writes `PGHOST`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`, `GCS_BUCKET`, `HOURLY_RETENTION`, `WEEKLY_RETENTION` to `/etc/backup.env` (`printf 'export %s=%q'`, mode 600) before starting `cron`. The cron line is now `SHELL=/bin/bash` + `0 * * * * root . /etc/backup.env && /usr/local/bin/backup.sh >> /var/log/backup.log 2>&1`.
- `deploy/RUNBOOK.md`: "Updating the `backup` service" now says to `git pull` first, explains the cron-env rule (a new variable in `backup.sh` must be added to the Dockerfile list), and gives the check that exercises the cron path (`exec ... env -i ... . /etc/backup.env`) instead of a manual `exec`.
- `backup.sh` itself is untouched.

## 5. Why It Changed?
- Cron starts jobs with a minimal environment, not the container's. `backup.sh` has `set -u`, so it aborted on its first variable every hour. Manual `docker compose exec backup backup.sh` worked because `exec` does carry the container env, which hid the failure; the 2026-09-15 "cadence confirmed" check was such a manual run. The newest cron-made dump was `2026-09-15T02`.
- `%q` quoting plus `SHELL=/bin/bash` (cron's default `sh` would not parse the `$'...'` that `%q` can emit) keeps passwords with spaces or quotes intact.

## Verification
- Local, Docker 29: built the image, ran it with a password `p@ss w'ord$x"y`; `/etc/backup.env` was written and `env -i bash -c '. /etc/backup.env'` restored it exactly.
- Local: switched the cron line to every minute in the test container; cron fired `backup.sh` with the env loaded and the log showed `dumping ember` then `pg_dump: could not translate host name "nohost"` (the fake host) — i.e. past the old `unbound variable` failure, twice in a row.
- Note: the Windows checkout has CRLF in `backup.sh`, which breaks the shebang if built from it locally; the repo index is LF and the VM checkout is LF, so prod is unaffected. The local test used LF copies.
- **Not done:** deploy to the VM and a real hourly run (user, Cloud Shell). Until then prod still has no working hourly backup. Also not done: alerting on failed backups.
