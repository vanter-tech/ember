#!/usr/bin/env bash
set -euo pipefail

STAMP="$(date -u +%Y-%m-%dT%H)"            # YYYY-MM-DDTHH, one file per hourly run
DOW="$(date -u +%u)"                       # 1..7, 7 = Sunday
HOUR="$(date -u +%H)"
TMP="/tmp/${STAMP}.dump.gz"

echo "[$(date -u +%FT%TZ)] dumping ${PGDATABASE}"
pg_dump -Fc "${PGDATABASE}" | gzip > "${TMP}"

gcloud storage cp "${TMP}" "gs://${GCS_BUCKET}/postgres/${STAMP}.dump.gz"
if [ "${DOW}" = "7" ] && [ "${HOUR}" = "08" ]; then
  gcloud storage cp "${TMP}" "gs://${GCS_BUCKET}/postgres-weekly/${STAMP}.dump.gz"
fi
rm -f "${TMP}"

prune() {   # $1 = prefix, $2 = keep count
  mapfile -t objs < <(gcloud storage ls "gs://${GCS_BUCKET}/$1/" 2>/dev/null | sort)
  local excess=$(( ${#objs[@]} - $2 ))
  for (( i=0; i<excess; i++ )); do
    echo "pruning ${objs[$i]}"
    gcloud storage rm "${objs[$i]}"
  done
}
prune "postgres" "${HOURLY_RETENTION}"
prune "postgres-weekly" "${WEEKLY_RETENTION}"
echo "[$(date -u +%FT%TZ)] done"
