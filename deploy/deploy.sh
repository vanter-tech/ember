#!/usr/bin/env bash
set -euo pipefail

VM="${EMBER_VM:-ember-prod}"
ZONE="${EMBER_ZONE:-us-central1-a}"
TAG="${1:-latest}"
SECRET="${EMBER_ENV_SECRET:-ember-prod-env}"   # Secret Manager secret holding the full .env body

echo ">> pushing /opt/ember/.env from Secret Manager secret '${SECRET}'"
gcloud secrets versions access latest --secret "${SECRET}" \
  | gcloud compute ssh "${VM}" --zone "${ZONE}" --tunnel-through-iap --command \
    "sudo install -m 600 /dev/stdin /opt/ember/.env && echo 'EMBER_IMAGE_TAG=${TAG}' | sudo tee -a /opt/ember/.env >/dev/null"

echo ">> deploying tag ${TAG}"
gcloud compute ssh "${VM}" --zone "${ZONE}" --tunnel-through-iap --command "
  set -e
  cd /opt/ember
  sudo docker compose -f docker-compose.prod.yml pull
  sudo docker compose -f docker-compose.prod.yml up -d
  echo 'waiting for app health...'
  for i in \$(seq 1 20); do
    if sudo docker compose -f docker-compose.prod.yml exec -T app wget -qO- http://localhost:8081/actuator/health | grep -q UP; then
      echo 'app healthy'; exit 0
    fi
    sleep 6
  done
  echo 'app did NOT become healthy'; sudo docker compose -f docker-compose.prod.yml logs --tail=80 app; exit 1
"
