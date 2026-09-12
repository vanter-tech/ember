# Report 444 — PRINT-AGENT-V2 bug fix: `LocalControlServer` missing CORS headers

**Predecessor Task:** report 443 — Task 6a (`VERIFY.md` update for the Tauri shell)

## Objective
Fix a bug the user hit while manually testing the freshly installed Tauri-shell agent
(`EmberAgentSetup-0.1.1.exe`): the window got stuck on a loading state, the pairing
(API key / code) button never appeared, and the window looked unresponsive to resizing.

## Root Cause (found via `superpowers:systematic-debugging`, reproduced before fixing)
`LocalControlServer` (the loopback HTTP bridge added in Task 1) never sent
`Access-Control-Allow-Origin`, and had no handling for CORS preflight `OPTIONS` requests on its
JSON `POST` endpoints. The Tauri window's origin is never `http://127.0.0.1:<port>`, so every
`fetch()` the React UI makes (`ui/src/lib/api.ts`) is cross-origin. Without a matching CORS
header, the browser silently discards the response — `fetch()` rejects with `TypeError: Failed
to fetch`. `Dashboard.tsx`'s `refresh()` swallows that error every 1.5s poll
(`catch { /* transient poll failure */ }`), so `status` stays `null` forever:
- `StatusSection` shows "Cargando…" permanently (the "part stuck loading" the user saw).
- `needsPairing` (`status?.phase === 'UNPAIRED'`) never becomes true, so `PairingSection` (the
  API-key/code button) never renders.
- The "window doesn't reflow on resize" complaint has no matching CSS bug anywhere in the
  codebase (no `fixed`/`absolute`/hardcoded widths) — it's a byproduct of the same root cause:
  with the dashboard stuck on a near-empty loading screen there's nothing to reflow.

**Reproduced live** against the actual installed agent (PID 2080, port 54381, discovered via
`Get-NetTCPConnection`): a page fetching `http://127.0.0.1:54381/api/status` from a different
origin failed with `Failed to fetch` in a Chromium tab, while the same request via
`Invoke-WebRequest` (not CORS-enforced) succeeded — confirming the browser's CORS policy, not
the server or the sidecar, was the blocker.

## Modified Files
- `printing-agent/src/main/java/com/vanter/emberagent/control/LocalControlServer.java`
- `printing-agent/src/test/java/com/vanter/emberagent/control/LocalControlServerTest.java`

## What Changed?
- New `CORS_FILTER` (a `com.sun.net.httpserver.Filter`) attached to every `/api/*` context:
  adds `Access-Control-Allow-Origin: *` to every response, and short-circuits `OPTIONS`
  preflight requests with `204` + `Access-Control-Allow-Methods: GET, POST, OPTIONS` +
  `Access-Control-Allow-Headers: Content-Type` (needed because `pairWithCode`/`pairWithApiKey`/
  `testPrint` send `Content-Type: application/json`, which isn't CORS-safelisted and triggers a
  preflight).
- TDD: 2 new tests — `status_includesCorsHeaderSoTheTauriWebviewCanReadIt` and
  `pair_optionsPreflight_returns204WithCorsHeaders` — written first (confirmed red: `expected
  <*> but was <null>` / `expected <204> but was <405>`), then made to pass by the fix above.
- `mvn -f printing-agent/pom.xml test`: **52/52** (was 50; +2 new).
- Rebuilt the app-image (`build-installer.ps1 -Stage appimage`, i.e. `mvn package` + `jpackage`)
  with the fix, launched it standalone (bypassing the Tauri shell) and re-verified live: the
  same cross-origin fetch that failed before the fix now returns `200
  {"phase":"UNPAIRED",...}` from a real Chromium tab against the rebuilt sidecar (port 55463).
- **Could not produce a fresh installer `.exe` in this session.** `build-installer.ps1 -Stage
  installer` (`cargo tauri build`) failed 3 times in a row with `Acceso denegado. (os error 5)`
  while embedding the `app-image` resources into the Tauri bundle — right after emitting
  `cargo:rerun-if-changed=..\dist\app-image\Ember Agent\Ember Agent.exe`. Ruled out: a locking
  process (killed the two test sidecars I'd started; no process had an open handle — a direct
  exclusive `FileStream` open on that exact exe succeeded), a read-only attribute (cleared
  recursively, no change), and a corrupted incremental build cache (deleted both
  `target\release\build\ember-agent-shell-*` dirs and rebuilt clean — same failure, same file).
  Per `systematic-debugging`'s 3-strikes rule this is flagged as an environment blocker rather
  than guessed at further — most likely antivirus real-time scanning holding a transient lock on
  the freshly-jpackaged `.exe` at the exact moment Tauri's build script tries to copy it. **The
  Java-side fix and its tests are unaffected by this** — it's specific to the NSIS/Tauri
  packaging step, unrelated to `LocalControlServer`. The still-installed `EmberAgentSetup-0.1.1.exe`
  on the user's machine has the OLD (buggy) code; a fresh installer with the fix still needs to
  be built (retry `printing-agent/build-installer.ps1 -Stage installer`, possibly with real-time
  AV protection paused or a Defender exclusion on `printing-agent/dist`) and reinstalled before
  the fix is live for the user.

## Why It Changed?
Loopback-only HTTP was chosen deliberately (spec §2.3) to let the Tauri shell talk to the Java
sidecar without embedding an HTTP client in Rust; nothing in that spec anticipated that a
browser enforces CORS between the WebView2 origin and `127.0.0.1` regardless of both endpoints
being on the same machine. The `no auth, because the channel never leaves this machine` design
note still holds — `Access-Control-Allow-Origin: *` doesn't loosen that guarantee, since the
server still only *binds* to `127.0.0.1` and is never reachable from outside this machine; it
only lets the local WebView2 origin read a response that was already local-only.

## Also observed (not fixed here — flagged to the user)
A leftover installation from the earlier Inno-Setup-based build (pre-Task-5) is still present at
the top level of `C:\Program Files\Ember Agent\` (`Ember Agent.exe`, `app\`, `runtime\`, dated
2026-09-09) alongside the new NSIS install's `ember-agent-shell.exe` + `app-image\` (today's
install). A stray instance of that old exe (`Ember Agent.exe --tray`, PID 12488/12516 at the
time of investigation) was running concurrently with the new Tauri shell. Installing the new
NSIS package over the old Inno Setup install does not remove the old product's files/uninstall
entry, since they're two separate installer technologies. Recommended: uninstall the old version
via *Agregar o quitar programas* (or manually delete the stray top-level `Ember Agent.exe`/`app`/
`runtime` after confirming no autostart entry references them) before reinstalling the new
`.exe`, to avoid two agent processes fighting over the same `credential.bin`/backend connection.
