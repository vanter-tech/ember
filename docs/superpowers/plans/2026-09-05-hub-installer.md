# Ember Hub Windows Installer (HUB-03) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce a double-click `EmberHubSetup-<version>.exe` that installs Ember Hub (embedded JRE + Spring app + portable PostgreSQL + portable MinIO) on a Windows PC with no Java installed, and makes it start automatically when the operator logs in.

**Architecture:** The Hub runtime already exists — it's `backend/`'s jar run with `SPRING_PROFILES_ACTIVE=hub`, which boots `HubDashboard` (Swing) instead of a headless server. This plan adds only packaging: `jpackage --type app-image` wraps a `jlink`-trimmed JRE + the fat jar into a launcher folder; a PowerShell orchestrator (`ember-hub/build-installer.ps1`) assembles that folder with the portable Postgres/MinIO binaries and a `.cmd` shim; an Inno Setup script (`ember-hub/installer/EmberHub.iss`) turns the folder into a Windows installer that registers a firewall rule, writes runtime config to `%ProgramData%\EmberHub\hub.env`, and drops a startup shortcut. One small runtime change: a "Seleccionar license.key…" button on the dashboard so the customer can point the Hub at the license file they receive after purchase.

**Tech Stack:** Java 17, Spring Boot 3.5.14, `jlink`/`jpackage` (JDK 17), Inno Setup 6 (`iscc`), PowerShell 5.1, portable PostgreSQL 16.6-1 (EDB binaries-only, Windows x64), portable MinIO (`minio.exe`, `RELEASE.2025-04-22T22-12-26Z`).

**Spec:** `docs/superpowers/specs/2026-09-05-hub-installer-design.md`

## Global Constraints

