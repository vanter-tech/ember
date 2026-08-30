# ember-prod monitoring (HPD-14)

Cloud Monitoring notification channel + alert policies for the hosted production VM.
Applied by the maintainer against project `ember-prod-vanter`; see
`deploy/RUNBOOK.md` § "First-time provisioning → GCP → HPD-14".

## Files

| File | Purpose |
|---|---|
| `ops-agent-config.yaml` | `/etc/google-cloud-ops-agent/config.yaml` on the VM — hostmetrics + a Prometheus scrape of `localhost:8081/actuator/prometheus`. |
| `alert-cpu.json` | CPU utilisation > 90% for 10 min. |
| `alert-mem.json` | Guest memory used > 90% for 10 min (needs the Ops Agent). |
| `alert-disk.json` | Guest disk used > 85% for 5 min (needs the Ops Agent). |

## Apply

```bash
EMAIL=<ops-email>
CH=$(gcloud beta monitoring channels create --display-name="ember ops email" \
     --type=email --channel-labels=email_address="$EMAIL" --format="value(name)")

for f in cpu mem disk; do
  sed "s#\${CHANNEL}#${CH}#" "deploy/monitoring/alert-${f}.json" \
    | gcloud alpha monitoring policies create --policy-from-file=-
done
```

## Not here yet — HPD-20

The uptime check on `https://api.ember.vanter.net/v1/public/ping` and its alert
policy (`monitoring.googleapis.com/uptime_check/check_passed`) are created once the
Cloudflare Tunnel makes that hostname resolve.
