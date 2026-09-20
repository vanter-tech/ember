# Report 521 — HUB-PRINT-1: the print agent can pair with an on-premise Hub

## 1. Identification
- **Report number:** 521
- **Task ID:** HUB-PRINT-1
- **Predecessor:** report 518 (last report on `main`; 519 and 520 live on unmerged branches)

## 2. Objective
Let the existing `printing-agent` pair with an Ember Hub on the LAN, so a caja PC with a USB printer prints tickets issued by the Hub exactly like the web version does today, without a second print path.

## 3. Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/PairingClient.java`
- `printing-agent/src/test/java/com/vanter/emberagent/PairingClientTest.java`
- `printing-agent/ui/src/components/PairingSection.tsx`
- `printing-agent/ui/src/components/PairingSection.test.tsx`
- `printing-agent/ui/src/lib/server-url.ts` (new)
- `printing-agent/ui/src/lib/server-url.test.ts` (new)
- `PROGRESS.md`, `reports/521-task-hub-print-1-agent-server-field.md`

## 4. What Changed?
- The pairing dialog has a **Servidor** field, prefilled with the cloud (`https://api.ember.vanter.net/v1`). For a Hub the operator types `http://<hub-ip>:8080` (or `http://localhost:8080` when the Hub is on the same PC). `normalizeServerUrl` trims, adds `http://` when the scheme is missing and drops trailing slashes; an empty value is refused with a message. Both pairing modes (code and API key) use it.
- `PairingClient.redeem` persists the URL it redeemed against (trailing slash trimmed) instead of the `backendBaseUrl` field of the response.
- Tests: new `PairingClientTest` case (Hub answers with the cloud URL, the agent keeps its own), 3 `PairingSection` cases (default cloud, normalized Hub address, empty server refused), 3 `normalizeServerUrl` cases.

## 5. Why It Changed?
The agent had the cloud URL hardcoded in the UI, and after pairing it adopted the URL in the response — which the backend fills from `ember.agent.backend-base-url` (cloud by default, and unaware of a Hub's changing LAN IP or its root context path). Pairing against a Hub therefore either was impossible or would have pointed the agent back at the cloud. Everything else needed is already shared: the Hub runs the same `printing` module (agents, printer config, job queue, `/ws/print-agent`) and its installer opens the 8080 firewall rule for LAN PCs.

Verification: agent `mvnw -f printing-agent/pom.xml test` **53/53**, `printing-agent/ui` `pnpm run test` **11/11**, `pnpm run build` clean. Not verified: a real pairing between a caja and a Hub with a physical printer (needs a manual test).

**Known gap (next task):** `PrintDispatchService` routes by role only, so with several cajas each receipt prints on every active `RECEIPT` printer. Agreed direction: for the Hub profile, route a receipt to the agent connected from the requester's source IP. The agent version in `pom.xml` was not bumped; do it when building the next installer.