- **Platform:** Windows x64 only. Build and all verification run on real Windows (no CI covers the final `.exe`). The `build-installer.ps1` orchestrator itself may run in a Windows CI job that publishes the `.exe` as an artifact.
- **Java:** 17 (matches `backend/pom.xml`). Use the JDK's own `jlink`/`jpackage`.
- **PostgreSQL:** exactly `16.6-1`, EDB "binaries only", Windows x64 — `https://get.enterprisedb.com/postgresql/postgresql-16.6-1-windows-x64-binaries.zip`. Confirmed in report 236.
- **MinIO:** `https://dl.min.io/server/minio/release/windows-amd64/archive/minio.RELEASE.2025-04-22T22-12-26Z` (dated release, not the rolling `minio.exe`).
- **Install layout (exact):**
  - App (read-only, replaced on every update): `%ProgramFiles%\Ember Hub\`
  - Data (survives updates/uninstall-keep): `%ProgramData%\EmberHub\` with `data\postgres\`, `data\minio\`, `logs\`, `backups\`, `license.key`, `hub-state.json`, `hub.env`
- **Installer tooling:** `jpackage --type app-image` + Inno Setup. **No WiX.**
- **Auto-start:** a `.lnk` to the `.cmd` shim in `shell:common startup` (`%ProgramData%\Microsoft\Windows\Start Menu\Programs\StartUp`). **Not** a Windows service.
- **Firewall rule:** inbound, TCP, port = `EMBER_HUB_SERVER_PORT`, profiles `private,domain` only — **never `public`**.
- **Baked config:** `ember-hub/build.env` supplies `EMBER_HUB_ACTIVATION_URL`, `EMBER_HUB_HEARTBEAT_URL`, `EMBER_HUB_SERVER_PORT`. `build.env` is gitignored; `build.env.example` is tracked.
- **License:** never bundled in the installer. Delivered to the customer separately after purchase.
- **No new backend runtime dependencies.** The dashboard is plain Swing (JDK stdlib).
- **Operator-facing strings:** Spanish.
- **Commits:** Conventional Commits, lowercase, no `Co-authored-by`/`Signed-off-by`/AI signatures. Scoped `git add <paths>` only — never `git add -A`/`.` (this repo has tracked secrets historically).
- **Branch:** work on `spec/hub-installer` (already created off `main`, holds the spec commit).

---

### Task 1: Repo hygiene — gitignore + `build.env.example`

**Files:**
- Create: `ember-hub/build.env.example`
- Delete: `ember-hub/build.env` (currently an untracked file holding template text — becomes gitignored)
- Modify: `.gitignore`

**Interfaces:**
- Produces: `ember-hub/build.env.example` with keys `EMBER_HUB_ACTIVATION_URL`, `EMBER_HUB_HEARTBEAT_URL`, `EMBER_HUB_SERVER_PORT`. Later tasks read the real `ember-hub/build.env` (operator/CI creates it by copying the example).

- [ ] **Step 1: Create `ember-hub/build.env.example`**

```sh
# ember-hub/build.env.example
# Copy to ember-hub/build.env (gitignored) and fill with production values.
# These are baked into every installer, identical for all customers.
EMBER_HUB_ACTIVATION_URL=https://api.vanter.com/hub-activations
EMBER_HUB_HEARTBEAT_URL=https://api.vanter.com/hub-heartbeat
EMBER_HUB_SERVER_PORT=8080
```

- [ ] **Step 2: Add ignore entries to `.gitignore`**

Append under the existing "Hub distributable" comment block:

```gitignore
# Hub installer build — real baked config, downloaded vendor binaries, and build output
ember-hub/build.env
ember-hub/.vendor-cache/
ember-hub/dist/
```

- [ ] **Step 3: Replace the working-copy `build.env` with a real one**

```powershell
Copy-Item ember-hub\build.env.example ember-hub\build.env -Force
git rm --cached ember-hub/build.env 2>$null   # no-op if it was never tracked
```

- [ ] **Step 4: Verify**

Run: `git status --porcelain ember-hub/`
Expected: `A  ember-hub/build.env.example` and `M  .gitignore` staged; `ember-hub/build.env` NOT listed (ignored).

- [ ] **Step 5: Commit**

```powershell
git add ember-hub/build.env.example .gitignore
git commit -m "chore(hub): track build.env.example, gitignore installer build artifacts"
```

---

### Task 2: `LicenseFileInstaller` helper (TDD)

The dashboard button (Task 3) needs to copy a customer-chosen file to `properties.licenseFile()`. Extract that into a plain, unit-tested class so the Swing glue stays trivial.

**Files:**
- Create: `backend/src/main/java/com/vanter/ember/hub/dashboard/LicenseFileInstaller.java`
- Test: `backend/src/test/java/com/vanter/ember/hub/dashboard/LicenseFileInstallerTest.java`

**Interfaces:**
- Produces: `LicenseFileInstaller.install(Path source, Path destination)` — copies `source` to `destination`, creating parent dirs, replacing any existing file. Throws `java.nio.file.NoSuchFileException` if `source` doesn't exist; throws `IllegalArgumentException` if `source` and `destination` resolve to the same path. Returns `void`.

- [ ] **Step 1: Write the failing test**

```java
package com.vanter.ember.hub.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LicenseFileInstallerTest {

    @Test
    void install_copiesSourceToDestination_creatingParentDirs(@TempDir Path tmp) throws Exception {
        Path source = Files.writeString(tmp.resolve("chosen.key"), "LICENSE-BODY");
        Path destination = tmp.resolve("data").resolve("EmberHub").resolve("license.key");

        LicenseFileInstaller.install(source, destination);

        assertThat(destination).exists();
        assertThat(Files.readString(destination)).isEqualTo("LICENSE-BODY");
    }

    @Test
    void install_overwritesAnExistingDestination(@TempDir Path tmp) throws Exception {
        Path source = Files.writeString(tmp.resolve("new.key"), "NEW");
        Path destination = Files.writeString(tmp.resolve("license.key"), "OLD");

        LicenseFileInstaller.install(source, destination);

        assertThat(Files.readString(destination)).isEqualTo("NEW");
    }

    @Test
    void install_throwsNoSuchFileException_whenSourceMissing(@TempDir Path tmp) {
        Path source = tmp.resolve("does-not-exist.key");
        Path destination = tmp.resolve("license.key");

        assertThatExceptionOfType(NoSuchFileException.class)
                .isThrownBy(() -> LicenseFileInstaller.install(source, destination));
    }

    @Test
    void install_throwsIllegalArgument_whenSourceEqualsDestination(@TempDir Path tmp) throws Exception {
        Path file = Files.writeString(tmp.resolve("license.key"), "BODY");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> LicenseFileInstaller.install(file, file));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw test -Dtest=LicenseFileInstallerTest`
Expected: FAIL — `LicenseFileInstaller` does not exist (compilation error).

- [ ] **Step 3: Write the implementation**

```java
package com.vanter.ember.hub.dashboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Copies a customer-chosen {@code license.key} into the Hub's configured license path
 * (see {@link com.vanter.ember.hub.config.HubProperties#licenseFile()}). Plain file I/O,
 * pulled out of {@link HubDashboard} so it can be unit-tested without Swing.
 */
public final class LicenseFileInstaller {

    private LicenseFileInstaller() {}

    public static void install(Path source, Path destination) throws IOException {
        if (!Files.exists(source)) {
            throw new NoSuchFileException(source.toString());
        }
        Path from = source.toAbsolutePath().normalize();
        Path to = destination.toAbsolutePath().normalize();
        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "El archivo seleccionado ya es el license.key en uso: " + to);
        }
        Path parent = to.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw test -Dtest=LicenseFileInstallerTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Run the full backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, `Tests run: 1054` (1050 from report 380 + 4 new). BUILD SUCCESS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/vanter/ember/hub/dashboard/LicenseFileInstaller.java backend/src/test/java/com/vanter/ember/hub/dashboard/LicenseFileInstallerTest.java
git commit -m "feat(hub): add LicenseFileInstaller helper for the dashboard license picker"
```

---

### Task 3: `HubDashboard` — license picker button + `--autostart`

**Files:**
- Modify: `backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java`

**Interfaces:**
- Consumes: `LicenseFileInstaller.install(Path, Path)` (Task 2); `HubProperties.licenseFile()` (existing).
- Produces: `HubDashboard.launch(String[] args)` unchanged signature. New behaviour: if `args` contains `--autostart`, the dashboard invokes its start action once the window is shown. A new button "Seleccionar license.key…" opens a `JFileChooser`, installs the chosen file, and retries the start.

- [ ] **Step 1: Add the button field and widen the button grid**

In the field declarations block (near `exitButton`), add:

```java
    private final JButton selectLicenseButton = new JButton("Seleccionar license.key…");
```

In the constructor, change the button panel from a 4-column grid to 5 and add the button:

```java
        JPanel buttonPanel = new JPanel(new GridLayout(1, 5, 8, 0));
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);
        buttonPanel.add(selectLicenseButton);
        buttonPanel.add(openButton);
        buttonPanel.add(exitButton);
```

Change `setSize(360, 215);` to `setSize(520, 215);` so five buttons fit.

Wire the listener, next to the other `addActionListener` calls:

```java
        selectLicenseButton.addActionListener(e -> onSelectLicense());
```

- [ ] **Step 2: Add the `onSelectLicense()` method**

Place it after `onExit()`. Add imports `javax.swing.JFileChooser`, `javax.swing.filechooser.FileNameExtensionFilter`, `java.nio.file.Path`, `java.io.File`.

```java
    private void onSelectLicense() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona el archivo license.key");
        chooser.setFileFilter(new FileNameExtensionFilter("Licencia Ember (license.key, *.key)", "key"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        try {
            LicenseFileInstaller.install(chosen.toPath(), properties.licenseFile());
        } catch (Exception ex) {
            log.error("No se pudo instalar el license.key", ex);
            JOptionPane.showMessageDialog(
                    this,
                    "No se pudo copiar la licencia:\n" + ex.getMessage(),
                    "Licencia",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        refreshLicenseStatus();
        JOptionPane.showMessageDialog(
                this,
                "Licencia instalada. Se intentará iniciar Ember Hub.",
                "Licencia",
                JOptionPane.INFORMATION_MESSAGE);
        if (startButton.isEnabled()) {
            onStart();
        }
    }
```

- [ ] **Step 3: Add `--autostart` handling**

Change `launch` so it triggers the start action after the frame is visible when `--autostart` is present:

```java
    public static void launch(String[] args) {
        boolean autostart = Arrays.asList(args).contains("--autostart");
        SwingUtilities.invokeLater(() -> {
            HubDashboard dashboard = new HubDashboard(args);
            dashboard.setVisible(true);
            if (autostart) {
                dashboard.onStart();
            }
        });
    }
```

Add `import java.util.Arrays;` (already imported in `EmberApplication` but this is a different file — add it here).

- [ ] **Step 4: Compile and run the backend suite**

Run: `cd backend && ./mvnw test`
Expected: PASS, `Tests run: 1054`. BUILD SUCCESS. (No new tests — Swing UI is not unit-tested here; behaviour is covered by the Task 10 manual pass.)

- [ ] **Step 5: Manual smoke (optional here, required in Task 10)**

Run the hub profile locally and confirm the new button appears:

```powershell
$env:SPRING_PROFILES_ACTIVE = "hub"
$env:EMBER_HUB_POSTGRES_PORT = "5433"
cd backend; ./mvnw spring-boot:run
```

Expected: the dashboard window shows five buttons including "Seleccionar license.key…". Close it.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/vanter/ember/hub/dashboard/HubDashboard.java
git commit -m "feat(hub): dashboard license.key picker button and --autostart flag"
```

---

### Task 4: `jlink-modules.txt` + `build-installer.ps1` (runtime step)

**Files:**
- Create: `ember-hub/jlink-modules.txt`
- Create: `ember-hub/build-installer.ps1` (first slice — the `jlink` runtime only)

**Interfaces:**
- Produces: `ember-hub/dist/runtime/` — a self-contained JRE image. Consumed by Task 7's `jpackage --runtime-image`. `build-installer.ps1` gains a `-Stage runtime` switch; Task 7 adds `appimage`, Task 8 adds `installer`, and a default that runs all.

- [ ] **Step 1: Create `ember-hub/jlink-modules.txt`**

A conservative superset — `java.se` covers Swing/JDBC/JNDI; the `jdk.*` entries are the ones Spring Boot + the outbound HTTPS heartbeat + OSHI need and that `java.se` does not pull. Trimming further is a size optimisation, not v1.

```
java.se
jdk.crypto.ec
jdk.crypto.cryptoki
jdk.unsupported
jdk.management
jdk.management.agent
jdk.zipfs
jdk.localedata
jdk.jdwp.agent
jdk.charsets
```

- [ ] **Step 2: Create `ember-hub/build-installer.ps1` with the runtime stage**

```powershell
<#
Builds the Ember Hub Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> ember-hub/dist/runtime         (jlink JRE image)
  appimage  -> ember-hub/dist/app-image        (jpackage + assembled binaries)   [Task 7]
  installer -> ember-hub/dist/EmberHubSetup-*.exe (Inno Setup)                    [Task 8]
Requires: JDK 17 on PATH (java, jlink, jpackage), pnpm, mvn, Inno Setup (iscc).
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$hubDir     = $PSScriptRoot
$distDir    = Join-Path $hubDir "dist"
$runtimeDir = Join-Path $distDir "runtime"

function Build-Runtime {
    Write-Host "== jlink runtime ==" -ForegroundColor Cyan
    $modules = (Get-Content (Join-Path $hubDir "jlink-modules.txt") |
                Where-Object { $_ -and -not $_.StartsWith("#") }) -join ","
    if (Test-Path $runtimeDir) { Remove-Item -Recurse -Force $runtimeDir }
    & jlink `
        --add-modules $modules `
        --strip-debug --no-header-files --no-man-pages `
        --compress=2 `
        --include-locales=en,es `
        --output $runtimeDir
    if ($LASTEXITCODE -ne 0) { throw "jlink failed ($LASTEXITCODE)" }
    & (Join-Path $runtimeDir "bin\java.exe") --version
    if ($LASTEXITCODE -ne 0) { throw "runtime java.exe is not runnable" }
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
# appimage / installer stages added in later tasks
Write-Host "Done ($Stage)." -ForegroundColor Green
```

- [ ] **Step 3: Run the runtime stage**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/build-installer.ps1 -Stage runtime`
Expected: `ember-hub/dist/runtime/bin/java.exe --version` prints `openjdk 17...`; no errors.

- [ ] **Step 4: Verify the module set resolved**

Run: `ember-hub/dist/runtime/bin/java.exe --list-modules`
Expected: output includes `java.sql`, `java.desktop`, `java.naming`, `jdk.crypto.ec`, `jdk.unsupported`.

- [ ] **Step 5: Commit**

```powershell
git add ember-hub/jlink-modules.txt ember-hub/build-installer.ps1
git commit -m "feat(hub): jlink runtime image stage for the installer build"
```

---

### Task 5: `fetch-vendor-binaries.ps1` — portable Postgres + MinIO

**Files:**
- Create: `ember-hub/fetch-vendor-binaries.ps1`

**Interfaces:**
- Produces: `ember-hub/.vendor-cache/staging/pgsql/` (contains `bin\initdb.exe`, `bin\pg_ctl.exe`, `bin\postgres.exe`, `lib\`, `share\`) and `ember-hub/.vendor-cache/staging/minio/minio.exe`. Consumed by Task 7's assembly step. Downloads are cached (and hash-verified) under `ember-hub/.vendor-cache/`.

- [ ] **Step 1: Create `ember-hub/fetch-vendor-binaries.ps1`**

```powershell
<#
Downloads and verifies the portable binaries bundled into the Hub installer:
  - PostgreSQL 16.6-1  (EDB "binaries only", Windows x64)
  - MinIO server       (dated Windows release)
Caches downloads under ember-hub/.vendor-cache/ and stages an extracted copy
under ember-hub/.vendor-cache/staging/ for build-installer.ps1 to pick up.
On first run, if a *_SHA256 constant below is "REPLACE_ME", the script prints
the computed hash and stops — paste it in and commit, then re-run.
#>
$ErrorActionPreference = "Stop"
$hubDir   = $PSScriptRoot
$cacheDir = Join-Path $hubDir ".vendor-cache"
$stageDir = Join-Path $cacheDir "staging"

$PG_URL      = "https://get.enterprisedb.com/postgresql/postgresql-16.6-1-windows-x64-binaries.zip"
$PG_SHA256   = "REPLACE_ME"
$MINIO_URL   = "https://dl.min.io/server/minio/release/windows-amd64/archive/minio.RELEASE.2025-04-22T22-12-26Z"
$MINIO_SHA256 = "REPLACE_ME"

function Get-Verified([string]$url, [string]$expected, [string]$outFile) {
    if (-not (Test-Path $outFile)) {
        Write-Host "downloading $url"
        Invoke-WebRequest -Uri $url -OutFile $outFile
    }
    $actual = (Get-FileHash $outFile -Algorithm SHA256).Hash.ToLower()
    if ($expected -eq "REPLACE_ME") {
        throw "SHA256 for $(Split-Path $outFile -Leaf) is $actual - paste it into fetch-vendor-binaries.ps1 and re-run."
    }
    if ($actual -ne $expected.ToLower()) {
        throw "SHA256 mismatch for $outFile`n  expected $expected`n  actual   $actual"
    }
}

New-Item -ItemType Directory -Force -Path $cacheDir | Out-Null
if (Test-Path $stageDir) { Remove-Item -Recurse -Force $stageDir }
New-Item -ItemType Directory -Force -Path $stageDir | Out-Null

# --- PostgreSQL ---
$pgZip = Join-Path $cacheDir "postgresql-16.6-1-windows-x64-binaries.zip"
Get-Verified $PG_URL $PG_SHA256 $pgZip
$pgTmp = Join-Path $cacheDir "pg-extract"
if (Test-Path $pgTmp) { Remove-Item -Recurse -Force $pgTmp }
Expand-Archive -Path $pgZip -DestinationPath $pgTmp
# the zip contains a top-level "pgsql/" folder
$pgStaged = Join-Path $stageDir "pgsql"
Copy-Item -Recurse (Join-Path $pgTmp "pgsql") $pgStaged
Remove-Item -Recurse -Force $pgTmp
# The EDB "binaries" package ships pgAdmin 4 (~630 MB), StackBuilder, docs, headers and
# debug symbols - the Hub only needs bin/ + lib/ + share/. Drop the rest.
foreach ($drop in @("pgAdmin 4", "StackBuilder", "doc", "include", "symbols", "pgAdmin4.exe")) {
    $p = Join-Path $pgStaged $drop
    if (Test-Path $p) { Remove-Item -Recurse -Force $p }
}
& (Join-Path $stageDir "pgsql\bin\initdb.exe") --version
if ($LASTEXITCODE -ne 0) { throw "staged initdb.exe is not runnable" }

# --- MinIO ---
$minioExe = Join-Path $cacheDir "minio.exe"
Get-Verified $MINIO_URL $MINIO_SHA256 $minioExe
New-Item -ItemType Directory -Force -Path (Join-Path $stageDir "minio") | Out-Null
Copy-Item $minioExe (Join-Path $stageDir "minio\minio.exe")
& (Join-Path $stageDir "minio\minio.exe") --version
if ($LASTEXITCODE -ne 0) { throw "staged minio.exe is not runnable" }

Write-Host "Vendor binaries staged under $stageDir" -ForegroundColor Green
```

- [ ] **Step 2: First run — capture the hashes**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/fetch-vendor-binaries.ps1`
Expected: it downloads, then throws with the two computed SHA256 values.

- [ ] **Step 3: Pin the hashes**

Replace both `REPLACE_ME` constants in the script with the printed lowercase hashes.

- [ ] **Step 4: Re-run to verify**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/fetch-vendor-binaries.ps1`
Expected: `Vendor binaries staged under ...\staging`; `initdb.exe --version` prints `initdb (PostgreSQL) 16.6`; `minio.exe --version` prints the `2025-04-22` release.

- [ ] **Step 5: Commit**

```powershell
git add ember-hub/fetch-vendor-binaries.ps1
git commit -m "feat(hub): fetch + hash-pin portable postgres and minio for the installer"
```

---

### Task 6: `.cmd` shim + `hub.env.example`

**Files:**
- Create: `ember-hub/installer/Iniciar Ember Hub.cmd`
- Create: `ember-hub/installer/hub.env.example`

**Interfaces:**
- Consumes: `%ProgramData%\EmberHub\hub.env` at runtime (written by the installer, Task 8).
- Produces: the shim that every shortcut and the startup `.lnk` point at. It exports `SPRING_PROFILES_ACTIVE=hub` + all `EMBER_HUB_*` from `hub.env`, then runs `"%~dp0Ember Hub.exe" --autostart`.

- [ ] **Step 1: Create `ember-hub/installer/hub.env.example`**

```sh
# %ProgramData%\EmberHub\hub.env — written by the installer, read by "Iniciar Ember Hub.cmd".
# Absolute paths so the Hub works regardless of the working directory.
EMBER_HUB_DATA_DIR=C:\ProgramData\EmberHub\data\postgres
EMBER_HUB_MINIO_DATA_DIR=C:\ProgramData\EmberHub\data\minio
EMBER_HUB_POSTGRES_BIN_DIR=C:\Program Files\Ember Hub\pgsql\bin
EMBER_HUB_MINIO_BIN_DIR=C:\Program Files\Ember Hub\minio
EMBER_HUB_LICENSE_FILE=C:\ProgramData\EmberHub\license.key
EMBER_HUB_PUBLIC_KEY_FILE=C:\Program Files\Ember Hub\hub-public-key.der
EMBER_HUB_STATE_FILE=C:\ProgramData\EmberHub\hub-state.json
EMBER_HUB_POSTGRES_PORT=5432
EMBER_HUB_MINIO_PORT=9000
EMBER_HUB_SERVER_PORT=8080
EMBER_HUB_ACTIVATION_URL=https://api.vanter.com/hub-activations
EMBER_HUB_HEARTBEAT_URL=https://api.vanter.com/hub-heartbeat
```

- [ ] **Step 2: Create `ember-hub/installer/Iniciar Ember Hub.cmd`**

```bat
@echo off
setlocal EnableDelayedExpansion
rem Ember Hub launcher shim. Loads runtime config from %ProgramData%\EmberHub\hub.env,
rem exports it, and starts the jpackage app with the hub profile.

set "HUB_ENV=%ProgramData%\EmberHub\hub.env"
if not exist "%HUB_ENV%" (
    echo No se encontro "%HUB_ENV%". Reinstala Ember Hub.
    pause
    exit /b 1
)

set "SPRING_PROFILES_ACTIVE=hub"
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%HUB_ENV%") do (
    if not "%%~A"=="" set "%%~A=%%~B"
)

set "LOGDIR=%ProgramData%\EmberHub\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%"

rem %~dp0 is "...\Ember Hub\" (this shim ships next to the app launcher)
"%~dp0Ember Hub.exe" --autostart >> "%LOGDIR%\hub.out.log" 2>> "%LOGDIR%\hub.err.log"
```

- [ ] **Step 3: Verify the parser with a stub**

Create a throwaway `t.cmd` next to a copy of `hub.env.example` renamed `hub.env`, replacing the last line with `echo EMBER_HUB_SERVER_PORT=[%EMBER_HUB_SERVER_PORT%] PROFILE=[%SPRING_PROFILES_ACTIVE%]` instead of launching the exe. Run it.
Expected: prints `EMBER_HUB_SERVER_PORT=[8080] PROFILE=[hub]`. Delete the throwaway files.

- [ ] **Step 4: Commit**

```powershell
git add "ember-hub/installer/Iniciar Ember Hub.cmd" ember-hub/installer/hub.env.example
git commit -m "feat(hub): launcher shim + hub.env template for the installed app"
```

---

### Task 7: `build-installer.ps1` — `jpackage` app-image + assembly

**Files:**
- Modify: `ember-hub/build-installer.ps1` (add the `appimage` stage)
- Create: `ember-hub/installer/make-icon.ps1`
- Create: `ember-hub/installer/ember-hub.ico` (generated by `make-icon.ps1`, committed as a binary)

**Interfaces:**
- Consumes: `ember-hub/dist/runtime/` (Task 4), `ember-hub/.vendor-cache/staging/` (Task 5), `ember-hub/installer/Iniciar Ember Hub.cmd` (Task 6), `ember-hub/build.env`, `ember-hub/keys/hub-public-key.der` (operator-supplied production key; see Step 4).
- Produces: `ember-hub/dist/app-image/Ember Hub/` containing `Ember Hub.exe`, `Iniciar Ember Hub.cmd`, `runtime/`, `app/ember-hub.jar`, `pgsql/bin/…`, `minio/minio.exe`, `hub-public-key.der`. Consumed by Task 8's Inno script.

- [ ] **Step 1: Create `ember-hub/installer/make-icon.ps1`**

Wraps `frontend/src/assets/ember.png` (resized to 256×256) into a single-image `.ico` (PNG-in-ICO, valid on Windows Vista+), so no external image tool is required.

```powershell
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Drawing
$src = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "frontend\src\assets\ember.png"
$out = Join-Path $PSScriptRoot "ember-hub.ico"

$img = [System.Drawing.Image]::FromFile($src)
$bmp = New-Object System.Drawing.Bitmap 256, 256
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.DrawImage($img, 0, 0, 256, 256)
$g.Dispose(); $img.Dispose()

$pngStream = New-Object System.IO.MemoryStream
$bmp.Save($pngStream, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
$png = $pngStream.ToArray()

$fs = [System.IO.File]::Create($out)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]1)      # ICONDIR: reserved, type=icon, count=1
$bw.Write([Byte]0);   $bw.Write([Byte]0)                              # width/height 0 => 256
$bw.Write([Byte]0);   $bw.Write([Byte]0)                              # colors, reserved
$bw.Write([UInt16]1); $bw.Write([UInt16]32)                          # planes, bpp
$bw.Write([UInt32]$png.Length)                                        # bytes of image data
$bw.Write([UInt32]22)                                                 # offset (6 + 16)
$bw.Write($png)
$bw.Dispose(); $fs.Dispose()
Write-Host "wrote $out ($($png.Length) bytes)"
```

- [ ] **Step 2: Generate and commit the icon**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/installer/make-icon.ps1`
Expected: `ember-hub/installer/ember-hub.ico` created.

```powershell
git add ember-hub/installer/make-icon.ps1 ember-hub/installer/ember-hub.ico
git commit -m "build(hub): generate installer icon from the ember mark"
```

- [ ] **Step 3: Add the `appimage` stage to `build-installer.ps1`**

Insert this function before the stage dispatch, and add the dispatch line.

```powershell
$frontendPs = Join-Path $hubDir "build-frontend.ps1"
$fetchPs    = Join-Path $hubDir "fetch-vendor-binaries.ps1"
$stageDir   = Join-Path $hubDir ".vendor-cache\staging"
$appImageParent = Join-Path $distDir "app-image"
$appImageDir    = Join-Path $appImageParent "Ember Hub"
$installerDir   = Join-Path $hubDir "installer"

function Get-HubVersion {
    $pom = Get-Content (Join-Path $repoRoot "backend\pom.xml") -Raw
    if ($pom -notmatch "<artifactId>ember</artifactId>\s*<version>([^<]+)</version>") {
        throw "could not read <version> from backend/pom.xml"
    }
    return $Matches[1] -replace "-SNAPSHOT",""
}

function Build-AppImage {
    Write-Host "== app-image ==" -ForegroundColor Cyan
    if (-not (Test-Path $runtimeDir)) { Build-Runtime }
    if (-not (Test-Path (Join-Path $stageDir "pgsql\bin\initdb.exe"))) { & $fetchPs }

    Write-Host "-- frontend --"
    & powershell -ExecutionPolicy Bypass -File $frontendPs
    if ($LASTEXITCODE -ne 0) { throw "build-frontend.ps1 failed" }

    Write-Host "-- mvn package --"
    Push-Location (Join-Path $repoRoot "backend")
    try {
        & .\mvnw.cmd -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    } finally { Pop-Location }

    $jar = Get-ChildItem (Join-Path $repoRoot "backend\target") -Filter "ember-*.jar" |
           Where-Object { $_.Name -notmatch "sources|javadoc|original" } |
           Select-Object -First 1
    if (-not $jar) { throw "no ember-*.jar in backend/target" }

    # jpackage needs the jar alone in an input dir under a stable name
    $inputDir = Join-Path $distDir "jpackage-input"
    if (Test-Path $inputDir) { Remove-Item -Recurse -Force $inputDir }
    New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
    Copy-Item $jar.FullName (Join-Path $inputDir "ember-hub.jar")

    if (Test-Path $appImageParent) { Remove-Item -Recurse -Force $appImageParent }
    & jpackage `
        --type app-image `
        --name "Ember Hub" `
        --app-version (Get-HubVersion) `
        --vendor "Vanter" `
        --input $inputDir `
        --main-jar "ember-hub.jar" `
        --main-class "org.springframework.boot.loader.launch.JarLauncher" `
        --runtime-image $runtimeDir `
        --icon (Join-Path $installerDir "ember-hub.ico") `
        --java-options "-Dfile.encoding=UTF-8" `
        --dest $appImageParent
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed ($LASTEXITCODE)" }

    # assemble the extras next to the launcher
    Copy-Item (Join-Path $stageDir "pgsql")  (Join-Path $appImageDir "pgsql")  -Recurse
    Copy-Item (Join-Path $stageDir "minio")  (Join-Path $appImageDir "minio")  -Recurse
    Copy-Item (Join-Path $installerDir "Iniciar Ember Hub.cmd") $appImageDir
    Copy-Item (Join-Path $hubDir "keys\hub-public-key.der") $appImageDir

    if (-not (Test-Path (Join-Path $appImageDir "Ember Hub.exe"))) { throw "app-image launcher missing" }
    Write-Host "app-image at $appImageDir" -ForegroundColor Green
}
```

Update the dispatch tail:

```powershell
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
# installer stage added in Task 8
Write-Host "Done ($Stage)." -ForegroundColor Green
```

- [ ] **Step 4: Provide the public key**

Place the production RSA public key at `ember-hub/keys/hub-public-key.der` (DER, X.509 SubjectPublicKeyInfo — the format `LicenseKeyParser.loadPublicKey` expects). For a build-pipeline test before the real key exists, generate a throwaway pair:

```powershell
New-Item -ItemType Directory -Force -Path ember-hub/keys | Out-Null
# throwaway keypair for build testing only — do NOT ship
& "$env:JAVA_HOME\bin\keytool" -genkeypair -alias t -keyalg RSA -keysize 2048 -dname "CN=test" `
  -keystore ember-hub/keys/t.p12 -storetype PKCS12 -storepass changeit -keypass changeit
& "$env:JAVA_HOME\bin\keytool" -exportcert -alias t -keystore ember-hub/keys/t.p12 -storepass changeit `
  -file ember-hub/keys/hub-public-key.der   # note: this exports a cert, not a bare SPKI —
# for a real dry run reuse the DER produced during report 236's verification instead.
```

Add `ember-hub/keys/` to `.gitignore` (keys are never committed):

```powershell
Add-Content .gitignore "ember-hub/keys/"
git add .gitignore
git commit -m "chore(hub): gitignore ember-hub/keys"
```

- [ ] **Step 5: Run the app-image stage**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/build-installer.ps1 -Stage appimage`
Expected: completes; `ember-hub/dist/app-image/Ember Hub/Ember Hub.exe`, `.../pgsql/bin/initdb.exe`, `.../minio/minio.exe`, `.../Iniciar Ember Hub.cmd`, `.../hub-public-key.der` all present.

- [ ] **Step 6: Smoke the app-image**

Copy `ember-hub/installer/hub.env.example` to `%ProgramData%\EmberHub\hub.env`, edit `EMBER_HUB_POSTGRES_BIN_DIR`/`EMBER_HUB_MINIO_BIN_DIR` to point at the `dist/app-image/Ember Hub/...` paths, put a valid `license.key` at `%ProgramData%\EmberHub\license.key`, then run `"ember-hub\dist\app-image\Ember Hub\Iniciar Ember Hub.cmd"`.
Expected: the dashboard opens and (via `--autostart`) starts Postgres/MinIO/server; `http://localhost:8080/` serves the SPA. Stop it from the dashboard.

- [ ] **Step 7: Commit**

```powershell
git add ember-hub/build-installer.ps1
git commit -m "feat(hub): jpackage app-image stage + binary assembly"
```

---

### Task 8: `EmberHub.iss` + `build-installer.ps1` installer stage

**Files:**
- Create: `ember-hub/installer/EmberHub.iss`
- Modify: `ember-hub/build-installer.ps1` (add the `installer` stage)

**Interfaces:**
- Consumes: `ember-hub/dist/app-image/Ember Hub/` (Task 7), `ember-hub/build.env`.
- Produces: `ember-hub/dist/EmberHubSetup-<version>.exe`.

- [ ] **Step 1: Create `ember-hub/installer/EmberHub.iss`**

`build-installer.ps1` passes `AppVersion` and `ServerPort` via `/D` so the script has no duplicated constants.

```iss
; Ember Hub installer. Compiled by build-installer.ps1 via:
;   iscc /DAppVersion=<v> /DServerPort=<p> EmberHub.iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef ServerPort
  #define ServerPort "8080"
#endif

[Setup]
AppName=Ember Hub
AppVersion={#AppVersion}
AppPublisher=Vanter
DefaultDirName={autopf}\Ember Hub
DefaultGroupName=Ember Hub
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\Ember Hub.exe
OutputDir=..\dist
OutputBaseFilename=EmberHubSetup-{#AppVersion}
SetupIconFile=ember-hub.ico
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
WizardStyle=modern
CloseApplications=yes
CloseApplicationsFilter=Ember Hub.exe,*.cmd

[Files]
Source: "..\dist\app-image\Ember Hub\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Dirs]
Name: "{commonappdata}\EmberHub"
Name: "{commonappdata}\EmberHub\data\postgres"
Name: "{commonappdata}\EmberHub\data\minio"
Name: "{commonappdata}\EmberHub\logs"
Name: "{commonappdata}\EmberHub\backups"

[Icons]
Name: "{group}\Ember Hub";        Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"
Name: "{commondesktop}\Ember Hub"; Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"
Name: "{commonstartup}\Ember Hub"; Filename: "{app}\Iniciar Ember Hub.cmd"; IconFilename: "{app}\Ember Hub.exe"; WorkingDir: "{app}"

[Run]
; Inbound firewall rule for LAN terminals — private + domain only, never public.
Filename: "{sys}\netsh.exe"; \
  Parameters: "advfirewall firewall add rule name=""Ember Hub {#ServerPort}"" dir=in action=allow protocol=TCP localport={#ServerPort} profile=private,domain"; \
  Flags: runhidden

[UninstallRun]
Filename: "{sys}\netsh.exe"; \
  Parameters: "advfirewall firewall delete rule name=""Ember Hub {#ServerPort}"""; \
  Flags: runhidden; RunOnceId: "DelFwRule"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  EnvPath, Lines: string;
begin
  if CurStep = ssPostInstall then
  begin
    EnvPath := ExpandConstant('{commonappdata}\EmberHub\hub.env');
    if not FileExists(EnvPath) then
    begin
      Lines :=
        '# Generado por el instalador de Ember Hub. No editar salvo el puerto.' + #13#10 +
        'EMBER_HUB_DATA_DIR=' + ExpandConstant('{commonappdata}\EmberHub\data\postgres') + #13#10 +
        'EMBER_HUB_MINIO_DATA_DIR=' + ExpandConstant('{commonappdata}\EmberHub\data\minio') + #13#10 +
        'EMBER_HUB_POSTGRES_BIN_DIR=' + ExpandConstant('{app}\pgsql\bin') + #13#10 +
        'EMBER_HUB_MINIO_BIN_DIR=' + ExpandConstant('{app}\minio') + #13#10 +
        'EMBER_HUB_LICENSE_FILE=' + ExpandConstant('{commonappdata}\EmberHub\license.key') + #13#10 +
        'EMBER_HUB_PUBLIC_KEY_FILE=' + ExpandConstant('{app}\hub-public-key.der') + #13#10 +
        'EMBER_HUB_STATE_FILE=' + ExpandConstant('{commonappdata}\EmberHub\hub-state.json') + #13#10 +
        'EMBER_HUB_POSTGRES_PORT=5432' + #13#10 +
        'EMBER_HUB_MINIO_PORT=9000' + #13#10 +
        'EMBER_HUB_SERVER_PORT={#ServerPort}' + #13#10 +
        'EMBER_HUB_ACTIVATION_URL={#EmberHubActivationUrl}' + #13#10 +
        'EMBER_HUB_HEARTBEAT_URL={#EmberHubHeartbeatUrl}' + #13#10;
      SaveStringToFile(EnvPath, Lines, False);
    end;
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    if MsgBox('¿Eliminar también los datos de Ember Hub (base de datos, licencia, respaldos) en ' +
              ExpandConstant('{commonappdata}\EmberHub') + '?  Elige "No" para conservarlos.',
              mbConfirmation, MB_YESNO or MB_DEFBUTTON2) = IDYES then
      DelTree(ExpandConstant('{commonappdata}\EmberHub'), True, True, True);
end;
```

Note `{#EmberHubActivationUrl}` / `{#EmberHubHeartbeatUrl}` are `/D` defines supplied by `build-installer.ps1` (next step) from `ember-hub/build.env`.

- [ ] **Step 2: Add the `installer` stage to `build-installer.ps1`**

```powershell
function Read-BuildEnv {
    $path = Join-Path $hubDir "build.env"
    if (-not (Test-Path $path)) { throw "ember-hub/build.env missing — copy build.env.example and fill it." }
    $map = @{}
    Get-Content $path | Where-Object { $_ -and -not $_.StartsWith("#") -and $_.Contains("=") } | ForEach-Object {
        $k, $v = $_.Split("=", 2); $map[$k.Trim()] = $v.Trim()
    }
    return $map
}

function Build-Installer {
    Write-Host "== installer ==" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $appImageDir "Ember Hub.exe"))) { Build-AppImage }
    $iscc = (Get-Command iscc.exe -ErrorAction SilentlyContinue).Source
    if (-not $iscc) { $iscc = "${env:ProgramFiles(x86)}\Inno Setup 6\iscc.exe" }
    if (-not (Test-Path $iscc)) { throw "Inno Setup (iscc.exe) not found — install Inno Setup 6." }

    $env = Read-BuildEnv
    $version = Get-HubVersion
    Push-Location $installerDir
    try {
        & $iscc `
            "/DAppVersion=$version" `
            "/DServerPort=$($env['EMBER_HUB_SERVER_PORT'])" `
            "/DEmberHubActivationUrl=$($env['EMBER_HUB_ACTIVATION_URL'])" `
            "/DEmberHubHeartbeatUrl=$($env['EMBER_HUB_HEARTBEAT_URL'])" `
            "EmberHub.iss"
        if ($LASTEXITCODE -ne 0) { throw "iscc failed ($LASTEXITCODE)" }
    } finally { Pop-Location }

    $out = Join-Path $distDir "EmberHubSetup-$version.exe"
    if (-not (Test-Path $out)) { throw "installer not produced at $out" }
    Write-Host "installer: $out" -ForegroundColor Green
}
```

Update the dispatch tail:

```powershell
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
if ($Stage -in @("all","installer")) { Build-Installer }
Write-Host "Done ($Stage)." -ForegroundColor Green
```

- [ ] **Step 3: Compile the installer**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/build-installer.ps1 -Stage installer`
Expected: `ember-hub/dist/EmberHubSetup-0.0.1.exe` produced (size ~250–320 MB).

- [ ] **Step 4: Commit**

```powershell
git add ember-hub/installer/EmberHub.iss ember-hub/build-installer.ps1
git commit -m "feat(hub): inno setup script + installer build stage"
```

---

### Task 9: `ember-hub/README.md` + PROGRESS update

**Files:**
- Create: `ember-hub/README.md`
- Modify: `PROGRESS.md`

**Interfaces:** none (docs).

- [ ] **Step 1: Write `ember-hub/README.md`**

````markdown
# Ember Hub — Windows installer build

Ember Hub is `backend/`'s jar run with `SPRING_PROFILES_ACTIVE=hub` (boots the
Swing `HubDashboard` instead of a headless server). This folder packages it into
a Windows installer. Design: `docs/superpowers/specs/2026-09-05-hub-installer-design.md`.

