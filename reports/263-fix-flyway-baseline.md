# Report 263 — realign the dev DB's Flyway baseline to the consolidated V1–V5 scheme

## 1. Identification
- **Report number:** 263
- **Task ID:** fix-flyway-baseline
- **Predecessor Task:** report 262 (fix-user-role-ui)

## 2. Objective
Since the 2026-08-24 migration consolidation, the migration set is `V1__baseline_consolidated`
(full schema through 2026-08-24, old V2–V15 folded in) → `V2` hub_activations → `V3`
printer_config_windows_queue → `V4` printer_config_render_mode → `V5` cash_shift_expiry.

A genuinely empty database (every fresh Ember Hub install) runs V1–V5 in order and is correct.
But the real **dev** DB's `flyway_schema_history` still carried a single baseline row at the
**old V15**, set by hand during that day's `flyway:clean` recovery. Flyway runs only versions
`> 15`, so the renumbered V2–V5 are "below baseline" and silently skipped on every boot — their
schema changes were applied to dev by hand instead (render_mode in report 254, cash_shift_expiry
in report 259). Goal: fix the bookkeeping so future migrations (V6+) apply on dev with no manual
step, without disturbing the working fresh-install path.

## 3. Modified Files
- `docs/db/dev-rebaseline-v5.sql` (new)
- `backend/src/main/resources/application.yml` (comment only)

## 4. What Changed?
- **`docs/db/dev-rebaseline-v5.sql` (new):** a one-time, DEV-ONLY SQL script. It guards that
  `flyway_schema_history` is exactly the expected stale single-row V15 baseline (aborts
  otherwise), then `DELETE`s it and inserts one `BASELINE` row at `version = '5'`. Truthful,
  because the dev schema already physically contains everything through V5. After it runs,
  Flyway's "current version" on dev is 5 and V6+ apply normally. Includes the runbook
  (stop backend → `pg_dump` backup → `psql -f` → restart, expecting "No migration necessary").
  Lives in `docs/`, **not** `db/migration/`, so Flyway never executes it.
- **`application.yml`:** replaced the stale `flyway:` comment (which still described the
  pre-consolidation "adopt existing state as V1, migrations start at V2" situation) with an
  accurate note about the empty-DB bootstrap path, the dev V15-baseline history, this script,
  and the queued `ddl-auto=validate` follow-up. No config value changed
  (`baseline-on-migrate: true`, `baseline-version: 1` still correct for a non-empty unversioned
  schema).

## 5. Why It Changed?
Only the dev DB's Flyway *bookkeeping* was wrong — not its schema, and not the fresh-install
migration path (Hub installs are Flyway-only and already work). Re-baselining dev at V5 is the
minimal truthful fix. Options considered and rejected: inserting per-migration rows (needs
hand-computed CRC32 checksums, no `flyway repair` available — no maven plugin); renumbering
V2–V5 → V16–V19 (would fail on dev where the columns already exist unless rewritten idempotent,
and half-abandons the consolidated V1); re-consolidating into a new V1 (changes V1's checksum,
breaks any DB that ran it, dev still needs a re-baseline anyway).

**Deliberately out of scope — queued follow-up:** dev still runs `ddl-auto=update`, which
masks Flyway (it auto-patches the schema, which is why dev "worked" despite skipping V2–V5).
Switching dev to `ddl-auto=validate` (Flyway as sole schema owner) is the correct end state but
its own task: booting with `validate` will surface the "cosmetic drift" the V1 header documents
(Hibernate-generated constraint names vs the hand-authored names, e.g.
`platform_operators_email_key` vs `uk_platform_operators_email`), each needing a reconciliation
migration. Bundling that here would turn a 10-minute bookkeeping fix into an open-ended schema
audit.

## Verification
- Backend `./mvnw test` — full suite PASS (comment/docs-only change, no behavior impact).
- The re-baseline itself is applied and confirmed against the live dev DB by the user (same
  manual-step pattern as report 254): after running the script, backend boot logs show Flyway
  validating 5 migrations and "No migration necessary", not re-attempting V2–V5.
