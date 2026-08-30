# Report 284 — HPD-12: GCS backup bucket, SA role, disk snapshots

## 1. Identification
- **Report number:** 284
- **Current Task ID:** HPD-12
- **Predecessor Task:** HPD-11 (report 283 — GCP project baseline + VM + firewall)
- **Branch:** `feat/hpd-12-backups` (stacked on `feat/hpd-11-runbook`, off `main` `8fd4a9e`)

## 2. Objective
Give the production VM a private, versioned GCS bucket it can write nightly `pg_dump`
archives to (least privilege — write access to that one bucket only), and add a daily
boot-disk snapshot schedule for point-in-time VM recovery.

## 3. Modified Files
- `deploy/RUNBOOK.md` — added the `#### HPD-12 — executed 2026-08-29` block to
  `First-time provisioning → GCP` with the executed commands, verification output, and
  the multi-region deviation note.
- `PROGRESS.md` — new "Last Completed Task (report 284)" entry, Current Active Task
  reset to HPD-13, System health prefix updated, HPD-12 checkbox ticked.
- `reports/284-hpd-12-backups.md` — this report.

No application code, tests, or build artifacts touched.

## 4. What Changed?

### Infrastructure (executed live by the operator, project `ember-prod-vanter`)
- **Bucket:** `gs://ember-backups-ember-prod-vanter` already existed as a private
  bucket. Verified state: `public_access_prevention: enforced`,
  `uniform_bucket_level_access: true`, `location: US`.
- **Object versioning:** enabled via
  `gcloud storage buckets update … --versioning` (`versioning_enabled: true`) — keeps a
  prior generation of each dump object if it is ever overwritten or deleted.
- **Service-account grant:** the VM runs as the default compute service account
  `253780825021-compute@developer.gserviceaccount.com`, which already carries the
  `https://www.googleapis.com/auth/cloud-platform` scope, so no
  `set-service-account` + stop/start was needed. Granted it
  `roles/storage.objectAdmin` **on the bucket only** (not project-wide) via
  `gcloud storage buckets add-iam-policy-binding`. Confirmed in the bucket IAM policy.
- **Daily disk snapshots:** created resource policy `ember-daily-snap`
  (`--region=us-central1`, `--max-retention-days=7`, `--daily-schedule`,
  `--start-time=07:00` UTC, `--on-source-disk-delete=keep-auto-snapshots`) and
  attached it to the `ember-prod` boot disk. `gcloud compute disks describe` lists the
  policy on the disk.

### Repo
- `deploy/RUNBOOK.md` §GCP now documents all of the above as runnable commands plus the
  expected verification output.

## 5. Why It Changed?
- **Keyless, bucket-scoped write:** `deploy/backup/backup.sh` (HPD-06) authenticates to
  GCS via the VM's attached service account through the metadata server — no key file.
  For that to work the SA needs write access, and `roles/storage.objectAdmin` on just
  the backup bucket is the least-privilege grant that lets it create, overwrite, and
  prune dump objects without touching anything else in the project.
- **Object versioning + PAP:** versioning is a cheap backstop against a buggy prune or
  an accidental delete wiping a needed dump; `public_access_prevention: enforced`
  guarantees a database backup can never be exposed publicly.
- **Daily disk snapshots:** the RUNBOOK's "VM lost" recovery path is "recreate from the
  most recent disk snapshot"; a 7-day daily schedule bounds data loss to ~24h of VM
  state while keeping snapshot storage small. `keep-auto-snapshots` means deleting the
  instance does not cascade-delete its recovery points.
- **DEVIATION — `US` multi-region bucket:** the plan created the bucket with
  `--location=us-central1`; as provisioned it is `US` multi-region. This only raises
  storage cost marginally and increases durability. `backup.sh` addresses the bucket by
  name (`gs://${GCS_BUCKET}/…`), so nothing depends on its region. Left as-is and
  recorded rather than recreating the bucket.
