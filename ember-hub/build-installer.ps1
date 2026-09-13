<#
Builds the Ember Hub Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> ember-hub/dist/runtime            (jlink JRE image)
  appimage  -> ember-hub/dist/app-image          (jpackage + assembled binaries)
  installer -> ember-hub/dist/EmberHubSetup-*.exe (Tauri bundler, NSIS)
Requires: JDK 17 on PATH (java, jlink, jpackage), pnpm, mvn, Node (for ember-hub/ui), Rust +
`cargo install tauri-cli --version "^2"` for the last stage.
#>
param([ValidateSet("all","runtime","appimage","installer")] [string] $Stage = "all")

$ErrorActionPreference = "Stop"
$repoRoot   = Split-Path -Parent $PSScriptRoot
$hubDir     = $PSScriptRoot
$distDir    = Join-Path $hubDir "dist"
$runtimeDir = Join-Path $distDir "runtime"

$frontendPs     = Join-Path $hubDir "build-frontend.ps1"
$fetchPs        = Join-Path $hubDir "fetch-vendor-binaries.ps1"
$stageDir       = Join-Path $hubDir ".vendor-cache\staging"
$appImageParent = Join-Path $distDir "app-image"
$appImageDir    = Join-Path $appImageParent "Ember Hub"
$installerDir   = Join-Path $hubDir "installer"
$tauriDir       = Join-Path $hubDir "src-tauri"

function Get-HubVersion {
    $pom = Get-Content (Join-Path $repoRoot "backend\pom.xml") -Raw
    if ($pom -notmatch "<artifactId>ember</artifactId>\s*<version>([^<]+)</version>") {
        throw "could not read <version> from backend/pom.xml"
    }
    return $Matches[1] -replace "-SNAPSHOT",""
}

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

function Build-AppImage {
    Write-Host "== app-image ==" -ForegroundColor Cyan
    if (-not (Test-Path $runtimeDir)) { Build-Runtime }
    if (-not (Test-Path (Join-Path $stageDir "pgsql\bin\initdb.exe"))) {
        & powershell -ExecutionPolicy Bypass -File $fetchPs
        if ($LASTEXITCODE -ne 0) { throw "fetch-vendor-binaries.ps1 failed" }
    }

    Write-Host "-- frontend --"
    # vite's hub build writes a non-fatal warning to stderr (env-config.js script tag isn't
    # type="module"); under $ErrorActionPreference="Stop" that native stderr write is treated as
    # a terminating error even though the process exits 0, so relax it locally and trust
    # $LASTEXITCODE (checked right below) for the real pass/fail signal.
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & powershell -ExecutionPolicy Bypass -File $frontendPs
    $ErrorActionPreference = $prevEap
    if ($LASTEXITCODE -ne 0) { throw "build-frontend.ps1 failed" }

    Write-Host "-- mvn package --"
    Push-Location (Join-Path $repoRoot "backend")
    try {
        & .\mvnw.cmd -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    } finally { Pop-Location }

    # backend/target can accumulate jars from older builds/versions (it's a shared Maven module);
    # Get-ChildItem's enumeration order is not chronological, so an unsorted -First 1 can silently
    # pick a stale jar instead of the one `mvn package` just produced above. Sort by build time.
    $jar = Get-ChildItem (Join-Path $repoRoot "backend\target") -Filter "ember-*.jar" |
           Where-Object { $_.Name -notmatch "sources|javadoc|original" } |
           Sort-Object LastWriteTime -Descending |
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
    Copy-Item (Join-Path $hubDir "keys\hub-public-key.der") $appImageDir

    if (-not (Test-Path (Join-Path $appImageDir "Ember Hub.exe"))) { throw "app-image launcher missing" }
    Write-Host "app-image at $appImageDir" -ForegroundColor Green
}

function Read-BuildEnv {
    $path = Join-Path $hubDir "build.env"
    if (-not (Test-Path $path)) { throw "ember-hub/build.env missing - copy build.env.example and fill it." }
    $map = @{}
    Get-Content $path | Where-Object { $_ -and -not $_.StartsWith("#") -and $_.Contains("=") } | ForEach-Object {
        $k, $v = $_.Split("=", 2); $map[$k.Trim()] = $v.Trim()
    }
    return $map
}

function Clear-ReadOnlyRecurse($path) {
    if (-not (Test-Path $path)) { return }
    Get-ChildItem -Path $path -Recurse -Force -File | ForEach-Object {
        if ($_.Attributes -band [System.IO.FileAttributes]::ReadOnly) {
            $_.Attributes = $_.Attributes -band (-bnot [System.IO.FileAttributes]::ReadOnly)
        }
    }
}

function Build-Installer {
    Write-Host "== installer (Tauri) ==" -ForegroundColor Cyan
    if (-not (Test-Path (Join-Path $appImageDir "Ember Hub.exe"))) { Build-AppImage }

    $cargoTauri = (Get-Command cargo-tauri.exe -ErrorAction SilentlyContinue) -or
                  (Get-Command cargo -ErrorAction SilentlyContinue)
    if (-not $cargoTauri) { throw "Rust/cargo not found - install Rust and `cargo install tauri-cli --version '^2'`." }

    # Same read-only app-image copy issue printer-agent's build-installer.ps1 hit (report 444):
    # jpackage's launcher exe is read-only and tauri-build's copy_resources step can't overwrite
    # a read-only destination on a second build.
    Clear-ReadOnlyRecurse (Join-Path $tauriDir "target\release\app-image")
    Clear-ReadOnlyRecurse (Join-Path $tauriDir "target\debug\app-image")

    Push-Location $tauriDir
    try {
        # cargo/tauri-cli write non-fatal "Info"/progress lines to stderr; under
        # $ErrorActionPreference="Stop" Windows PowerShell 5.1 treats any native stderr write as
        # a terminating NativeCommandError regardless of the real exit code (same bug class fixed
        # in printer-agent/build-installer.ps1 and this script's own frontend step) -- relax it
        # locally and trust $LASTEXITCODE for the real pass/fail signal.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & cargo tauri build
        $ErrorActionPreference = $prevEap
        if ($LASTEXITCODE -ne 0) { throw "cargo tauri build failed ($LASTEXITCODE)" }
    } finally { Pop-Location }

    $bundleDir = Join-Path $tauriDir "target\release\bundle\nsis"
    $produced = Get-ChildItem $bundleDir -Filter "*-setup.exe" | Select-Object -First 1
    if (-not $produced) { throw "no NSIS installer produced under $bundleDir" }

    $version = Get-HubVersion
    $out = Join-Path $distDir "EmberHubSetup-$version.exe"
    Copy-Item $produced.FullName $out -Force
    Write-Host "installer: $out" -ForegroundColor Green
}

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
if ($Stage -in @("all","installer")) { Build-Installer }
Write-Host "Done ($Stage)." -ForegroundColor Green
