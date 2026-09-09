# Report 418 — EMB-PRINT-AGENT T2: observable `StatusHub` + DPAPI credential store + config precedence

## 1. Identification
- **Report number:** 418
- **Current Task ID:** EMB-PRINT-AGENT T2 (agent)
- **Predecessor Task:** EMB-PRINT-AGENT T1 (backend — report 417)

## 2. Objective
Give the print agent an OS-encrypted credential store so it never re-pairs and the API key
is never plaintext on disk (closes F-24), plus a Swing-free observable `StatusHub` for the
dashboard/tray (T4) to consume, and a config-resolution order that prefers the stored
credential over `agent.properties` and returns empty (instead of throwing) when nothing is set.

## 3. Modified Files
- `printing-agent/pom.xml` — add `net.java.dev.jna:jna-platform:5.14.0`
- `printing-agent/src/main/java/com/vanter/emberagent/AgentConfig.java` — rewritten
- `printing-agent/src/main/java/com/vanter/emberagent/AgentPaths.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/AgentCredential.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/credential/CredentialStore.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/credential/DpapiCredentialStore.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/credential/PlaintextCredentialStore.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/credential/CredentialStores.java` — new
- `printing-agent/src/main/java/com/vanter/emberagent/status/StatusHub.java` — new
- `printing-agent/src/test/java/com/vanter/emberagent/AgentConfigTest.java` — new
- `printing-agent/src/test/java/com/vanter/emberagent/credential/PlaintextCredentialStoreTest.java` — new
- `printing-agent/src/test/java/com/vanter/emberagent/credential/DpapiCredentialStoreTest.java` — new
- `printing-agent/src/test/java/com/vanter/emberagent/status/StatusHubTest.java` — new

## 4. What Changed?
- **`jna-platform:5.14.0`** added to `printing-agent/pom.xml` (pulls `jna` transitively; ships
  `com.sun.jna.platform.win32.Crypt32Util` / `WinCrypt`). Verified the `cryptProtectData(byte[],
  byte[], int, String, CRYPTPROTECT_PROMPTSTRUCT)` 5-arg and `cryptUnprotectData(byte[], int)`
  2-arg overloads exist in 5.14.0 before coding against them.
- **`AgentPaths`** — resolves `%ProgramData%\EmberAgent` on Windows (`System.getenv("ProgramData")`),
  `~/.ember-agent` elsewhere; `credentialFile()` (`credential.bin`), `plaintextCredentialFile()`
  (`credential.json`), `stateFile()`, `logsDir()`; creates directories on first access.
- **`AgentCredential(String apiKey, String backendBaseUrl)`** — the persisted record, JSON via Jackson.
- **`CredentialStore`** interface: `load()` / `save()` / `clear()` / `isEncrypted()`.
- **`DpapiCredentialStore`** — Windows-only. Serializes `AgentCredential` to JSON, wraps it with
  DPAPI at machine scope (`CRYPTPROTECT_LOCAL_MACHINE`) on `save`, unwraps on `load`. A present
  but undecryptable `credential.bin` (PC changed) logs a WARN and returns empty rather than
  crashing. Uses `java.lang.System.Logger` (no slf4j on the agent classpath). Package-private
  `DpapiCredentialStore(Path)` constructor so tests stay hermetic under `@TempDir`.
- **`PlaintextCredentialStore`** — non-Windows fallback; clear JSON at `credential.json` with a
  WARN on every save; `isEncrypted()` → `false`. Same test-only `Path` constructor.
- **`CredentialStores.forThisMachine()`** — picks DPAPI on `os.name` containing `win`, else plaintext.
- **`StatusHub`** — `enum Phase {UNPAIRED, CONNECTING, CONNECTED, RETRYING}`; records `JobRecord`
  and `Snapshot`; `setPhase` / `setConnected` / `recordJob` mutate under a lock and publish a
  fresh immutable snapshot to `CopyOnWriteArrayList` listeners; `addListener` replays the current
  snapshot immediately; keeps the newest 20 `JobRecord`s. No Swing import.
- **`AgentConfig`** — new `resolve(CredentialStore, Path): Optional<AgentConfig>` tries the store
  first, then `agent.properties` (`backend.base-url` / `agent.api-key`), then `Optional.empty()`.
  The old `load(Path)` is kept as a thin shim delegating to the same properties parser so `Main`
  still compiles; T4 rewires `Main` and drops the shim.

## 5. Why It Changed?
- **F-24** (print-agent key plaintext on disk): DPAPI machine-scope means any process on the
  installed PC — and no other machine — can read the key back, so the agent persists it once at
  pairing and never re-pairs, without the key ever sitting readable on disk on Windows.
- **`StatusHub` is Swing-free and observable** so the headless core stays untouched (spec: no
  logic change to core classes) while T4's dashboard/tray just subscribe.
- **`resolve` returns empty instead of throwing** because after this milestone "not configured"
  is a normal first-run state — the dashboard opens the pairing dialog rather than the process dying.
- **`load(Path)` shim retained** to keep `printing-agent` compiling this task; `Main` is
  explicitly out of scope until T4.

## Verification
`mvn -f printing-agent/pom.xml test` → **BUILD SUCCESS, Tests run: 28, Failures: 0, Errors: 0,
Skipped: 0** (was 13; +15: `AgentConfigTest` 4, `PlaintextCredentialStoreTest` 3,
`DpapiCredentialStoreTest` 3, `StatusHubTest` 5). The DPAPI round-trip test ran for real on this
Windows machine (asserts the raw key is absent from the ciphertext on disk); it auto-skips via
`@EnabledOnOs(OS.WINDOWS)` elsewhere.