## Prerequisites (build machine, Windows x64)

- JDK 17 on `PATH` (`java`, `jlink`, `jpackage`)
- `pnpm`, and the repo's `backend/mvnw.cmd`
- [Inno Setup 6](https://jrsoftware.org/isdl.php) (`iscc.exe`)
- Internet access (one-time, to fetch the portable Postgres + MinIO binaries)

## One-time setup

```powershell
Copy-Item ember-hub\build.env.example ember-hub\build.env   # then edit URLs/port
# place the production RSA public key at ember-hub\keys\hub-public-key.der
powershell -ExecutionPolicy Bypass -File ember-hub\fetch-vendor-binaries.ps1
# first run prints the SHA256s — paste them into fetch-vendor-binaries.ps1 and re-run
```

## Build

```powershell
powershell -ExecutionPolicy Bypass -File ember-hub\build-installer.ps1
# stages: runtime | appimage | installer  (default: all)
```

Output: `ember-hub\dist\EmberHubSetup-<version>.exe`.

## Install layout (on the customer PC)

| Path | Contents | Lifecycle |
|---|---|---|
| `%ProgramFiles%\Ember Hub\` | launcher, JRE, jar, pgsql, minio, public key, shim | replaced on every update |
| `%ProgramData%\EmberHub\` | `data\`, `logs\`, `backups\`, `license.key`, `hub-state.json`, `hub.env` | survives updates; uninstall asks before deleting |

Auto-start: a shortcut in the common Startup folder runs `Iniciar Ember Hub.cmd`,
which loads `hub.env`, sets `SPRING_PROFILES_ACTIVE=hub`, and launches the app with
`--autostart`.

## Manual verification checklist (no CI covers the .exe)

1. Install on a clean Windows PC with **no Java**. Reboot / re-login → the
   dashboard opens and starts the services by itself.
2. From a second PC on the same LAN, open `http://<hub-ip>:{port}/` → the SPA
   loads; a waiter can take an order and the kitchen screen sees it.
3. Delete `%ProgramData%\EmberHub\license.key`, restart → dashboard shows the
   license error → "Seleccionar license.key…" → pick the file → it activates and
   writes `hub-state.json`.
4. Build a higher `<version>`, run its installer over the existing one → app
   files replaced, `%ProgramData%` intact, Flyway migrates on next start.
5. Uninstall, choose **No** to keep data → reinstall → history intact.
6. Re-confirm the five boot-error paths from report 236 (port in use, missing
   license, wrong-hardware fingerprint, corrupt PGDATA, non-empty initdb dir)
   still show actionable Spanish messages.
````

- [ ] **Step 2: Update `PROGRESS.md`**

In "Task Queue Status", under the Ember Hub HUB-01 bullet, change:

```markdown
  - [ ] Hub: `jpackage`/`jlink` embedded-JRE `.exe` installer — not started. **Until this + the service exist, Hub isn't installable by a non-technical customer.**
```

to:

```markdown
  - [x] **HUB-03** — `jpackage` app-image + Inno Setup `.exe` installer, portable Postgres 16.6-1 + MinIO bundled, `%ProgramData%\EmberHub` data dir, firewall rule, common-Startup auto-launch (`Iniciar Ember Hub.cmd` shim + `hub.env`), dashboard `license.key` picker. Plan `docs/superpowers/plans/2026-09-05-hub-installer.md`. Build: `ember-hub/build-installer.ps1`. **Manual Windows verification pending — see report from Task 10.**
  - [ ] Hub: Windows service auto-start (`sc.exe`/SCM recovery) — **deferred**: v1 uses common-Startup launch to keep the Swing dashboard as the operator surface (spec 2026-09-05 §1).
```

Also update the "Last Completed Task" / "System Health" lines at the top to reflect this plan's completion and `Tests run: 1054`.

- [ ] **Step 3: Commit**

```powershell
git add ember-hub/README.md PROGRESS.md
git commit -m "docs(hub): installer build README + progress update"
```

---

### Task 10: Manual verification on real Windows + report

**Files:**
- Create: `reports/381-hub-installer-manual-verification.md` (bump the number if `reports/` already has one ≥ 381)

**Interfaces:** none (verification only; no code commit).

- [ ] **Step 1: Build the installer**

Run: `powershell -ExecutionPolicy Bypass -File ember-hub/build-installer.ps1`
Expected: `ember-hub/dist/EmberHubSetup-<version>.exe`.

- [ ] **Step 2: Run the six-item checklist** from `ember-hub/README.md` on a real Windows PC (ideally one without a JDK), with a second PC on the same LAN for item 2, and a real (or report-236-style throwaway) `license.key` for item 3.

- [ ] **Step 3: Record results in a report**

Create `reports/381-hub-installer-manual-verification.md` (bump if `reports/` already has a ≥381) with the standard structure (Identification / Objective / Modified Files: "none — verification only" / What was verified / outcome per checklist item, including any bugs found). File each bug fix as its own atomic task/commit (same pattern as reports 233–235).

- [ ] **Step 4: Commit the report**

```powershell
git add reports/381-hub-installer-manual-verification.md PROGRESS.md
git commit -m "docs(hub): HUB-03 manual verification report"
```

---

## Self-Review

**Spec coverage:**
- §1 auto-start at login → Task 8 `{commonstartup}` icon + Task 3 `--autostart`. ✔
- §2.1 installer contents → Tasks 4 (runtime), 5 (pg/minio), 7 (jar/frontend/shim/key/icon). ✔
- §2.2 install layout → Task 8 `[Dirs]` + `DefaultDirName={autopf}\Ember Hub`. ✔
- §2.3 shim + `hub.env` → Task 6 + Task 8 `[Code] CurStepChanged`. ✔
- §2.4 license picker → Tasks 2 + 3. ✔
- §3 build process → Tasks 4/5/7/8 `build-installer.ps1` stages; §3 step order (frontend → mvn → jlink → jpackage → binaries → iscc) matches `Build-AppImage`/`Build-Installer`. ✔
- §4 Inno script (firewall private+domain, uninstaller data prompt, upgrade close-running) → Task 8 `[Run]`/`[UninstallRun]`/`CloseApplications`/`CurUninstallStepChanged`. ✔
- §5 runtime boot → unchanged code path; smoke in Task 7 step 6, full in Task 10. ✔
- §6 boot errors → Task 10 checklist item 6. ✔
- §7 security (F-21 out of scope; firewall private/domain) → noted in Global Constraints + Task 8 rule scope. ✔
- §8 test plan → Task 9 README checklist + Task 10. ✔
- §10 risks: `jlink` module list → Task 4 `jlink-modules.txt` (+ verify step); `%ProgramData%` resolution → shim (Task 6); port-8080 conflict → `hub.env`'s `EMBER_HUB_SERVER_PORT` is operator-editable + the firewall rule reads the same value (documented in README); code-signing/SmartScreen → called out as a business decision, not in scope. ✔

**Placeholder scan:** The only literal `REPLACE_ME` is in Task 5, with explicit steps to compute and pin the value on first run (a vendoring pattern, resolved within the task). `reports/NNN-…` in Task 10 = "next sequential number", with the lookup instruction. No "TODO"/"handle edge cases"/"similar to Task N".

**Type consistency:** `LicenseFileInstaller.install(Path source, Path destination)` — defined Task 2, consumed Task 3 with `chosen.toPath()` / `properties.licenseFile()`. `Build-Runtime` / `Build-AppImage` / `Build-Installer` / `Get-HubVersion` / `Read-BuildEnv` — defined once, called across Tasks 4/7/8, names consistent. `Iniciar Ember Hub.cmd` filename identical in Tasks 6, 7, 8. `hub.env` key names identical in Task 6 template and Task 8 `[Code]` writer. `--autostart` produced Task 3, consumed by the shim in Task 6.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-05-hub-installer.md`. Two execution options:

1. **Subagent-Driven (recommended)** — a fresh subagent per task, review between tasks. Note: Tasks 4–10 must run on Windows with a JDK 17 + Inno Setup + internet, and Task 10 needs a second LAN PC — those are your machine, not a subagent sandbox, so expect to run those steps yourself and paste results back.
2. **Inline Execution** — tasks executed in this session with checkpoints.

Which approach?
