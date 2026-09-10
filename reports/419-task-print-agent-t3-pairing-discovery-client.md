# Report 419 — EMB-PRINT-AGENT T3: PairingClient + WindowsPrinterEnumerator + discovery reporting

## 1. Identification
- **Report number:** 419
- **Current Task ID:** EMB-PRINT-AGENT T3 (plan `docs/superpowers/plans/2026-09-08-print-agent-installer.md`)
- **Predecessor Task:** EMB-PRINT-AGENT T2 — report 418 (StatusHub, DPAPI credential store, `AgentConfig.resolve`)

## 2. Objective
Give the print agent the two pieces it needs to onboard without a hand-edited
`agent.properties`: redeem a one-time pairing code for the persistent API key, and
enumerate the PC's Windows print queues so the admin UI (T5) can offer them as a
dropdown with an inkjet→`DRIVER` pre-pick. Wiring into the agent runtime is T4;
this task only builds and unit-tests the classes.

## 3. Modified Files
Created:
- `printing-agent/src/main/java/com/vanter/emberagent/DiscoveredPrinter.java`
- `printing-agent/src/main/java/com/vanter/emberagent/PairingException.java`
- `printing-agent/src/main/java/com/vanter/emberagent/PairingClient.java`
- `printing-agent/src/main/java/com/vanter/emberagent/WindowsPrinterEnumerator.java`
- `printing-agent/src/main/java/com/vanter/emberagent/DiscoveredPrintersClient.java`
- `printing-agent/src/test/java/com/vanter/emberagent/PairingClientTest.java`
- `printing-agent/src/test/java/com/vanter/emberagent/WindowsPrinterEnumeratorTest.java`
- `printing-agent/src/test/java/com/vanter/emberagent/DiscoveredPrintersClientTest.java`
- `printing-agent/src/test/resources/get-printer-sample.json`

## 4. What Changed?
- **`DiscoveredPrinter`** — `record(String name, String driverName, String portName, boolean inkjetGuess)`, an agent-side twin of `com.vanter.ember.printing.model.DiscoveredPrinter` (identical field names so the JSON round-trips; the agent module can't import backend classes).
- **`PairingException`** — small `RuntimeException` whose message is user-facing Spanish text.
- **`PairingClient(CredentialStore store [, HttpClient http])`** — `redeem(backendBaseUrl, code)`:
  `POST {baseUrl}/printing/agents/pair` with `{"code": <trimmed>}`; `429` → `PairingException("Demasiados intentos…")`, any non-200 → `PairingException("Código inválido, usado o vencido.")`; on 200 it parses the backend `PairResponse` shape `{apiKey, backendBaseUrl, agentId, agentName}`, calls `store.save(new AgentCredential(apiKey, backendBaseUrl))`, and returns the credential. Transport failures are wrapped as `PairingException("No se pudo contactar al servidor: …")`. Package-private `HttpClient` ctor for hermetic tests, mirroring `AuthClient`.
- **`WindowsPrinterEnumerator`** — `List<DiscoveredPrinter> enumerate()` runs `powershell -NoProfile -NonInteractive -Command "Get-Printer | Select-Object Name,DriverName,PortName | ConvertTo-Json -Compress"`, returns `[]` on non-Windows or any failure (never throws). Static package-visible `parse(String json)` handles both a JSON array and the bare single object `ConvertTo-Json` emits for one printer, and returns `[]` for null/blank/garbage. Static `looksLikeInkjet(String)` matches EcoTank/inkjet/photo driver families (`ecotank`, `pixma`, `L####`, `ET-####`, …) case-insensitively. Uses `java.lang.System.Logger` to match the module's logging convention (the `credential/*` classes).
- **`DiscoveredPrintersClient([HttpClient http])`** — `report(backendBaseUrl, jwt, printers)`:
  `POST {baseUrl}/printing/agents/me/discovered-printers` with `{"printers":[…]}` and `Authorization: Bearer {jwt}`; a status other than `204` is logged at WARNING and swallowed — discovery is best-effort and must never block printing. Mirrors `PrinterConfigClient`'s `HttpClient`/`ObjectMapper` style.
- **Tests** (9 new, `mockwebserver3`): `PairingClientTest` — 200 saves + returns the credential (and trims the code), 401 → `PairingException` with no save, 429 → the rate-limit message; `WindowsPrinterEnumeratorTest` — 3-printer fixture parse with inkjet guesses, bare-object parse, empty/garbage → `[]`, `looksLikeInkjet` truth table; `DiscoveredPrintersClientTest` — 204 sends the printers + bearer token (asserted via `takeRequest()`), 500 does not throw. Fixture `get-printer-sample.json` is a realistic compact 3-printer array (`EPSON L3210 Series`, `Microsoft Print to PDF`, `POS-80` / `Generic / Text Only`).

## 5. Why It Changed?
Spec §2.2 makes the pairing code the sole onboarding credential — redeemed once for
the API key that then lives (DPAPI-encrypted) in the T2 credential store — so the
end user never edits a config file. Spec §2.3 wants the agent to report its local
Windows queues so the admin picks a real queue name from a dropdown instead of
typing it, and so an inkjet (no ESC/POS) is pre-set to `DRIVER` render mode.
`WindowsPrinterEnumerator` is deliberately a standalone class with a pure static
`parse`/`looksLikeInkjet` core so the future Hub-local printer-detection plan can
reuse it in-process, and so the heuristic is testable without a Windows host.
Discovery reporting swallows errors because a discovery outage must not stop the
agent from serving print jobs.

## 6. Verification
`mvn -f printing-agent/pom.xml test` → **BUILD SUCCESS**, `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` (28 prior + 9 new).
