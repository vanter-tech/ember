# Report 525 — AGENT-UI-HARDENING, part 2: "Nube / Local" instead of a visible server address

## 1. Identification
- **Report number:** 525
- **Task ID:** AGENT-UI-HARDENING (part 2 of 2)
- **Predecessor:** report 524 (part 1); before that HUB-PRINT-1 (report 521) added the visible "Servidor" text field

## 2. Objective
The Ember API address must never be shown. Replace the "Servidor" text field of the pairing dialog by a dropdown: **Nube** (Ember Cloud) or **Local** (an on-premise Ember Hub, detected automatically).

## 3. Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/HubDiscovery.java` (new)
- `printing-agent/src/main/java/com/vanter/emberagent/PairingClient.java`
- `printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java`
- Tests: `HubDiscoveryTest` (new), `PairingClientTest`, `control/LocalControlServerTest`
- `printing-agent/ui/src/components/PairingSection.tsx`, `PairingSection.test.tsx`, `lib/api.ts`, `lib/types.ts`; removed `lib/server-url.ts` and `lib/server-url.test.ts`
- `printing-agent/README.md`, `PROGRESS.md`, this report

## 4. What Changed?
- **UI:** a `Servidor` dropdown (`Nube (Ember Cloud)` / `Local (Ember Hub en esta red)`), default Nube. It sends only `{code|apiKey, target: "cloud"|"local"}`; there is no address to show, type or send. While pairing locally it says "Buscando el servidor en la red…". The cloud address is no longer in the UI bundle.
- **Agent, cloud:** `LocalControlServer.CLOUD_URL` holds the address, so it is used from the Java side only.
- **Agent, local:** `HubDiscovery` tries this PC first and then every host of the local subnet (a /24; wider subnets are narrowed to the /24, smaller ones keep their size; up, non-loopback, non-virtual IPv4 interfaces) on port 8080, 64 probes in parallel, 12 s budget. A host is an Ember Hub only if `GET /` answers with a redirect to `/app/` (the Hub's bundled web app; the cloud and other servers do not do that). `PairingClient.redeemAny` tries the code on every candidate and keeps the one that accepts it; if none does, it reports rate-limited, then invalid code, then unreachable. With an API key, the first Hub found is used. No Hub found → "No se encontró ningún servidor Ember en esta red. Verifica que el Ember Hub esté encendido y en la misma red."
- An explicit `backendUrl` in `/api/pair` is still honoured for scripted use; the dashboard no longer sends it.
- Tests: discovery (redirect to `/app/` relative and absolute, redirect elsewhere, plain 404, nothing listening, subnet math), `redeemAny` (picks the right Hub, no candidates, invalid-code beats unreachable), control server (cloud/local with API key, no Hub found), UI (default cloud, Local, only two options and no `http`/host text anywhere).

## 5. Why It Changed?
Showing the API address exposes the service and invites poking at it, and asking a restaurant operator to type an IP is error-prone. The Hub's IP changes from one restaurant to the next, so it is discovered instead of typed.

Verification: agent `mvnw -f printing-agent/pom.xml test` **68/68**, `printing-agent/ui` `pnpm run test` **10/10**, `pnpm run build` clean; the built UI bundle has no occurrence of the cloud address.

**Limits / open:** the discovery only looks at port 8080 (the Hub's default `EMBER_HUB_SERVER_PORT`) and at the /24 of each interface; a Hub on another port or another subnet is not found. Any device on the LAN that answers the same redirect could be picked as a candidate, but the code only works on the real server. The cloud address still exists inside the .exe (the agent must know where to connect). Not tested on a real LAN, and a new agent build is needed to ship (bump `printing-agent/pom.xml`, next is `0.1.3`).

**Product note:** the landing says the agent is only for the cloud, which is true for Hubs whose printers are on the Hub PC or on the network; a caja with a **USB** printer still needs the agent (Local mode) next to a Hub. The landing wording may need a clarification, or folding the agent into the Hub installer as a "station" mode (not decided).
