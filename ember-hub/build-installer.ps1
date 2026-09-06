<#
Builds the Ember Hub Windows installer.
Stages (run all by default, or one via -Stage):
  runtime   -> ember-hub/dist/runtime            (jlink JRE image)
  appimage  -> ember-hub/dist/app-image          (jpackage + assembled binaries)
  installer -> ember-hub/dist/EmberHubSetup-*.exe (Inno Setup)                      [Task 8]
Requires: JDK 17 on PATH (java, jlink, jpackage), pnpm, mvn, Inno Setup (iscc).
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

New-Item -ItemType Directory -Force -Path $distDir | Out-Null
if ($Stage -in @("all","runtime"))   { Build-Runtime }
if ($Stage -in @("all","appimage"))  { Build-AppImage }
# installer stage added in Task 8
Write-Host "Done ($Stage)." -ForegroundColor Green
