# Report 700 — VM runs as a dedicated least-privilege service account (GCS hardening task 5)

## 1. Identification
- **Report number:** 700
- **Current Task:** GCS-HARDENING task 5 — VM default service account has `roles/editor`
- **Predecessor Task:** Report 699 — GCS-MEDIA-BUCKET-CLOSED-AND-SA-KEY

## 2. Objective
Remove the project-wide Editor access that the VM's default compute service account carried, so code execution on the VM no longer reaches every bucket through the metadata token.

## 3. Modified Files
- `deploy/RUNBOOK.md`
- `PROGRESS.md`
- `reports/700-gcs-vm-dedicated-service-account.md` — new

## 4. What Changed?
- Created `ember-vm@ember-prod-vanter.iam.gserviceaccount.com` with `roles/storage.objectAdmin` on `ember-backups-ember-prod-vanter` only, plus project `roles/logging.logWriter` and `roles/monitoring.metricWriter`. Those are the only things the VM uses its identity for (`backup.sh` and the Ops Agent); `deploy.sh`, Secret Manager and the downloads bucket run from the operator's account.
- Stopped `ember-prod`, `set-service-account` to `ember-vm` (scope `cloud-platform`), started it. A few minutes of downtime; all four containers came back healthy.
- `roles/editor` is no longer bound to `253780825021-compute@developer.gserviceaccount.com`. The user had already removed it before the final check, so the `remove-iam-policy-binding` call answered "binding not found"; `get-iam-policy` filtered on both accounts shows only `ember-vm` with the two project roles.
- RUNBOOK: new section after HPD-12 with the grants, procedure, verification and rollback.
- Found while verifying (not fixed here, see section 5): the hourly backup cron has been failing.

## 5. Why It Changed?
- Report 699 left one honest gap: the VM's default SA had Editor, so any code execution on the VM could read and write every bucket without a key. Least privilege on the VM identity closes it.
- **Unrelated finding, high priority:** `docker compose ... exec backup tail /var/log/backup.log` showed `backup.sh: line 9: PGDATABASE: unbound variable` on every run. Cron does not inherit the container's environment, so with `set -u` the hourly backup dies immediately. The newest cron-made object is `2026-09-15T02`; the dump of 2026-10-03T00 exists only because it was run by hand with `exec`. `PROGRESS.md` previously said the hourly cadence was "confirmed working" on 2026-09-15 — that confirmation covered a manual run. Until fixed, the only automatic recovery point is the daily disk snapshot (7 days). Queued as its own task.

## Verification (reported by the user, 2026-10-02)
- VM metadata email is `ember-vm@…`; `google-cloud-ops-agent` is `active`; `docker compose ps`: app and postgres healthy, backup and caddy up.
- Manual `exec backup /usr/local/bin/backup.sh` uploaded `postgres/2026-10-03T00.dump.gz` and ended with `done` under the new SA.
- Cloud Shell: syslog entries after the restart present in Cloud Logging. Metrics were not checked in the console (the role is granted; if absent, only `monitoring.metricWriter` is at fault).
- Not done: rollback was not rehearsed; the VM user's home directory is not writable (breaks the snap `gcloud` on the host, harmless to the containers).
