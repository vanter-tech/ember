# Archived migrations (V2–V15)

These are the original, hand-authored migrations from before the schema was
consolidated into `../migration/V1__baseline_consolidated.sql` (2026-08-24).
Their cumulative effect is now inside V1 — they are kept here purely for
their historical/explanatory value (each file's comments explain *why* a
change was made, which the consolidated dump doesn't carry).

This directory is **not** scanned by Flyway (only `classpath:db/migration`
is) — these files will never be re-applied. Do not move them back.
