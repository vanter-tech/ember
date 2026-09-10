#!/usr/bin/env bash
#
# Publish a Windows installer to the public downloads bucket and refresh the CDN.
#
#   deploy/publish-installer.sh <agent|hub> <version> <path-to-.exe>
#
# Example:
#   deploy/publish-installer.sh agent 0.1.1 printing-agent/dist/EmberAgentSetup-0.1.1.exe
#
# What it does:
#   1. uploads  EmberAgentSetup-<version>.exe   (immutable, long cache)
#   2. copies it to  EmberAgentSetup-latest.exe (short cache — the URL the sites link to)
#   3. purges the Cloudflare edge cache for the -latest URL so the new build is
#      served immediately (Cloudflare otherwise keeps .exe at the edge for ~4 h)
#
# Requirements:
#   - gcloud, authenticated, project ember-prod-vanter (Cloud Shell already is)
#   - a Cloudflare API token with  Zone -> Cache Purge -> Purge  on the vanter.net
#     zone, stored in Secret Manager as  cloudflare-cache-purge-token
#     (one-time: printf %s '<TOKEN>' | gcloud secrets create cloudflare-cache-purge-token \
#        --data-file=- --replication-policy=automatic)

set -euo pipefail

BUCKET="gs://ember-downloads-prod"
CF_ZONE_ID="${CF_ZONE_ID:-167aaa15781ebd1455a86eec0ac5ec9b}"   # vanter.net zone id (not a secret; in every CF API URL)
CF_TOKEN_SECRET="${CF_TOKEN_SECRET:-cloudflare-cache-purge-token}"
PUBLIC_HOST="downloads.ember.vanter.net"

product="${1:-}"
version="${2:-}"
exe="${3:-}"

case "$product" in
  agent) base="EmberAgentSetup" ;;
  hub)   base="EmberHubSetup" ;;
  *) echo "usage: $0 <agent|hub> <version> <path-to-.exe>" >&2; exit 2 ;;
esac
[ -n "$version" ] && [ -f "$exe" ] || { echo "usage: $0 <agent|hub> <version> <path-to-.exe>" >&2; exit 2; }

versioned="${base}-${version}.exe"
latest="${base}-latest.exe"

echo ">> uploading ${versioned}"
gcloud storage cp --cache-control="public, max-age=31536000, immutable" \
  "$exe" "${BUCKET}/${versioned}"

echo ">> updating ${latest}"
gcloud storage cp --cache-control="public, max-age=300" \
  "${BUCKET}/${versioned}" "${BUCKET}/${latest}"

echo ">> purging Cloudflare cache for https://${PUBLIC_HOST}/${latest}"
if [ "$CF_ZONE_ID" = "REPLACE_WITH_VANTER_NET_ZONE_ID" ]; then
  echo "!! CF_ZONE_ID not set — edit this script or export CF_ZONE_ID. Skipping purge." >&2
  echo "   New build is uploaded; the edge will catch up within ~4 h without a purge." >&2
  exit 0
fi
token="$(gcloud secrets versions access latest --secret "$CF_TOKEN_SECRET")"
resp="$(curl -sS -X POST \
  "https://api.cloudflare.com/client/v4/zones/${CF_ZONE_ID}/purge_cache" \
  -H "Authorization: Bearer ${token}" \
  -H "Content-Type: application/json" \
  --data "{\"files\":[\"https://${PUBLIC_HOST}/${latest}\",\"https://${PUBLIC_HOST}/${versioned}\"]}")"
echo "$resp"
echo "$resp" | grep -q '"success":true' || { echo "!! Cloudflare purge failed" >&2; exit 1; }

echo ">> done: https://${PUBLIC_HOST}/${latest}"
